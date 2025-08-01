package ca.uhn.fhir.jpa.provider.r4;

import ca.uhn.fhir.jpa.api.config.JpaStorageSettings;
import ca.uhn.fhir.jpa.provider.BaseResourceProviderR4Test;
import ca.uhn.fhir.jpa.replacereferences.ReplaceReferencesLargeTestData;
import ca.uhn.fhir.jpa.replacereferences.ReplaceReferencesTestHelper;
import ca.uhn.fhir.jpa.test.Batch2JobHelper;
import ca.uhn.fhir.model.api.IProvenanceAgent;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.StrictErrorHandler;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ca.uhn.fhir.test.utilities.HttpClientExtension;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static ca.uhn.fhir.jpa.provider.ReplaceReferencesSvcImpl.RESOURCE_TYPES_SYSTEM;
import static ca.uhn.fhir.jpa.replacereferences.ReplaceReferencesLargeTestData.RESOURCE_TYPES_EXPECTED_TO_BE_PATCHED;
import static ca.uhn.fhir.jpa.replacereferences.ReplaceReferencesLargeTestData.TOTAL_EXPECTED_PATCHES;
import static ca.uhn.fhir.rest.server.provider.ProviderConstants.OPERATION_MERGE_OUTPUT_PARAM_INPUT;
import static ca.uhn.fhir.rest.server.provider.ProviderConstants.OPERATION_MERGE_OUTPUT_PARAM_OUTCOME;
import static ca.uhn.fhir.rest.server.provider.ProviderConstants.OPERATION_MERGE_OUTPUT_PARAM_RESULT;
import static ca.uhn.fhir.rest.server.provider.ProviderConstants.OPERATION_MERGE_OUTPUT_PARAM_TASK;
import static ca.uhn.fhir.rest.server.provider.ProviderConstants.OPERATION_MERGE_PARAM_RESULT_PATIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PatientMergeR4Test extends BaseResourceProviderR4Test {
	static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(PatientMergeR4Test.class);

	@RegisterExtension
	MyExceptionHandler ourExceptionHandler = new MyExceptionHandler();

	@Autowired
	Batch2JobHelper myBatch2JobHelper;
	
	ReplaceReferencesTestHelper myTestHelper;

	ReplaceReferencesLargeTestData myLargeTestData;

	@Override
	@AfterEach
	public void after() throws Exception {
		super.after();

		myStorageSettings.setDefaultTransactionEntriesForWrite(new JpaStorageSettings().getDefaultTransactionEntriesForWrite());
		myStorageSettings.setReuseCachedSearchResultsForMillis(new JpaStorageSettings().getReuseCachedSearchResultsForMillis());
	}

	@Override
	@BeforeEach
	public void before() throws Exception {
		super.before();
		myStorageSettings.setReuseCachedSearchResultsForMillis(null);
		myStorageSettings.setAllowMultipleDelete(true);
		myFhirContext.setParserErrorHandler(new StrictErrorHandler());
		// we need to keep the version on Provenance.target fields to
		// verify that Provenance resources were saved with versioned target references
		myFhirContext.getParserOptions().setStripVersionsFromReferences(false);
		myTestHelper = new ReplaceReferencesTestHelper(myFhirContext, myDaoRegistry);
		myLargeTestData = new ReplaceReferencesLargeTestData(myDaoRegistry);
	}

	private void waitForAsyncTaskCompletion(Parameters theOutParams) {
		assertThat(getLastHttpStatusCode()).isEqualTo(HttpServletResponse.SC_ACCEPTED);
		Task task = (Task) theOutParams.getParameter(OPERATION_MERGE_OUTPUT_PARAM_TASK).getResource();
		assertNull(task.getIdElement().getVersionIdPart());
		ourLog.info("Got task {}", task.getId());
		String jobId = myTestHelper.getJobIdFromTask(task);
		myBatch2JobHelper.awaitJobCompletion(jobId);
	}

	private void validateTaskOutput(Parameters theOutParams) {
		Task task = (Task) theOutParams.getParameter(OPERATION_MERGE_OUTPUT_PARAM_TASK).getResource();
		Task taskWithOutput = myTaskDao.read(task.getIdElement(), mySrd);
		assertThat(taskWithOutput.getStatus()).isEqualTo(Task.TaskStatus.COMPLETED);
		ourLog.info("Complete Task: {}", myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(taskWithOutput));

		Task.TaskOutputComponent taskOutput = taskWithOutput.getOutputFirstRep();

		// Assert on the output type
		Coding taskType = taskOutput.getType().getCodingFirstRep();
		assertEquals(RESOURCE_TYPES_SYSTEM, taskType.getSystem());
		assertEquals("Bundle", taskType.getCode());

		List<Resource> containedResources = taskWithOutput.getContained();
		assertThat(containedResources)
			.hasSize(1)
			.element(0)
			.isInstanceOf(Bundle.class);

		Bundle containedBundle = (Bundle) containedResources.get(0);

		Reference outputRef = (Reference) taskOutput.getValue();
		Bundle patchResultBundle = (Bundle) outputRef.getResource();
		assertTrue(containedBundle.equalsDeep(patchResultBundle));
		ReplaceReferencesTestHelper.validatePatchResultBundle(patchResultBundle, TOTAL_EXPECTED_PATCHES, RESOURCE_TYPES_EXPECTED_TO_BE_PATCHED);


		OperationOutcome outcome = (OperationOutcome) theOutParams.getParameter(OPERATION_MERGE_OUTPUT_PARAM_OUTCOME).getResource();
		assertThat(outcome.getIssue())
			.hasSize(1)
			.element(0)
			.satisfies(issue -> {
				assertThat(issue.getSeverity()).isEqualTo(OperationOutcome.IssueSeverity.INFORMATION);
				assertThat(issue.getDetails().getText()).isEqualTo("Merge request is accepted, and will be " +
					"processed asynchronously. See task resource returned in this response for details.");
			});
	}

	private void validateSyncOutcome(Parameters theOutParams) {
		// Assert outcome
		OperationOutcome outcome = (OperationOutcome) theOutParams.getParameter(OPERATION_MERGE_OUTPUT_PARAM_OUTCOME).getResource();
		assertThat(outcome.getIssue())
			.hasSize(1)
			.element(0)
			.satisfies(issue -> {
				assertThat(issue.getSeverity()).isEqualTo(OperationOutcome.IssueSeverity.INFORMATION);
				assertThat(issue.getDetails().getText()).isEqualTo("Merge operation completed successfully.");
			});

		// In sync mode, the result patient is returned in the output,
		// assert what is returned is the same as the one in the db
		Patient targetPatientInOutput = (Patient) theOutParams.getParameter(OPERATION_MERGE_OUTPUT_PARAM_RESULT).getResource();
		Patient targetPatientReadFromDB = myTestHelper.readPatient(myLargeTestData.getTargetPatientId());
		IParser parser = myFhirContext.newJsonParser();
		assertThat(parser.encodeResourceToString(targetPatientInOutput)).isEqualTo(parser.encodeResourceToString(targetPatientReadFromDB));
	}


	private void validatePreviewModeOutcome(Parameters theOutParams) {
		OperationOutcome outcome = (OperationOutcome) theOutParams.getParameter(OPERATION_MERGE_OUTPUT_PARAM_OUTCOME).getResource();
		assertThat(outcome.getIssue())
			.hasSize(1)
			.element(0)
			.satisfies(issue -> {
				assertThat(issue.getSeverity()).isEqualTo(OperationOutcome.IssueSeverity.INFORMATION);
				assertThat(issue.getDetails().getText()).isEqualTo("Preview only merge operation - no issues detected");
				assertThat(issue.getDiagnostics()).isEqualTo("Merge would update 25 resources");
			});
	}


	@ParameterizedTest(name = "{index}: deleteSource={0}, resultPatient={1}, preview={2}, async={3}")
	@CsvSource({
		// withDelete, withInputResultPatient, withPreview, isAsync
		"true, true, true, false",
		"true, false, true, false",
		"false, true, true, false",
		"false, false, true, false",
		"true, true, false, false",
		"true, false, false, false",
		"false, true, false, false",
		"false, false, false, false",

		"true, true, true, true",
		"true, false, true, true",
		"false, true, true, true",
		"false, false, true, true",
		"true, true, false, true",
		"true, false, false, true",
		"false, true, false, true",
		"false, false, false, true",
	})
	public void testMerge(boolean withDelete, boolean withInputResultPatient, boolean withPreview, boolean isAsync) {
		// setup
		myLargeTestData.createTestResources();
		ReplaceReferencesTestHelper.PatientMergeInputParameters inParams = new ReplaceReferencesTestHelper.PatientMergeInputParameters();
		myTestHelper.setSourceAndTarget(inParams, myLargeTestData);
		inParams.deleteSource = withDelete;
		if (withInputResultPatient) {
			inParams.resultPatient = myLargeTestData.createResultPatientInput(withDelete);
		}
		if (withPreview) {
			inParams.preview = true;
		}

		Parameters inParameters = inParams.asParametersResource();

		// exec
		Parameters outParams = callMergeOperation(inParameters, isAsync);

		// validate:
		// the result will contain the input parameters, the outcome,
		// and in sync and preview modes the resulting patient
		// or in async mode the task resource instead of the resulting patient
		assertThat(outParams.getParameter()).hasSize(3);

		// Assert input
		Parameters input = (Parameters) outParams.getParameter(OPERATION_MERGE_OUTPUT_PARAM_INPUT).getResource();
		if (withInputResultPatient) { // if the following assert fails, check that these two patients are identical
			Patient p1 = (Patient) inParameters.getParameter(OPERATION_MERGE_PARAM_RESULT_PATIENT).getResource();
			Patient p2 = (Patient) input.getParameter(OPERATION_MERGE_PARAM_RESULT_PATIENT).getResource();
			ourLog.info(myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(p1));
			ourLog.info(myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(p2));
		}
		assertTrue(input.equalsDeep(inParameters));

		List<Identifier> expectedIdentifiersOnTargetAfterMerge =
			myLargeTestData.getExpectedIdentifiersForTargetAfterMerge(withInputResultPatient);


		if (withPreview) {
			validatePreviewModeOutcome(outParams);
			myTestHelper.assertReferencesHaveNotChanged(myLargeTestData);
			//no more validation is needed in preview mode, so we can return early
			return;
		}

		if (isAsync) {
			waitForAsyncTaskCompletion(outParams);
			validateTaskOutput(outParams);
		} else { // Synchronous case
			validateSyncOutcome(outParams);
		}

		// Check that the linked resources were updated
		myTestHelper.assertAllReferencesUpdated(true, withDelete, myLargeTestData);
		myTestHelper.assertSourcePatientUpdatedOrDeletedAfterMerge(myLargeTestData.getSourcePatientId(), myLargeTestData.getTargetPatientId(), withDelete);
		myTestHelper.assertTargetPatientUpdatedAfterMerge(myLargeTestData.getTargetPatientId(), myLargeTestData.getSourcePatientId(), withDelete, expectedIdentifiersOnTargetAfterMerge);
		myTestHelper.assertMergeProvenance(inParams.asParametersResource(), myLargeTestData,  null);
	}


	@ParameterizedTest(name = "{index}: isAsync={0}, theAgentInterceptorReturnsMultipleAgents={1}")
	@CsvSource (value = {
		"false, false",
		"false, true",
		"true, false",
		"true, true",
	})
	void testMerge_withProvenanceAgentInterceptor_Success(boolean theIsAsync, boolean theAgentInterceptorReturnsMultipleAgents) {
		myLargeTestData.createTestResources();
		List<IProvenanceAgent> agents = new ArrayList<>();
		agents.add(myTestHelper.createTestProvenanceAgent());
		if (theAgentInterceptorReturnsMultipleAgents) {
			agents.add(myTestHelper.createTestProvenanceAgent());
		}
		// this interceptor will be unregistered in @AfterEach of the base class, which unregisters all interceptors
		ReplaceReferencesTestHelper.registerProvenanceAgentInterceptor(myServer.getRestfulServer(), agents);

		ReplaceReferencesTestHelper.PatientMergeInputParameters inParams = new ReplaceReferencesTestHelper.PatientMergeInputParameters();
		myTestHelper.setSourceAndTarget(inParams, myLargeTestData);

		Parameters inParameters = inParams.asParametersResource();

		// exec
		Parameters outParams = callMergeOperation(inParameters, theIsAsync);

		if (theIsAsync) {
			waitForAsyncTaskCompletion(outParams);
			validateTaskOutput(outParams);
		}
		else { // Synchronous case
			validateSyncOutcome(outParams);
		}

		myTestHelper.assertMergeProvenance(inParams.asParametersResource(), myLargeTestData, agents);
	}

	@ParameterizedTest(name = "{index}: isAsync={0}")
	@CsvSource (value = {
		"false",
		"true"
	})
	void testMerge_withProvenanceAgentInterceptor_InterceptorReturnsNoAgent_ReturnsInternalError(boolean theIsAsync) {

		// this interceptor will be unregistered in @AfterEach of the base class, which unregisters all interceptors
		ReplaceReferencesTestHelper.registerProvenanceAgentInterceptor(myServer.getRestfulServer(), Collections.emptyList());

		ReplaceReferencesTestHelper.PatientMergeInputParameters inParams = new ReplaceReferencesTestHelper.PatientMergeInputParameters();

		Parameters inParameters = inParams.asParametersResource();

		assertThatThrownBy(() -> callMergeOperation(inParameters, theIsAsync)
		).isInstanceOf(InternalErrorException.class)
			.hasMessageContaining("HAPI-2723: No Provenance Agent was provided by any interceptor for Pointcut.PROVENANCE_AGENTS")
			.extracting(InternalErrorException.class::cast)
			.extracting(BaseServerResponseException::getStatusCode)
			.isEqualTo(500);
	}



	@Test
	void testMerge_smallResourceLimit() {
		myLargeTestData.createTestResources();
		ReplaceReferencesTestHelper.PatientMergeInputParameters inParams = new ReplaceReferencesTestHelper.PatientMergeInputParameters();
		myTestHelper.setSourceAndTarget(inParams, myLargeTestData);

		inParams.resourceLimit = 5;
		Parameters inParameters = inParams.asParametersResource();

		// exec
		assertThatThrownBy(() -> callMergeOperation(inParameters, false))
			.isInstanceOf(PreconditionFailedException.class)
			.satisfies(ex -> assertThat(myTestHelper.extractFailureMessageFromOutcomeParameter((BaseServerResponseException) ex)).isEqualTo("HAPI-2597: Number of resources with references to "+ myLargeTestData.getSourcePatientId() + " exceeds the resource-limit 5. Submit the request asynchronsly by adding the HTTP Header 'Prefer: respond-async'."));
	}

	@ParameterizedTest(name = "{index}: deleteSource={0}, async={1}")
	@CsvSource({
		"true, false",
		"false, false",
		"true, true",
		"false, true",
	})
	void testMerge_SourceResourceNotReferencedByAnyResource_ShouldSucceedAndCreateProvenance(boolean theDeleteSource, boolean theAsync) {

		Patient sourcePatient = new Patient();
		sourcePatient = (Patient) myPatientDao.create(sourcePatient, mySrd).getResource();

		Patient targetPatient = new Patient();
		targetPatient = (Patient) myPatientDao.create(targetPatient, mySrd).getResource();

		ReplaceReferencesTestHelper.PatientMergeInputParameters inParams = new ReplaceReferencesTestHelper.PatientMergeInputParameters();
		inParams.sourcePatient = new Reference(sourcePatient.getIdElement().toVersionless());
		inParams.targetPatient = new Reference(targetPatient.getIdElement().toVersionless());
		if (theDeleteSource) {
			inParams.deleteSource = true;
		}

		Parameters outParams = callMergeOperation(inParams.asParametersResource(), theAsync);

		if (theAsync) {
			waitForAsyncTaskCompletion(outParams);
		}

		IIdType theExpectedTargetIdWithVersion = targetPatient.getIdElement().withVersion("2");
		if (theDeleteSource) {
			// the source resource is being deleted and there is no identifiers to copy over to the target,
			// so, in this version of the test, the target is not actually updated. Its version will remain the same.
			theExpectedTargetIdWithVersion = targetPatient.getIdElement().withVersion("1");
		}

		myTestHelper.assertTargetPatientUpdatedAfterMerge(
			targetPatient.getIdElement(),
			sourcePatient.getIdElement(),
			theDeleteSource,
			Collections.emptyList()
		);

		myTestHelper.assertSourcePatientUpdatedOrDeletedAfterMerge(sourcePatient.getIdElement(),
			targetPatient.getIdElement(),
			theDeleteSource);

		myTestHelper.assertMergeProvenance(inParams.asParametersResource(),
			sourcePatient.getIdElement().withVersion("2"),
			theExpectedTargetIdWithVersion,
			0,
			Collections.EMPTY_SET,
			null);
	}


	@Test
	void testMerge_SourceResourceCannotBeDeletedBecauseAnotherResourceReferencingSourceWasAddedWhileJobIsRunning_JobFails() {
		myLargeTestData.createTestResources();
		ReplaceReferencesTestHelper.PatientMergeInputParameters inParams = new ReplaceReferencesTestHelper.PatientMergeInputParameters();
		myTestHelper.setSourceAndTarget(inParams, myLargeTestData);
		inParams.deleteSource = true;
		//using a small batch size that would result in multiple chunks to ensure that
		//the job runs a bit slowly so that we have sometime to add a resource that references the source
		//after the first step
		myStorageSettings.setDefaultTransactionEntriesForWrite(5);
		Parameters inParameters = inParams.asParametersResource();

		// exec
		Parameters outParams = callMergeOperation(inParameters, true);
		Task task = (Task) outParams.getParameter(OPERATION_MERGE_OUTPUT_PARAM_TASK).getResource();
		assertNull(task.getIdElement().getVersionIdPart());
		ourLog.info("Got task {}", task.getId());
		String jobId = myTestHelper.getJobIdFromTask(task);

		// wait for first step of the job to finish
		await()
			.until(() -> {
				myBatch2JobHelper.runMaintenancePass();
				String currentGatedStepId = myJobCoordinator.getInstance(jobId).getCurrentGatedStepId();
				return !"query-ids".equals(currentGatedStepId);
			});

		Encounter enc = new Encounter();
		enc.setStatus(Encounter.EncounterStatus.ARRIVED);
		enc.getSubject().setReferenceElement(myLargeTestData.getSourcePatientId());
		myEncounterDao.create(enc, mySrd);

		myBatch2JobHelper.awaitJobFailure(jobId);


		Task taskAfterJobFailure = myTaskDao.read(task.getIdElement().toVersionless(), mySrd);
		assertThat(taskAfterJobFailure.getStatus()).isEqualTo(Task.TaskStatus.FAILED);
	}

	@ParameterizedTest(name = "{index}: deleteSource={0}, resultPatient={1}, preview={2}")
	@CsvSource({
		// withDelete, withInputResultPatient, withPreview
		"true, true, true",
		"true, false, true",
		"false, true, true",
		"false, false, true",
		"true, true, false",
		"true, false, false",
		"false, true, false",
		"false, false, false",
	})
	public void testMultipleTargetMatchesFails(boolean withDelete, boolean withInputResultPatient, boolean withPreview) {
		myLargeTestData.createTestResources();
		ReplaceReferencesTestHelper.PatientMergeInputParameters inParams = myTestHelper.buildMultipleTargetMatchParameters(withDelete, withInputResultPatient, withPreview, myLargeTestData);

		Parameters inParameters = inParams.asParametersResource();

		assertUnprocessibleEntityWithMessage(inParameters, "Multiple resources found matching the identifier(s) specified in 'target-patient-identifier'");
	}


	@ParameterizedTest(name = "{index}: deleteSource={0}, resultPatient={1}, preview={2}")
	@CsvSource({
		// withDelete, withInputResultPatient, withPreview
		"true, true, true",
		"true, false, true",
		"false, true, true",
		"false, false, true",
		"true, true, false",
		"true, false, false",
		"false, true, false",
		"false, false, false",
	})
	public void testMultipleSourceMatchesFails(boolean withDelete, boolean withInputResultPatient, boolean withPreview) {
		myLargeTestData.createTestResources();
		ReplaceReferencesTestHelper.PatientMergeInputParameters inParams = myTestHelper.buildMultipleSourceMatchParameters(withDelete, withInputResultPatient, withPreview, myLargeTestData);

		Parameters inParameters = inParams.asParametersResource();

		assertUnprocessibleEntityWithMessage(inParameters, "Multiple resources found matching the identifier(s) specified in 'source-patient-identifier'");
	}

	@Test
	void test_MissingRequiredParameters_Returns400BadRequest() {
		Parameters params = new Parameters();
		assertThatThrownBy(() -> callMergeOperation(params)
		).isInstanceOf(InvalidRequestException.class)
			.extracting(InvalidRequestException.class::cast)
			.extracting(BaseServerResponseException::getStatusCode)
			.isEqualTo(400);
	}


	@Test
	void testMerge_NonParameterRequestBody_Returns400BadRequest() throws IOException {
		HttpClientExtension clientExtension = new HttpClientExtension();
		clientExtension.initialize();
		try (CloseableHttpClient client = clientExtension.getClient()) {
			HttpPost post = new HttpPost(myServer.getBaseUrl() + "/Patient/$merge");
			post.addHeader("Content-Type", "application/fhir+json");
			post.setEntity(new StringEntity(myFhirContext.newJsonParser().encodeResourceToString(new Patient()), StandardCharsets.UTF_8));
			try (CloseableHttpResponse response = client.execute(post)) {
				assertThat(response.getStatusLine().getStatusCode()).isEqualTo(400);
				String responseContent = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
				assertThat(responseContent).contains("There are no source resource parameters provided");
				assertThat(responseContent).contains("There are no target resource parameters provided");
			}
		}
	}

	private void assertUnprocessibleEntityWithMessage(Parameters inParameters, String theExpectedMessage) {
		assertThatThrownBy(() ->
			callMergeOperation(inParameters))
			.isInstanceOf(UnprocessableEntityException.class)
			.extracting(UnprocessableEntityException.class::cast)
			.extracting(myTestHelper::extractFailureMessageFromOutcomeParameter)
			.isEqualTo(theExpectedMessage);
	}

	private void callMergeOperation(Parameters inParameters) {
		this.callMergeOperation(inParameters, false);
	}

	private Parameters callMergeOperation(Parameters inParameters, boolean isAsync) {
		return myTestHelper.callMergeOperation(myClient, inParameters, isAsync);
	}

	class MyExceptionHandler implements TestExecutionExceptionHandler {
		@Override
		public void handleTestExecutionException(ExtensionContext theExtensionContext, Throwable theThrowable) throws Throwable {
			if (theThrowable instanceof BaseServerResponseException ex) {
				String message = myTestHelper.extractFailureMessageFromOutcomeParameter(ex);
				throw ex.getClass().getDeclaredConstructor(String.class, Throwable.class).newInstance(message, ex);
			}
			throw theThrowable;
		}
	}


	@Override
	protected boolean verboseClientLogging() {
		return true;
	}
}

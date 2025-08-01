/*
 * #%L
 * HAPI FHIR - Core Library
 * %%
 * Copyright (C) 2014 - 2025 Smile CDR, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package ca.uhn.fhir.util;

import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementCompositeDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementDefinition;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.model.api.StorageResponseCodeEnum;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import jakarta.annotation.Nullable;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseCoding;
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.ICompositeType;
import org.hl7.fhir.instance.model.api.IPrimitiveType;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Utilities for dealing with OperationOutcome resources across various model versions
 */
public class OperationOutcomeUtil {

	public static final String OO_SEVERITY_ERROR = "error";
	public static final String OO_SEVERITY_INFO = "information";
	public static final String OO_SEVERITY_WARN = "warning";
	public static final String OO_ISSUE_CODE_INFORMATIONAL = "informational";

	/**
	 * Add an issue to an OperationOutcome
	 *
	 * @param theCtx              The fhir context
	 * @param theOperationOutcome The OO resource to add to
	 * @param theSeverity         The severity (fatal | error | warning | information)
	 * @param theDiagnostics      The diagnostics string (this was called "details" in FHIR DSTU2 but was renamed to diagnostics in DSTU3)
	 * @param theCode
	 * @return Returns the newly added issue
	 */
	public static IBase addIssue(
			FhirContext theCtx,
			IBaseOperationOutcome theOperationOutcome,
			String theSeverity,
			String theDiagnostics,
			String theLocation,
			String theCode) {
		return addIssue(
				theCtx, theOperationOutcome, theSeverity, theDiagnostics, theLocation, theCode, null, null, null);
	}

	public static IBase addIssue(
			FhirContext theCtx,
			IBaseOperationOutcome theOperationOutcome,
			String theSeverity,
			String theDiagnostics,
			String theLocation,
			String theCode,
			@Nullable String theDetailSystem,
			@Nullable String theDetailCode,
			@Nullable String theDetailDescription) {
		IBase issue = createIssue(theCtx, theOperationOutcome);
		populateDetails(
				theCtx,
				issue,
				theSeverity,
				theDiagnostics,
				theLocation,
				theCode,
				theDetailSystem,
				theDetailCode,
				theDetailDescription);
		return issue;
	}

	private static IBase createIssue(FhirContext theCtx, IBaseResource theOutcome) {
		RuntimeResourceDefinition ooDef = theCtx.getResourceDefinition(theOutcome);
		BaseRuntimeChildDefinition issueChild = ooDef.getChildByName("issue");
		BaseRuntimeElementCompositeDefinition<?> issueElement =
				(BaseRuntimeElementCompositeDefinition<?>) issueChild.getChildByName("issue");

		IBase issue = issueElement.newInstance();
		issueChild.getMutator().addValue(theOutcome, issue);
		return issue;
	}

	/**
	 * @deprecated Use {@link #getFirstIssueDiagnostics(FhirContext, IBaseOperationOutcome)} instead. This
	 * 	method has always been misnamed for historical reasons.
	 */
	@Deprecated(forRemoval = true, since = "8.2.0")
	public static String getFirstIssueDetails(FhirContext theCtx, IBaseOperationOutcome theOutcome) {
		return getFirstIssueDiagnostics(theCtx, theOutcome);
	}

	public static String getFirstIssueDiagnostics(FhirContext theCtx, IBaseOperationOutcome theOutcome) {
		return getIssueStringPart(theCtx, theOutcome, "diagnostics", 0);
	}

	public static String getIssueDiagnostics(FhirContext theCtx, IBaseOperationOutcome theOutcome, int theIndex) {
		return getIssueStringPart(theCtx, theOutcome, "diagnostics", theIndex);
	}

	public static String getFirstIssueLocation(FhirContext theCtx, IBaseOperationOutcome theOutcome) {
		return getIssueStringPart(theCtx, theOutcome, "location", 0);
	}

	private static String getIssueStringPart(
			FhirContext theCtx, IBaseOperationOutcome theOutcome, String theName, int theIndex) {
		if (theOutcome == null) {
			return null;
		}

		RuntimeResourceDefinition ooDef = theCtx.getResourceDefinition(theOutcome);
		BaseRuntimeChildDefinition issueChild = ooDef.getChildByName("issue");

		List<IBase> issues = issueChild.getAccessor().getValues(theOutcome);
		if (issues.size() <= theIndex) {
			return null;
		}

		IBase issue = issues.get(theIndex);
		BaseRuntimeElementCompositeDefinition<?> issueElement =
				(BaseRuntimeElementCompositeDefinition<?>) theCtx.getElementDefinition(issue.getClass());
		BaseRuntimeChildDefinition detailsChild = issueElement.getChildByName(theName);

		List<IBase> details = detailsChild.getAccessor().getValues(issue);
		if (details.isEmpty()) {
			return null;
		}
		return ((IPrimitiveType<?>) details.get(0)).getValueAsString();
	}

	/**
	 * Returns true if the given OperationOutcome has 1 or more Operation.issue repetitions
	 */
	public static boolean hasIssues(FhirContext theCtx, IBaseOperationOutcome theOutcome) {
		if (theOutcome == null) {
			return false;
		}
		return getIssueCount(theCtx, theOutcome) > 0;
	}

	public static int getIssueCount(FhirContext theCtx, IBaseOperationOutcome theOutcome) {
		RuntimeResourceDefinition ooDef = theCtx.getResourceDefinition(theOutcome);
		BaseRuntimeChildDefinition issueChild = ooDef.getChildByName("issue");
		return issueChild.getAccessor().getValues(theOutcome).size();
	}

	public static boolean hasIssuesOfSeverity(
			FhirContext theCtx, IBaseOperationOutcome theOutcome, String theSeverity) {
		RuntimeResourceDefinition ooDef = theCtx.getResourceDefinition(theOutcome);
		BaseRuntimeChildDefinition issueChild = ooDef.getChildByName("issue");
		List<IBase> issues = issueChild.getAccessor().getValues(theOutcome);

		if (issues.isEmpty()) {
			return false; // if there are no issues at all, there are no issues of the required severity
		}

		IBase firstIssue = issues.get(0);
		BaseRuntimeElementCompositeDefinition<?> issueElement =
				(BaseRuntimeElementCompositeDefinition<?>) theCtx.getElementDefinition(firstIssue.getClass());
		BaseRuntimeChildDefinition severityChild = issueElement.getChildByName("severity");

		return issues.stream()
				.flatMap(t -> severityChild.getAccessor().getValues(t).stream())
				.map(t -> (IPrimitiveType<?>) t)
				.map(IPrimitiveType::getValueAsString)
				.anyMatch(theSeverity::equals);
	}

	public static IBaseOperationOutcome newInstance(FhirContext theCtx) {
		RuntimeResourceDefinition ooDef = theCtx.getResourceDefinition("OperationOutcome");
		try {
			return (IBaseOperationOutcome) ooDef.getImplementingClass().newInstance();
		} catch (InstantiationException e) {
			throw new InternalErrorException(Msg.code(1803) + "Unable to instantiate OperationOutcome", e);
		} catch (IllegalAccessException e) {
			throw new InternalErrorException(Msg.code(1804) + "Unable to instantiate OperationOutcome", e);
		}
	}

	private static void populateDetails(
			FhirContext theCtx,
			IBase theIssue,
			String theSeverity,
			String theDiagnostics,
			String theLocation,
			String theCode,
			String theDetailSystem,
			String theDetailCode,
			String theDetailDescription) {
		BaseRuntimeElementCompositeDefinition<?> issueElement =
				(BaseRuntimeElementCompositeDefinition<?>) theCtx.getElementDefinition(theIssue.getClass());
		BaseRuntimeChildDefinition diagnosticsChild;
		diagnosticsChild = issueElement.getChildByName("diagnostics");

		BaseRuntimeChildDefinition codeChild = issueElement.getChildByName("code");
		IPrimitiveType<?> codeElem = (IPrimitiveType<?>)
				codeChild.getChildByName("code").newInstance(codeChild.getInstanceConstructorArguments());
		codeElem.setValueAsString(theCode);
		codeChild.getMutator().addValue(theIssue, codeElem);

		BaseRuntimeElementDefinition<?> stringDef = diagnosticsChild.getChildByName(diagnosticsChild.getElementName());
		BaseRuntimeChildDefinition severityChild = issueElement.getChildByName("severity");

		IPrimitiveType<?> severityElem = (IPrimitiveType<?>)
				severityChild.getChildByName("severity").newInstance(severityChild.getInstanceConstructorArguments());
		severityElem.setValueAsString(theSeverity);
		severityChild.getMutator().addValue(theIssue, severityElem);

		IPrimitiveType<?> string = (IPrimitiveType<?>) stringDef.newInstance();
		string.setValueAsString(theDiagnostics);
		diagnosticsChild.getMutator().setValue(theIssue, string);

		addLocationToIssue(theCtx, theIssue, theLocation);

		if (isNotBlank(theDetailSystem)) {
			BaseRuntimeChildDefinition detailsChild = issueElement.getChildByName("details");
			if (detailsChild != null) {
				BaseRuntimeElementDefinition<?> codeableConceptDef = theCtx.getElementDefinition("CodeableConcept");
				IBase codeableConcept = codeableConceptDef.newInstance();

				BaseRuntimeElementDefinition<?> codingDef = theCtx.getElementDefinition("Coding");
				IBaseCoding coding = (IBaseCoding) codingDef.newInstance();
				coding.setSystem(theDetailSystem);
				coding.setCode(theDetailCode);
				coding.setDisplay(theDetailDescription);

				codeableConceptDef.getChildByName("coding").getMutator().addValue(codeableConcept, coding);

				detailsChild.getMutator().addValue(theIssue, codeableConcept);
			}
		}
	}

	public static void addLocationToIssue(FhirContext theContext, IBase theIssue, String theLocation) {
		if (isNotBlank(theLocation)) {
			BaseRuntimeElementCompositeDefinition<?> issueElement =
					(BaseRuntimeElementCompositeDefinition<?>) theContext.getElementDefinition(theIssue.getClass());
			BaseRuntimeChildDefinition locationChild = issueElement.getChildByName("location");
			IPrimitiveType<?> locationElem = (IPrimitiveType<?>) locationChild
					.getChildByName("location")
					.newInstance(locationChild.getInstanceConstructorArguments());
			locationElem.setValueAsString(theLocation);
			locationChild.getMutator().addValue(theIssue, locationElem);
		}
	}

	/**
	 * Given an instance of <code>OperationOutcome.issue</code>, adds a new instance of
	 * <code>OperationOutcome.issue.expression</code> with the given string value.
	 *
	 * @param theContext            The FhirContext for the appropriate FHIR version
	 * @param theIssue              The <code>OperationOutcome.issue</code> to add to
	 * @param theLocationExpression The string to use as content
	 */
	public static void addExpressionToIssue(FhirContext theContext, IBase theIssue, String theLocationExpression) {
		if (isNotBlank(theLocationExpression)
				&& theContext.getVersion().getVersion().isEqualOrNewerThan(FhirVersionEnum.R4)) {
			BaseRuntimeElementCompositeDefinition<?> issueElement =
					(BaseRuntimeElementCompositeDefinition<?>) theContext.getElementDefinition(theIssue.getClass());
			BaseRuntimeChildDefinition locationChild = issueElement.getChildByName("expression");
			IPrimitiveType<?> locationElem = (IPrimitiveType<?>) locationChild
					.getChildByName("expression")
					.newInstance(locationChild.getInstanceConstructorArguments());
			locationElem.setValueAsString(theLocationExpression);
			locationChild.getMutator().addValue(theIssue, locationElem);
		}
	}

	public static IBase addIssueWithMessageId(
			FhirContext myCtx,
			IBaseOperationOutcome theOperationOutcome,
			String theSeverity,
			String theMessage,
			String theMessageId,
			String theLocation,
			String theCode) {
		IBase issue = addIssue(myCtx, theOperationOutcome, theSeverity, theMessage, theLocation, theCode);
		if (isNotBlank(theMessageId)) {
			addDetailsToIssue(myCtx, issue, Constants.JAVA_VALIDATOR_DETAILS_SYSTEM, theMessageId);
		}

		return issue;
	}

	public static void addDetailsToIssue(FhirContext theFhirContext, IBase theIssue, String theSystem, String theCode) {
		addDetailsToIssue(theFhirContext, theIssue, theSystem, theCode, null);
	}

	public static void addDetailsToIssue(
			FhirContext theFhirContext, IBase theIssue, String theSystem, String theCode, String theText) {
		BaseRuntimeElementCompositeDefinition<?> issueElement =
				(BaseRuntimeElementCompositeDefinition<?>) theFhirContext.getElementDefinition(theIssue.getClass());
		BaseRuntimeChildDefinition detailsChildDef = issueElement.getChildByName("details");
		BaseRuntimeElementCompositeDefinition<?> ccDef =
				(BaseRuntimeElementCompositeDefinition<?>) theFhirContext.getElementDefinition("CodeableConcept");
		ICompositeType codeableConcept = (ICompositeType) ccDef.newInstance();

		if (isNotBlank(theSystem) || isNotBlank(theCode)) {
			BaseRuntimeElementCompositeDefinition<?> codingDef =
					(BaseRuntimeElementCompositeDefinition<?>) theFhirContext.getElementDefinition("Coding");
			ICompositeType coding = (ICompositeType) codingDef.newInstance();

			// System
			if (isNotBlank(theSystem)) {
				IPrimitiveType<?> system = (IPrimitiveType<?>)
						theFhirContext.getElementDefinition("uri").newInstance();
				system.setValueAsString(theSystem);
				codingDef.getChildByName("system").getMutator().addValue(coding, system);
			}

			// Code
			if (isNotBlank(theCode)) {
				IPrimitiveType<?> code = (IPrimitiveType<?>)
						theFhirContext.getElementDefinition("code").newInstance();
				code.setValueAsString(theCode);
				codingDef.getChildByName("code").getMutator().addValue(coding, code);
			}

			ccDef.getChildByName("coding").getMutator().addValue(codeableConcept, coding);
		}

		if (isNotBlank(theText)) {
			IPrimitiveType<?> textElem = (IPrimitiveType<?>)
					ccDef.getChildByName("text").getChildByName("text").newInstance(theText);
			ccDef.getChildByName("text").getMutator().addValue(codeableConcept, textElem);
		}

		detailsChildDef.getMutator().addValue(theIssue, codeableConcept);
	}

	public static void addIssueLineExtensionToIssue(FhirContext theCtx, IBase theIssue, String theLine) {
		if (theCtx.getVersion().getVersion() != FhirVersionEnum.DSTU2) {
			ExtensionUtil.setExtension(
					theCtx,
					theIssue,
					"http://hl7.org/fhir/StructureDefinition/operationoutcome-issue-line",
					"integer",
					theLine);
		}
	}

	public static void addIssueColExtensionToIssue(FhirContext theCtx, IBase theIssue, String theColumn) {
		if (theCtx.getVersion().getVersion() != FhirVersionEnum.DSTU2) {
			ExtensionUtil.setExtension(
					theCtx,
					theIssue,
					"http://hl7.org/fhir/StructureDefinition/operationoutcome-issue-col",
					"integer",
					theColumn);
		}
	}

	public static void addMessageIdExtensionToIssue(FhirContext theCtx, IBase theIssue, String theMessageId) {
		if (theCtx.getVersion().getVersion() != FhirVersionEnum.DSTU2) {
			ExtensionUtil.setExtension(
					theCtx,
					theIssue,
					"http://hl7.org/fhir/StructureDefinition/operationoutcome-message-id",
					"string",
					theMessageId);
		}
	}

	public static IBaseOperationOutcome createOperationOutcome(
			String theSeverity,
			String theMessage,
			String theCode,
			FhirContext theFhirContext,
			@Nullable StorageResponseCodeEnum theStorageResponseCode) {
		IBaseOperationOutcome oo = newInstance(theFhirContext);
		String detailSystem = null;
		String detailCode = null;
		String detailDescription = null;
		if (theStorageResponseCode != null) {
			detailSystem = theStorageResponseCode.getSystem();
			detailCode = theStorageResponseCode.getCode();
			detailDescription = theStorageResponseCode.getDisplay();
		}
		addIssue(
				theFhirContext,
				oo,
				theSeverity,
				theMessage,
				null,
				theCode,
				detailSystem,
				detailCode,
				detailDescription);
		return oo;
	}
}

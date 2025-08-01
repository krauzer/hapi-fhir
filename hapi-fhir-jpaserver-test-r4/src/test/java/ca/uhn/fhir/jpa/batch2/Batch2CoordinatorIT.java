package ca.uhn.fhir.jpa.batch2;

import ca.uhn.fhir.batch2.api.ChunkExecutionDetails;
import ca.uhn.fhir.batch2.api.IJobCompletionHandler;
import ca.uhn.fhir.batch2.api.IJobCoordinator;
import ca.uhn.fhir.batch2.api.IJobDataSink;
import ca.uhn.fhir.batch2.api.IJobMaintenanceService;
import ca.uhn.fhir.batch2.api.IJobPersistence;
import ca.uhn.fhir.batch2.api.IJobStepWorker;
import ca.uhn.fhir.batch2.api.ILastJobStepWorker;
import ca.uhn.fhir.batch2.api.IReductionStepWorker;
import ca.uhn.fhir.batch2.api.JobExecutionFailedException;
import ca.uhn.fhir.batch2.api.RetryChunkLaterException;
import ca.uhn.fhir.batch2.api.RunOutcome;
import ca.uhn.fhir.batch2.api.StepExecutionDetails;
import ca.uhn.fhir.batch2.api.VoidModel;
import ca.uhn.fhir.batch2.coordinator.JobDefinitionRegistry;
import ca.uhn.fhir.batch2.coordinator.WorkChannelMessageListener;
import ca.uhn.fhir.batch2.maintenance.JobMaintenanceServiceImpl;
import ca.uhn.fhir.batch2.model.ChunkOutcome;
import ca.uhn.fhir.batch2.model.JobDefinition;
import ca.uhn.fhir.batch2.model.JobInstance;
import ca.uhn.fhir.batch2.model.JobInstanceStartRequest;
import ca.uhn.fhir.batch2.model.JobWorkNotification;
import ca.uhn.fhir.batch2.model.JobWorkNotificationJsonMessage;
import ca.uhn.fhir.batch2.model.StatusEnum;
import ca.uhn.fhir.batch2.model.WorkChunk;
import ca.uhn.fhir.batch2.model.WorkChunkStatusEnum;
import ca.uhn.fhir.batch2.models.JobInstanceFetchRequest;
import ca.uhn.fhir.broker.api.ChannelConsumerSettings;
import ca.uhn.fhir.jpa.batch.models.Batch2JobStartResponse;
import ca.uhn.fhir.jpa.entity.Batch2WorkChunkEntity;
import ca.uhn.fhir.jpa.subscription.channel.impl.LinkedBlockingChannel;
import ca.uhn.fhir.jpa.subscription.channel.impl.LinkedBlockingChannelFactory;
import ca.uhn.fhir.jpa.subscription.channel.impl.RetryPolicyProvider;
import ca.uhn.fhir.jpa.test.BaseJpaR4Test;
import ca.uhn.fhir.jpa.test.Batch2JobHelper;
import ca.uhn.fhir.jpa.test.config.Batch2FastSchedulerConfig;
import ca.uhn.fhir.jpa.test.config.TestR4Config;
import ca.uhn.fhir.jpa.util.RandomTextUtils;
import ca.uhn.fhir.model.api.IModelJson;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.server.messaging.json.HapiMessageHeaders;
import ca.uhn.fhir.test.utilities.UnregisterScheduledProcessor;
import ca.uhn.fhir.util.JsonUtil;
import ca.uhn.fhir.util.Logs;
import ca.uhn.test.concurrency.PointcutLatch;
import ca.uhn.test.util.LogbackTestExtension;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.MessageHandler;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.MaxAttemptsRetryPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static ca.uhn.fhir.batch2.config.BaseBatch2Config.CHANNEL_NAME;
import static ca.uhn.fhir.batch2.coordinator.WorkChunkProcessor.MAX_CHUNK_ERROR_COUNT;
import static ca.uhn.fhir.jpa.entity.Batch2WorkChunkEntity.ERROR_MSG_MAX_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doNothing;


@ContextConfiguration(classes = {
	Batch2FastSchedulerConfig.class,
	Batch2CoordinatorIT.RPConfig.class
})
@TestPropertySource(properties = {
	// These tests require scheduling to work
	UnregisterScheduledProcessor.SCHEDULING_DISABLED_EQUALS_FALSE
})
public class Batch2CoordinatorIT extends BaseJpaR4Test {

	/***
	 * Our internal configuration of Retry Mechanism is
	 * with exponential backoff, and infinite retries.
	 *
	 * This isn't ideal for tests; so we will override
	 * the retry mechanism for tests that require it to
	 * make them run faster and more 'predictably'
	 */
	public static class RetryProviderOverride extends RetryPolicyProvider {

		private RetryPolicy myRetryPolicy;

		private BackOffPolicy myBackOffPolicy;

		public void setPolicies(RetryPolicy theRetryPolicy, BackOffPolicy theBackOffPolicy) {
			myRetryPolicy = theRetryPolicy;
			myBackOffPolicy = theBackOffPolicy;
		}

		@Override
		protected RetryPolicy retryPolicy() {
			if (myRetryPolicy != null) {
				return myRetryPolicy;
			}
			return super.retryPolicy();
		}

		@Override
		protected BackOffPolicy backOffPolicy() {
			if (myBackOffPolicy != null) {
				return myBackOffPolicy;
			}
			return super.backOffPolicy();
		}
	}

	@Configuration
	public static class RPConfig {
		@Primary
		@Bean
		public RetryPolicyProvider retryPolicyProvider() {
			return new RetryProviderOverride();
		}
	}

	private static final Logger ourLog = LoggerFactory.getLogger(Batch2CoordinatorIT.class);

	public static final int TEST_JOB_VERSION = 1;
	public static final String FIRST_STEP_ID = "first-step";
	public static final String SECOND_STEP_ID = "second-step";
	public static final String LAST_STEP_ID = "last-step";
	@Autowired
	JobDefinitionRegistry myJobDefinitionRegistry;
	@Autowired
	IJobCoordinator myJobCoordinator;
	@Autowired
	IJobMaintenanceService myJobMaintenanceService;
	@Autowired
	Batch2JobHelper myBatch2JobHelper;
	@Autowired
	private LinkedBlockingChannelFactory myChannelFactory;

	@SpyBean
	private IJobPersistence myJobPersistence;

	@Autowired
	private WorkChannelMessageListener myWorkChannelMessageListener;

	@Autowired
	private RetryPolicyProvider myRetryPolicyProvider;

	@RegisterExtension
	private LogbackTestExtension myLogbackTestExtension = new LogbackTestExtension();

	private final PointcutLatch myFirstStepLatch = new PointcutLatch("First Step");
	private final PointcutLatch myLastStepLatch = new PointcutLatch("Last Step");
	private IJobCompletionHandler<TestJobParameters> myCompletionHandler;
	private LinkedBlockingChannel myWorkChannel;

	private static RunOutcome callLatch(PointcutLatch theLatch, StepExecutionDetails<?, ?> theStep) {
		theLatch.call(theStep);
		return RunOutcome.SUCCESS;
	}

	static {
		TestR4Config.ourMaxThreads = 100;
	}

	@Override
	@BeforeEach
	public void before() throws Exception {
		super.before();

		myCompletionHandler = details -> {
		};
		myWorkChannel = myChannelFactory.getOrCreateReceiver(CHANNEL_NAME, new ChannelConsumerSettings());
		myStorageSettings.setJobFastTrackingEnabled(true);

		// reset
		if (myRetryPolicyProvider instanceof RetryProviderOverride rp) {
			rp.setPolicies(null, null);
		}
	}

	@AfterEach
	public void after() {
		myWorkChannel.clearInterceptorsForUnitTest();
	}

	@Test
	public void replayingWorkChunkNotificationMessages_forFailedJobs_shouldNotThrowNullPointers() throws Exception {
		// setup
		String currentLogger = myLogbackTestExtension.getCurrentLogger();
		myLogbackTestExtension.setLoggerToWatch(Logs.CA_UHN_FHIR_LOG_BATCH_TROUBLESHOOTING);
		myLogbackTestExtension.reRegister();

		// create a job
		// step 1
		IJobStepWorker<TestJobParameters, VoidModel, FirstStepOutput> first = (step, sink) -> {
			throw new RuntimeException("Hi, i'm an error.");
		};
		// final step
		ILastJobStepWorker<TestJobParameters, FirstStepOutput> last = (step, sink) -> RunOutcome.SUCCESS;

		String jobId = getMethodNameForJobId();
		JobDefinition<? extends IModelJson> jd = JobDefinition.newBuilder()
			.setJobDefinitionId(jobId)
			.setJobDescription("test job")
			.setJobDefinitionVersion(TEST_JOB_VERSION)
			.setParametersType(TestJobParameters.class)
			.gatedExecution()
			.addFirstStep(
				FIRST_STEP_ID,
				"Test first step",
				FirstStepOutput.class,
				first
			)
			.addLastStep(
				LAST_STEP_ID,
				"Test last step",
				last
			)
			.build();
		myJobDefinitionRegistry.addJobDefinition(jd);

		// start a job
		JobInstanceStartRequest request = buildRequest(jobId);
		Batch2JobStartResponse response = myJobCoordinator.startInstance(new SystemRequestDetails(), request);
		String instanceId = response.getInstanceId();

		// await the failure
		myBatch2JobHelper.awaitJobFailure(instanceId);

		// get the only chunk we have
		Optional<Batch2WorkChunkEntity> firstOp = runInTransaction(() -> {
			Stream<Batch2WorkChunkEntity> stream = myWorkChunkRepository.fetchChunksForStep(response.getInstanceId(), FIRST_STEP_ID);
			return stream.findFirst();
		});
		assertTrue(firstOp.isPresent());
		String chunkId = firstOp.get().getId();

		// manually move the job to failed
		// (`awaitJobFailure` will wait for job to reach ERROR or FAILED, so
		// we want to make sure it's in FAILED)
		runInTransaction(() -> {
			myJobInstanceRepository.updateInstanceStatusIfIn(instanceId, StatusEnum.FAILED, Set.of(StatusEnum.getIncompleteStatuses()));
		});

		/*
		 * set the lifetime of failed jobs to 0;
		 * this allows the maintenance pass to clean up any "Failed"
		 * jobs if they have outlived  their lifetime (which we're setting to 0 here).
		 * Normally lifetime is 7 days (see JobInstanceProcessor.PURGE_THRESHOLD)
		 */
		assertTrue(myJobMaintenanceService instanceof JobMaintenanceServiceImpl);
		((JobMaintenanceServiceImpl)myJobMaintenanceService).setFailedJobLifetime(0);

		try {
			// run maintenance pass -> cleans up failed jobs/workchunks
			myBatch2JobHelper.runMaintenancePass();

			// create a fake work notification
			JobWorkNotification notification = new JobWorkNotification();
			notification.setInstanceId(response.getInstanceId());
			notification.setJobDefinitionId(jobId);
			notification.setJobDefinitionVersion(TEST_JOB_VERSION);
			notification.setTargetStepId(FIRST_STEP_ID);
			notification.setChunkId(chunkId);

			JobWorkNotificationJsonMessage msg = new JobWorkNotificationJsonMessage();
			msg.setPayload(notification);
			// default headers
			HapiMessageHeaders headers = new HapiMessageHeaders();
			headers.setRetryCount(0);
			msg.setHeaders(headers);

			// simulate a rollback and submit a job
			ourLog.info("Attempting to replay work notification for job id {} and work chunk {}.", instanceId, chunkId);
			myWorkChannelMessageListener.handleMessage(msg);

			List<ILoggingEvent> events = myLogbackTestExtension.getLogEvents(e -> e.getLevel() == Level.WARN
				&& e.getFormattedMessage().contains("Unknown chunk id " + chunkId));
			assertFalse(events.isEmpty());
		} finally {
			// reset
			((JobMaintenanceServiceImpl) myJobMaintenanceService).setFailedJobLifetime(-1);
			myJobPersistence.deleteInstanceAndChunks(instanceId);

			myLogbackTestExtension.setLoggerToWatch(currentLogger);
			myLogbackTestExtension.reRegister();
		}
	}

	@Test
	public void fetchAllJobInstances_withValidInput_returnsPage() {
		int maxJobsToSave = 10;

		// create a job
		// step 1
		IJobStepWorker<TestJobParameters, VoidModel, FirstStepOutput> first = (step, sink) -> RunOutcome.SUCCESS;
		// final step
		ILastJobStepWorker<TestJobParameters, FirstStepOutput> last = (step, sink) -> RunOutcome.SUCCESS;
		// job definition
		String jobId = getMethodNameForJobId();
		JobDefinition<? extends IModelJson> jd = JobDefinition.newBuilder()
			.setJobDefinitionId(jobId)
			.setJobDescription("test job")
			.setJobDefinitionVersion(TEST_JOB_VERSION)
			.setParametersType(TestJobParameters.class)
			.gatedExecution()
			.addFirstStep(
				FIRST_STEP_ID,
				"Test first step",
				FirstStepOutput.class,
				first
			)
			.addLastStep(
				LAST_STEP_ID,
				"Test last step",
				last
			)
			.build();
		myJobDefinitionRegistry.addJobDefinition(jd);

		// start a number of jobs
		List<String> jobIds = new ArrayList<>();
		for (int i = 0; i < maxJobsToSave; i++) {
			JobInstanceStartRequest request = buildRequest(jobId);
			Batch2JobStartResponse response = myJobCoordinator.startInstance(new SystemRequestDetails(), request);
			jobIds.add(response.getInstanceId());
		}

		// run the test
		// see if we can fetch jobs
		int index = 0;
		int size = 2;
		JobInstanceFetchRequest request = new JobInstanceFetchRequest();
		request.setPageStart(index);
		request.setBatchSize(size);
		request.setSort(Sort.unsorted());

		Page<JobInstance> page;
		Iterator<JobInstance> iterator;
		int pageIndex = 0;
		List<String> fetched = new ArrayList<>();
		do {
			// create / update our request
			request.setPageStart(pageIndex);
			page = myJobCoordinator.fetchAllJobInstances(request);
			iterator = page.iterator();

			while (iterator.hasNext()) {
				JobInstance next = iterator.next();
				assertThat(jobIds).contains(next.getInstanceId());
				fetched.add(next.getInstanceId());
			}

			pageIndex++;
		} while (page.hasNext());

		assertThat(fetched).hasSize(maxJobsToSave);
	}

	@Test
	public void testFirstStepNoSink() throws InterruptedException {
		IJobStepWorker<TestJobParameters, VoidModel, FirstStepOutput> firstStep = (step, sink) -> callLatch(myFirstStepLatch, step);
		IJobStepWorker<TestJobParameters, FirstStepOutput, VoidModel> lastStep = (step, sink) -> fail();

		String jobId = getMethodNameForJobId();
		JobDefinition<? extends IModelJson> definition = buildGatedJobDefinition(jobId, firstStep, lastStep);

		myJobDefinitionRegistry.addJobDefinition(definition);

		JobInstanceStartRequest request = buildRequest(jobId);

		myFirstStepLatch.setExpectedCount(1);
		Batch2JobStartResponse startResponse = myJobCoordinator.startInstance(new SystemRequestDetails(), request);
		myBatch2JobHelper.runMaintenancePass();
		myFirstStepLatch.awaitExpected();

		myBatch2JobHelper.awaitJobCompletion(startResponse.getInstanceId());
	}

	@Test
	public void testFirstStepToSecondStep_singleChunkFasttracks() throws InterruptedException {
		IJobStepWorker<TestJobParameters, VoidModel, FirstStepOutput> firstStep = (step, sink) -> {
			sink.accept(new FirstStepOutput());
			callLatch(myFirstStepLatch, step);
			return RunOutcome.SUCCESS;
		};
		IJobStepWorker<TestJobParameters, FirstStepOutput, VoidModel> lastStep = (step, sink) -> callLatch(myLastStepLatch, step);

		String jobDefId = "test-job-2";
		JobDefinition<? extends IModelJson> definition = buildGatedJobDefinition(jobDefId, firstStep, lastStep);

		myJobDefinitionRegistry.addJobDefinition(definition);

		JobInstanceStartRequest request = buildRequest(jobDefId);

		myFirstStepLatch.setExpectedCount(1);
		myLastStepLatch.setExpectedCount(1);
		String batchJobId = myJobCoordinator.startInstance(new SystemRequestDetails(), request).getInstanceId();
		myBatch2JobHelper.runMaintenancePass();
		myFirstStepLatch.awaitExpected();
		myBatch2JobHelper.assertFastTracking(batchJobId);

		myBatch2JobHelper.awaitJobCompletion(batchJobId);
		myLastStepLatch.awaitExpected();

		final List<JobInstance> jobInstances = myJobPersistence.fetchInstances(10, 0);

		assertThat(jobInstances).hasSize(1);

		final JobInstance jobInstance = jobInstances.get(0);

		assertEquals(StatusEnum.COMPLETED, jobInstance.getStatus());
		assertEquals(1.0, jobInstance.getProgress());
	}

	/**
	 * This test verifies that if we have a workchunks being processed by the queue,
	 * and the maintenance job kicks in, it won't necessarily advance the steps.
	 */
	@Test
	public void gatedJob_whenMaintenanceRunHappensDuringMsgProcessing_doesNotAdvance() throws InterruptedException {
		// setup
		// we disable the scheduler because multiple schedulers running simultaneously
		// might cause database collisions we do not expect (not what we're testing)
		myBatch2JobHelper.enableMaintenanceRunner(false);
		String jobId = getMethodNameForJobId();
		int chunksToMake = 5;
		AtomicInteger secondGateCounter = new AtomicInteger();
		AtomicBoolean reductionCheck = new AtomicBoolean(false);
		// we will listen into the message queue so we can force actions on it
		MessageHandler handler = message -> {
			/*
			 * We will force a run of the maintenance job
			 * to simulate the situation in which a chunk is
			 * still being processed by the WorkChunkMessageHandler
			 * (and thus, not available yet).
			 */
			myBatch2JobHelper.forceRunMaintenancePass();
		};

		buildAndDefine3StepReductionJob(jobId, new IReductionStepHandler() {

			@Override
			public void firstStep(StepExecutionDetails<TestJobParameters, VoidModel> theStep, IJobDataSink<FirstStepOutput> theDataSink) {
				for (int i = 0; i < chunksToMake; i++) {
					theDataSink.accept(new FirstStepOutput());
				}
			}

			@Override
			public void secondStep(StepExecutionDetails<TestJobParameters, FirstStepOutput> theStep, IJobDataSink<SecondStepOutput> theDataSink) {
				// no new chunks
				SecondStepOutput output = new SecondStepOutput();
				theDataSink.accept(output);
			}

			@Override
			public void reductionStepConsume(ChunkExecutionDetails<TestJobParameters, SecondStepOutput> theChunkDetails, IJobDataSink<ReductionStepOutput> theDataSink) {
				// we expect to get one here
				int val = secondGateCounter.getAndIncrement();
			}

			@Override
			public void reductionStepRun(StepExecutionDetails<TestJobParameters, SecondStepOutput> theStepExecutionDetails, IJobDataSink<ReductionStepOutput> theDataSink) {
				reductionCheck.set(true);
				theDataSink.accept(new ReductionStepOutput(new ArrayList<>()));
			}
		});

		try {
			myWorkChannel.subscribe(handler);

			// test
			JobInstanceStartRequest request = buildRequest(jobId);
			myFirstStepLatch.setExpectedCount(1);
			Batch2JobStartResponse startResponse = myJobCoordinator.startInstance(new SystemRequestDetails(), request);

			String instanceId = startResponse.getInstanceId();

			// wait
			myBatch2JobHelper.awaitJobCompletion(instanceId);

			// verify
			Optional<JobInstance> instanceOp = myJobPersistence.fetchInstance(instanceId);
			assertTrue(instanceOp.isPresent());
			JobInstance jobInstance = instanceOp.get();
			assertTrue(reductionCheck.get());
			assertEquals(chunksToMake, secondGateCounter.get());

			assertEquals(StatusEnum.COMPLETED, jobInstance.getStatus());
			assertEquals(1.0, jobInstance.getProgress());
		} finally {
			myWorkChannel.unsubscribe(handler);
			myBatch2JobHelper.enableMaintenanceRunner(true);
		}
	}

	@Test
	public void reductionStepFailing_willFailJob() {
		// setup
		String jobId = getMethodNameForJobId();
		int totalChunks = 3;
		AtomicInteger chunkCounter = new AtomicInteger();
		String error = "this is an error";

		buildAndDefine3StepReductionJob(jobId, new IReductionStepHandler() {

			@Override
			public void firstStep(StepExecutionDetails<TestJobParameters, VoidModel> theStep, IJobDataSink<FirstStepOutput> theDataSink) {
				for (int i = 0; i < totalChunks; i++) {
					theDataSink.accept(new FirstStepOutput());
				}
			}

			@Override
			public void secondStep(StepExecutionDetails<TestJobParameters, FirstStepOutput> theStep, IJobDataSink<SecondStepOutput> theDataSink) {
				SecondStepOutput output = new SecondStepOutput();
				theDataSink.accept(output);
			}

			@Override
			public void reductionStepConsume(ChunkExecutionDetails<TestJobParameters, SecondStepOutput> theChunkDetails, IJobDataSink<ReductionStepOutput> theDataSink) {
				chunkCounter.getAndIncrement();
			}

			@Override
			public void reductionStepRun(StepExecutionDetails<TestJobParameters, SecondStepOutput> theStepExecutionDetails, IJobDataSink<ReductionStepOutput> theDataSink) {
				// always throw
				throw new RuntimeException(error);
			}
		});

		// test
		JobInstanceStartRequest request = buildRequest(jobId);
		myFirstStepLatch.setExpectedCount(1);
		Batch2JobStartResponse startResponse = myJobCoordinator.startInstance(new SystemRequestDetails(), request);
		String instanceId = startResponse.getInstanceId();
		assertNotNull(instanceId);

		// waiting for job to end (any status - but we'll verify failed later)
		myBatch2JobHelper.awaitJobHasStatus(instanceId, StatusEnum.getEndedStatuses().toArray(new StatusEnum[0]));

		// verify
		Optional<JobInstance> instanceOp = myJobPersistence.fetchInstance(instanceId);
		assertTrue(instanceOp.isPresent());
		JobInstance jobInstance = instanceOp.get();

		assertEquals(totalChunks, chunkCounter.get());

		assertEquals(StatusEnum.FAILED, jobInstance.getStatus());
	}

	@Test
	public void testJobWithReductionStepFiresCompletionHandler() throws InterruptedException {
		// setup
		String jobId = getMethodNameForJobId();
		String testInfo = "test";
		int totalCalls = 2;
		AtomicInteger secondStepInt = new AtomicInteger();

		AtomicBoolean completionBool = new AtomicBoolean();

		myCompletionHandler = (params) -> {
			// ensure our completion handler gets the right status
			assertEquals(StatusEnum.COMPLETED, params.getInstance().getStatus());
			completionBool.getAndSet(true);
		};

		buildAndDefine3StepReductionJob(jobId, new IReductionStepHandler() {
			private final AtomicBoolean myBoolean = new AtomicBoolean();

			private final AtomicInteger mySecondGate = new AtomicInteger();

			@Override
			public void firstStep(StepExecutionDetails<TestJobParameters, VoidModel> theStep, IJobDataSink<FirstStepOutput> theDataSink) {
				for (int i = 0; i < totalCalls; i++) {
					theDataSink.accept(new FirstStepOutput());
				}
				callLatch(myFirstStepLatch, theStep);
			}

			@Override
			public void secondStep(StepExecutionDetails<TestJobParameters, FirstStepOutput> theStep, IJobDataSink<SecondStepOutput> theDataSink) {
				SecondStepOutput output = new SecondStepOutput();
				output.setValue(testInfo + secondStepInt.getAndIncrement());
				theDataSink.accept(output);
			}

			@Override
			public void reductionStepConsume(ChunkExecutionDetails<TestJobParameters, SecondStepOutput> theChunkDetails, IJobDataSink<ReductionStepOutput> theDataSink) {
				int val = mySecondGate.getAndIncrement();
			}

			@Override
			public void reductionStepRun(StepExecutionDetails<TestJobParameters, SecondStepOutput> theStepExecutionDetails, IJobDataSink<ReductionStepOutput> theDataSink) {
				boolean isRunAlready = myBoolean.getAndSet(true);
				assertFalse(isRunAlready, "Reduction step should only be called once!");

				theDataSink.accept(new ReductionStepOutput(new ArrayList<>()));
				callLatch(myLastStepLatch, theStepExecutionDetails);
			}
		});

		// test
		JobInstanceStartRequest request = buildRequest(jobId);
		myFirstStepLatch.setExpectedCount(1);
		Batch2JobStartResponse startResponse = myJobCoordinator.startInstance(new SystemRequestDetails(), request);

		String instanceId = startResponse.getInstanceId();
		myBatch2JobHelper.runMaintenancePass();
		myFirstStepLatch.awaitExpected();
		assertNotNull(instanceId);

		myBatch2JobHelper.awaitGatedStepId(SECOND_STEP_ID, instanceId);

		// wait for last step to finish
		ourLog.info("Setting last step latch");
		myLastStepLatch.setExpectedCount(1);

		// waiting
		myBatch2JobHelper.awaitJobCompletion(instanceId);
		ourLog.info("awaited the last step");
		myLastStepLatch.awaitExpected();

		// verify
		Optional<JobInstance> instanceOp = myJobPersistence.fetchInstance(instanceId);
		assertTrue(instanceOp.isPresent());
		JobInstance jobInstance = instanceOp.get();

		// ensure our completion handler fired
		assertTrue(completionBool.get());

		assertEquals(StatusEnum.COMPLETED, jobInstance.getStatus());
		assertEquals(1.0, jobInstance.getProgress());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testJobDefinitionWithReductionStepIT(boolean theDelayReductionStepBool) throws InterruptedException {
		// setup
		String jobId = getMethodNameForJobId() + "_" + theDelayReductionStepBool;
		String testInfo = "test";
		AtomicInteger secondStepInt = new AtomicInteger();

		buildAndDefine3StepReductionJob(jobId, new IReductionStepHandler() {
			private final ArrayList<SecondStepOutput> myOutput = new ArrayList<>();

			private final AtomicBoolean myBoolean = new AtomicBoolean();

			private final AtomicInteger mySecondGate = new AtomicInteger();

			@Override
			public void firstStep(StepExecutionDetails<TestJobParameters, VoidModel> theStep, IJobDataSink<FirstStepOutput> theDataSink) {
				theDataSink.accept(new FirstStepOutput());
				theDataSink.accept(new FirstStepOutput());
				callLatch(myFirstStepLatch, theStep);
			}

			@Override
			public void secondStep(StepExecutionDetails<TestJobParameters, FirstStepOutput> theStep, IJobDataSink<SecondStepOutput> theDataSink) {
				SecondStepOutput output = new SecondStepOutput();
				output.setValue(testInfo + secondStepInt.getAndIncrement());
				theDataSink.accept(output);
			}

			@Override
			public void reductionStepConsume(ChunkExecutionDetails<TestJobParameters, SecondStepOutput> theChunkDetails, IJobDataSink<ReductionStepOutput> theDataSink) {
				myOutput.add(theChunkDetails.getData());
				// 1 because we know 2 packets are coming.
				// we'll fire the second maintenance run on the second packet
				// which should cause multiple maintenance runs to run simultaneously
				if (theDelayReductionStepBool && mySecondGate.getAndIncrement() == 1) {
					ourLog.info("SECOND FORCED MAINTENANCE PASS FORCED");
					myBatch2JobHelper.forceRunMaintenancePass();
				}
			}

			@Override
			public void reductionStepRun(StepExecutionDetails<TestJobParameters, SecondStepOutput> theStepExecutionDetails, IJobDataSink<ReductionStepOutput> theDataSink) {
				boolean isRunAlready = myBoolean.getAndSet(true);
				assertThat(isRunAlready).as("Reduction step should only be called once!").isFalse();

				complete(theStepExecutionDetails, theDataSink);
			}

			private void complete(
				@Nonnull StepExecutionDetails<TestJobParameters, SecondStepOutput> theStepExecutionDetails,
				@Nonnull IJobDataSink<ReductionStepOutput> theDataSink
			) {
				assertTrue(myBoolean.get());
				theDataSink.accept(new ReductionStepOutput(myOutput));
				callLatch(myLastStepLatch, theStepExecutionDetails);
			}
		});

		// run test
		JobInstanceStartRequest request = buildRequest(jobId);
		myFirstStepLatch.setExpectedCount(1);
		Batch2JobStartResponse startResponse = myJobCoordinator.startInstance(new SystemRequestDetails(), request);
		String instanceId = startResponse.getInstanceId();
		myBatch2JobHelper.runMaintenancePass();
		myFirstStepLatch.awaitExpected();
		assertNotNull(instanceId);

		myBatch2JobHelper.awaitGatedStepId(SECOND_STEP_ID, instanceId);

		// wait for last step to finish
		ourLog.info("Setting last step latch");
		myLastStepLatch.setExpectedCount(1);

		// waiting
		myBatch2JobHelper.awaitJobCompletion(instanceId);
		myLastStepLatch.awaitExpected();
		ourLog.info("awaited the last step");

		// verify
		Optional<JobInstance> instanceOp = myJobPersistence.fetchInstance(instanceId);
		assertThat(instanceOp).isPresent();
		int secondStepCalls = secondStepInt.get();
		assertEquals(2, secondStepCalls);
		JobInstance instance = instanceOp.get();
		ourLog.info(JsonUtil.serialize(instance, true));
		assertNotNull(instance.getReport());

		for (int i = 0; i < secondStepInt.get(); i++) {
			assertThat(instance.getReport()).contains(testInfo + i);
		}

		final List<JobInstance> jobInstances = myJobPersistence.fetchInstances(10, 0);

		assertThat(jobInstances).hasSize(1);

		final JobInstance jobInstance = jobInstances.get(0);

		assertEquals(StatusEnum.COMPLETED, jobInstance.getStatus());
		assertEquals(1.0, jobInstance.getProgress());
	}

	@Test
	public void failingWorkChunks_withLargeErrorMsgs_shouldNotErrorOutTheJob() {
		// setup
		assertTrue(myRetryPolicyProvider instanceof RetryProviderOverride);

		String jobId = getMethodNameForJobId();
		AtomicInteger counter = new AtomicInteger();

		// we want an error message larger than can be contained in the db
		String errorMsg = RandomTextUtils.newSecureRandomAlphaNumericString(ERROR_MSG_MAX_LENGTH + 100);

		// we want 1 more error than the allowed maximum
		// otherwise we won't be updating the error chunk to have
		// "Too many errors" error
		int errorCount = MAX_CHUNK_ERROR_COUNT + 1;

		MaxAttemptsRetryPolicy retryPolicy = new MaxAttemptsRetryPolicy();
		retryPolicy.setMaxAttempts(errorCount);
		RetryProviderOverride overrideRetryProvider = (RetryProviderOverride) myRetryPolicyProvider;
		overrideRetryProvider.setPolicies(retryPolicy, new NoBackOffPolicy());

		// create a job that fails and throws a large error
		IJobStepWorker<TestJobParameters, VoidModel, FirstStepOutput> first = (step, sink) -> {
			counter.getAndIncrement();
			throw new RuntimeException(errorMsg);
		};
		IJobStepWorker<TestJobParameters, FirstStepOutput, VoidModel> last = (step, sink) -> {
			// we don't care; we'll never get here
			return RunOutcome.SUCCESS;
		};

		JobDefinition<? extends IModelJson> jd = JobDefinition.newBuilder()
			.setJobDefinitionId(jobId)
			.setJobDescription("test job")
			.setJobDefinitionVersion(TEST_JOB_VERSION)
			.setParametersType(TestJobParameters.class)
			.gatedExecution()
			.addFirstStep(
				FIRST_STEP_ID,
				"First step",
				FirstStepOutput.class,
				first
			)
			.addLastStep(
				LAST_STEP_ID,
				"Final step",
				last
			)
			.completionHandler(myCompletionHandler)
			.build();
		myJobDefinitionRegistry.addJobDefinition(jd);

		// test
		JobInstanceStartRequest request = buildRequest(jobId);
		Batch2JobStartResponse startResponse = myJobCoordinator.startInstance(new SystemRequestDetails(), request);
		String instanceId = startResponse.getInstanceId();

		// block deletion for the test
		doNothing().when(myJobPersistence).deleteChunksAndMarkInstanceAsChunksPurged(instanceId);

		// waiting for the multitude of failures
		await().until(() -> {
			myJobMaintenanceService.runMaintenancePass();
			JobInstance instance = myJobCoordinator.getInstance(instanceId);
			ourLog.info("Attempt " + counter.get() + " for "
				+ instance.getInstanceId() + ". Status: " + instance.getStatus());
			return counter.get() > errorCount - 1;
		});

		// verify
		Iterator<WorkChunk> iterator = myJobPersistence.fetchAllWorkChunksIterator(instanceId, true);
		List<WorkChunk> listOfChunks = new ArrayList<>();
		iterator.forEachRemaining(listOfChunks::add);
		assertEquals(1, listOfChunks.size());
		WorkChunk workChunk = listOfChunks.get(0);
		assertEquals(WorkChunkStatusEnum.FAILED, workChunk.getStatus());
		// should contain some of the original error msg, but not all
		assertTrue(workChunk.getErrorMessage().contains(errorMsg.substring(0, 100)));
		assertTrue(workChunk.getErrorMessage().startsWith("Too many errors"));

		// now we can delete the job/instance/work chunks
		myJobDefinitionRegistry.removeJobDefinition(jd.getJobDefinitionId(), jd.getJobDefinitionVersion());
		myJobPersistence.deleteInstanceAndChunks(instanceId);
	}

	@Test
	public void testJobWithLongPollingStep() throws InterruptedException {
		// create job definition
		int callsToMake = 3;
		int chunksToAwait = 2;
		String jobId = getMethodNameForJobId();

		ConcurrentHashMap<String, AtomicInteger> chunkToCounter = new ConcurrentHashMap<>();
		HashMap<String, Integer> chunkToCallsToMake = new HashMap<>();
		IJobStepWorker<TestJobParameters, VoidModel, FirstStepOutput> first = (step, sink) -> {
			for (int i = 0; i < chunksToAwait; i++) {
				String cv = "chunk" + i;
				chunkToCallsToMake.put(cv, callsToMake);
				sink.accept(new FirstStepOutput().setValue(cv));
			}
			return RunOutcome.SUCCESS;
		};

		// step 2
		IJobStepWorker<TestJobParameters, FirstStepOutput, SecondStepOutput> second = (step, sink) -> {
			// simulate a call
			Awaitility.await().atMost(100, TimeUnit.MICROSECONDS);

			// we use Batch2FastSchedulerConfig, so we have a fast scheduler
			// that should catch and call repeatedly pretty quickly
			String chunkValue = step.getData().myTestValue;
			AtomicInteger pollCounter = chunkToCounter.computeIfAbsent(chunkValue, (key) -> {
				return new AtomicInteger();
			});
			int count = pollCounter.getAndIncrement();

			if (chunkToCallsToMake.get(chunkValue) <= count) {
				sink.accept(new SecondStepOutput());
				return RunOutcome.SUCCESS;
			}
			throw new RetryChunkLaterException(Duration.of(200, ChronoUnit.MILLIS));
		};

		// step 3
		ILastJobStepWorker<TestJobParameters, SecondStepOutput> last = (step, sink) -> {
			myLastStepLatch.call(1);
			return RunOutcome.SUCCESS;
		};

		JobDefinition<? extends IModelJson> jd = JobDefinition.newBuilder()
			.setJobDefinitionId(jobId)
			.setJobDescription("test job")
			.setJobDefinitionVersion(TEST_JOB_VERSION)
			.setParametersType(TestJobParameters.class)
			.gatedExecution()
			.addFirstStep(
				FIRST_STEP_ID,
				"First step",
				FirstStepOutput.class,
				first
			)
			.addIntermediateStep(SECOND_STEP_ID,
				"Second step",
				SecondStepOutput.class,
				second)
			.addLastStep(
				LAST_STEP_ID,
				"Final step",
				last
			)
			.completionHandler(myCompletionHandler)
			.build();
		myJobDefinitionRegistry.addJobDefinition(jd);

		// test
		JobInstanceStartRequest request = buildRequest(jobId);
		myLastStepLatch.setExpectedCount(chunksToAwait);
		Batch2JobStartResponse startResponse = myJobCoordinator.startInstance(new SystemRequestDetails(), request);
		String instanceId = startResponse.getInstanceId();

		// waiting for the job
		myBatch2JobHelper.awaitJobCompletion(startResponse);
		// ensure final step fired
		myLastStepLatch.awaitExpected();

		// verify
		assertEquals(chunksToAwait, chunkToCounter.size());
		for (Map.Entry<String, AtomicInteger> set : chunkToCounter.entrySet()) {
			// +1 because after 0 indexing; it will make callsToMake failed calls (0, 1... callsToMake)
			// and one more successful call (callsToMake + 1)
			assertEquals(callsToMake + 1, set.getValue().get());
		}
	}

	@Test
	public void testFirstStepToSecondStep_doubleChunk_doesNotFastTrack() throws InterruptedException {
		IJobStepWorker<TestJobParameters, VoidModel, FirstStepOutput> firstStep = (step, sink) -> {
			sink.accept(new FirstStepOutput());
			sink.accept(new FirstStepOutput());
			return callLatch(myFirstStepLatch, step);
		};
		IJobStepWorker<TestJobParameters, FirstStepOutput, VoidModel> lastStep = (step, sink) -> callLatch(myLastStepLatch, step);

		String jobDefId = getMethodNameForJobId();
		JobDefinition<? extends IModelJson> definition = buildGatedJobDefinition(jobDefId, firstStep, lastStep);

		myJobDefinitionRegistry.addJobDefinition(definition);

		JobInstanceStartRequest request = buildRequest(jobDefId);

		myFirstStepLatch.setExpectedCount(1);
		Batch2JobStartResponse startResponse = myJobCoordinator.startInstance(new SystemRequestDetails(), request);
		String instanceId = startResponse.getInstanceId();
		myBatch2JobHelper.runMaintenancePass();
		myFirstStepLatch.awaitExpected();

		myLastStepLatch.setExpectedCount(2);
		myBatch2JobHelper.awaitJobCompletion(instanceId);
		myLastStepLatch.awaitExpected();

		// Now we've processed 2 chunks so we are no longer fast tracking
		myBatch2JobHelper.assertNotFastTracking(instanceId);
	}


	@Test
	public void jobExecutionFailedException_CausesInstanceFailure() {
		// setup
		IJobStepWorker<TestJobParameters, VoidModel, FirstStepOutput> firstStep = (step, sink) -> {
			throw new JobExecutionFailedException("Expected Test Exception");
		};
		IJobStepWorker<TestJobParameters, FirstStepOutput, VoidModel> lastStep = (step, sink) -> fail();

		String jobDefId = getMethodNameForJobId();
		JobDefinition<? extends IModelJson> definition = buildGatedJobDefinition(jobDefId, firstStep, lastStep);

		myJobDefinitionRegistry.addJobDefinition(definition);

		JobInstanceStartRequest request = buildRequest(jobDefId);

		// execute
		Batch2JobStartResponse startResponse = myJobCoordinator.startInstance(new SystemRequestDetails(), request);
		String instanceId = startResponse.getInstanceId();

		// validate
		myBatch2JobHelper.awaitJobFailure(instanceId);
	}

	@Test
	public void testUnknownException_KeepsInProgress_CanCancelManually() throws InterruptedException {
		// setup

		// we want to control the maintenance runner ourselves in this case
		// to prevent intermittent test failures
		myJobMaintenanceService.enableMaintenancePass(false);

		try {
			IJobStepWorker<TestJobParameters, VoidModel, FirstStepOutput> firstStep = (step, sink) -> {
				callLatch(myFirstStepLatch, step);
				throw new RuntimeException("Expected Test Exception");
			};
			IJobStepWorker<TestJobParameters, FirstStepOutput, VoidModel> lastStep = (step, sink) -> fail();

			String jobDefId = getMethodNameForJobId();
			JobDefinition<? extends IModelJson> definition = buildGatedJobDefinition(jobDefId, firstStep, lastStep);

			myJobDefinitionRegistry.addJobDefinition(definition);

			JobInstanceStartRequest request = buildRequest(jobDefId);

			// execute
			ourLog.info("Starting job");
			myFirstStepLatch.setExpectedCount(1);
			Batch2JobStartResponse startResponse = myJobCoordinator.startInstance(new SystemRequestDetails(), request);
			String instanceId = startResponse.getInstanceId();
			myBatch2JobHelper.forceRunMaintenancePass();
			myFirstStepLatch.awaitExpected();

			// validate
			myBatch2JobHelper.awaitJobHasStatusWithForcedMaintenanceRuns(instanceId, StatusEnum.ERRORED);

			// execute
			ourLog.info("Cancel job {}", instanceId);
			myJobCoordinator.cancelInstance(instanceId);
			ourLog.info("Cancel job {} done", instanceId);

			// validate
			myBatch2JobHelper.awaitJobHasStatusWithForcedMaintenanceRuns(instanceId,
				StatusEnum.CANCELLED);
		} finally {
			myJobMaintenanceService.enableMaintenancePass(true);
		}
	}

	@Test
	public void testStepRunFailure_continuouslyThrows_marksJobFailed() {

		// setup
		AtomicInteger counter = new AtomicInteger();
		// step 1
		IJobStepWorker<TestJobParameters, VoidModel, FirstStepOutput> first = (step, sink) -> {
			counter.getAndIncrement();
			throw new RuntimeException("Exception");
		};
		// final step
		ILastJobStepWorker<TestJobParameters, FirstStepOutput> last = (step, sink) -> {
			fail("We should never hit this last step");
			return RunOutcome.SUCCESS;
		};
		// job definition
		String jobDefId = getMethodNameForJobId();
		JobDefinition<? extends IModelJson> jd = JobDefinition.newBuilder()
			.setJobDefinitionId(jobDefId)
			.setJobDescription("test job")
			.setJobDefinitionVersion(TEST_JOB_VERSION)
			.setParametersType(TestJobParameters.class)
			.gatedExecution()
			.addFirstStep(
				FIRST_STEP_ID,
				"Test first step",
				FirstStepOutput.class,
				first
			)
			.addLastStep(
				LAST_STEP_ID,
				"Test last step",
				last
			)
			.build();
		myJobDefinitionRegistry.addJobDefinition(jd);
		// test
		JobInstanceStartRequest request = buildRequest(jobDefId);
		myFirstStepLatch.setExpectedCount(1);
		Batch2JobStartResponse response = myJobCoordinator.startInstance(new SystemRequestDetails(), request);
		JobInstance instance = myBatch2JobHelper.awaitJobHasStatus(response.getInstanceId(),
			30, // we want to wait a long time (2 min here) cause backoff is incremental
			StatusEnum.FAILED
		);

		assertEquals(MAX_CHUNK_ERROR_COUNT + 1, counter.get());

		assertThat(instance.getStatus()).isSameAs(StatusEnum.FAILED);
	}

	@Nonnull
	private JobInstanceStartRequest buildRequest(String jobId) {
		JobInstanceStartRequest request = new JobInstanceStartRequest();
		request.setJobDefinitionId(jobId);
		TestJobParameters parameters = new TestJobParameters();
		request.setParameters(parameters);
		return request;
	}

	/**
	 * Returns the method name of the calling method for a unique job id.
	 * It is best this is called from the test method directly itself, and never
	 * delegate to a separate child method.s
	 */
	private String getMethodNameForJobId() {
		return new Exception().getStackTrace()[1].getMethodName();
	}

	@Nonnull
	private JobDefinition<? extends IModelJson> buildGatedJobDefinition(String theJobId, IJobStepWorker<TestJobParameters, VoidModel, FirstStepOutput> theFirstStep, IJobStepWorker<TestJobParameters, FirstStepOutput, VoidModel> theLastStep) {
		return JobDefinition.newBuilder()
			.setJobDefinitionId(theJobId)
			.setJobDescription("test job")
			.setJobDefinitionVersion(TEST_JOB_VERSION)
			.setParametersType(TestJobParameters.class)
			.gatedExecution()
			.addFirstStep(
				FIRST_STEP_ID,
				"Test first step",
				FirstStepOutput.class,
				theFirstStep
			)
			.addLastStep(
				LAST_STEP_ID,
				"Test last step",
				theLastStep
			)
			.completionHandler(myCompletionHandler)
			.build();
	}


	private void buildAndDefine3StepReductionJob(
		String theJobId,
		IReductionStepHandler theHandler
	) {
		// step 1
		IJobStepWorker<TestJobParameters, VoidModel, FirstStepOutput> first = (step, sink) -> {
			theHandler.firstStep(step, sink);
			return RunOutcome.SUCCESS;
		};

		// step 2
		IJobStepWorker<TestJobParameters, FirstStepOutput, SecondStepOutput> second = (step, sink) -> {
			theHandler.secondStep(step, sink);
			return RunOutcome.SUCCESS;
		};

		// step 3
		IReductionStepWorker<TestJobParameters, SecondStepOutput, ReductionStepOutput> last = new IReductionStepWorker<>() {

			@Nonnull
			@Override
			public ChunkOutcome consume(ChunkExecutionDetails<TestJobParameters, SecondStepOutput> theChunkDetails) {
				theHandler.reductionStepConsume(theChunkDetails, null);
				return ChunkOutcome.SUCCESS();
			}

			@Override
			public IReductionStepWorker<TestJobParameters, SecondStepOutput, ReductionStepOutput> newInstance() {
				return this;
			}

			@Nonnull
			@Override
			public RunOutcome run(
				@Nonnull StepExecutionDetails<TestJobParameters, SecondStepOutput> theStepExecutionDetails,
				@Nonnull IJobDataSink<ReductionStepOutput> theDataSink
			) throws JobExecutionFailedException {
				theHandler.reductionStepRun(theStepExecutionDetails, theDataSink);
				return RunOutcome.SUCCESS;
			}
		};
		createThreeStepReductionJob(theJobId, first, second, last);
	}

	private void createThreeStepReductionJob(
		String theJobId,
		IJobStepWorker<TestJobParameters, VoidModel, FirstStepOutput> theFirstStep,
		IJobStepWorker<TestJobParameters, FirstStepOutput, SecondStepOutput> theSecondStep,
		IReductionStepWorker<TestJobParameters, SecondStepOutput, ReductionStepOutput> theReductionsStep
	) {
		// create job definition (it's the test method's name)
		JobDefinition<? extends IModelJson> jd = JobDefinition.newBuilder()
			.setJobDefinitionId(theJobId)
			.setJobDescription("test job")
			.setJobDefinitionVersion(TEST_JOB_VERSION)
			.setParametersType(TestJobParameters.class)
			.gatedExecution()
			.addFirstStep(
				FIRST_STEP_ID,
				"Test first step",
				FirstStepOutput.class,
				theFirstStep
			)
			.addIntermediateStep(SECOND_STEP_ID,
				"Second step",
				SecondStepOutput.class,
				theSecondStep)
			.addFinalReducerStep(
				LAST_STEP_ID,
				"Test last step",
				ReductionStepOutput.class,
				theReductionsStep
			)
			.completionHandler(myCompletionHandler)
			.build();
		myJobDefinitionRegistry.removeJobDefinition(theJobId, 1);
		myJobDefinitionRegistry.addJobDefinition(jd);
	}

	static class TestJobParameters implements IModelJson {
		TestJobParameters() {
		}
	}

	static class FirstStepOutput implements IModelJson {
		@JsonProperty("test")
		private String myTestValue;

		FirstStepOutput() {
		}

		public FirstStepOutput setValue(String theV) {
			myTestValue = theV;
			return this;
		}
	}

	static class SecondStepOutput implements IModelJson {
		@JsonProperty("test")
		private String myTestValue;

		SecondStepOutput() {
		}

		public void setValue(String theV) {
			myTestValue = theV;
		}
	}

	static class ReductionStepOutput implements IModelJson {
		@JsonProperty("result")
		private List<?> myResult;

		ReductionStepOutput(List<?> theResult) {
			myResult = theResult;
		}
	}

	private interface IReductionStepHandler {
		void firstStep(StepExecutionDetails<TestJobParameters, VoidModel> theStep, IJobDataSink<FirstStepOutput> theDataSink);

		void secondStep(StepExecutionDetails<TestJobParameters, FirstStepOutput> theStep, IJobDataSink<SecondStepOutput> theDataSink);

		void reductionStepConsume(ChunkExecutionDetails<TestJobParameters, SecondStepOutput> theChunkDetails, IJobDataSink<ReductionStepOutput> theDataSink);

		void reductionStepRun(StepExecutionDetails<TestJobParameters, SecondStepOutput> theStepExecutionDetails, IJobDataSink<ReductionStepOutput> theDataSink);
	}
}

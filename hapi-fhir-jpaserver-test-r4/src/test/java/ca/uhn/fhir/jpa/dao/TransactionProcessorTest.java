package ca.uhn.fhir.jpa.dao;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.interceptor.executor.InterceptorService;
import ca.uhn.fhir.jpa.api.config.JpaStorageSettings;
import ca.uhn.fhir.jpa.api.config.ThreadPoolFactoryConfig;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirSystemDao;
import ca.uhn.fhir.jpa.api.svc.IIdHelperService;
import ca.uhn.fhir.jpa.cache.IResourceVersionSvc;
import ca.uhn.fhir.jpa.dao.r4.TransactionProcessorVersionAdapterR4;
import ca.uhn.fhir.jpa.dao.tx.IHapiTransactionService;
import ca.uhn.fhir.jpa.dao.tx.NonTransactionalHapiTransactionService;
import ca.uhn.fhir.jpa.model.config.PartitionSettings;
import ca.uhn.fhir.jpa.partition.IRequestPartitionHelperSvc;
import ca.uhn.fhir.jpa.search.ResourceSearchUrlSvc;
import ca.uhn.fhir.jpa.searchparam.MatchUrlService;
import ca.uhn.fhir.jpa.searchparam.matcher.InMemoryResourceMatcher;
import ca.uhn.fhir.jpa.searchparam.matcher.SearchParamMatcher;
import ca.uhn.fhir.jpa.util.MemoryCacheService;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.hibernate.internal.SessionImpl;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MedicationKnowledge;
import org.hl7.fhir.r4.model.Meta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TransactionProcessorTest.MyConfig.class)
public class TransactionProcessorTest {

	private static final Logger ourLog = LoggerFactory.getLogger(TransactionProcessorTest.class);
	@Autowired
	private TransactionProcessor myTransactionProcessor;
	@MockBean
	private DaoRegistry myDaoRegistry;
	@MockBean
	private EntityManagerFactory myEntityManagerFactory;
	@MockBean(answer = Answers.RETURNS_DEEP_STUBS)
	private EntityManager myEntityManager;
	@MockBean
	private PlatformTransactionManager myPlatformTransactionManager;
	@MockBean
	private MatchResourceUrlService myMatchResourceUrlService;
	@MockBean
	private InMemoryResourceMatcher myInMemoryResourceMatcher;
	@MockBean
	private IIdHelperService myIdHelperService;
	@MockBean
	private PartitionSettings myPartitionSettings;
	@MockBean
	private MatchUrlService myMatchUrlService;
	@MockBean
	private IRequestPartitionHelperSvc myRequestPartitionHelperSvc;
	@MockBean
	private IResourceVersionSvc myResourceVersionSvc;
	@MockBean
	private SearchParamMatcher mySearchParamMatcher;
	@MockBean(answer = Answers.RETURNS_DEEP_STUBS)
	private SessionImpl mySession;
	@MockBean
	private IFhirSystemDao<Bundle, Meta> mySystemDao;
	@MockBean
	private ResourceSearchUrlSvc myResourceSearchUrlSvc;
	@MockBean
	private MemoryCacheService myMemoryCacheService;

	@BeforeEach
	public void before() {
		myTransactionProcessor.setEntityManagerForUnitTest(myEntityManager);
		when(myEntityManager.unwrap(eq(Session.class))).thenReturn(mySession);
	}


	@Test
	public void testTransactionWithDisabledResourceType() {

		Bundle input = new Bundle();
		input.setType(Bundle.BundleType.TRANSACTION);

		MedicationKnowledge medKnowledge = new MedicationKnowledge();
		medKnowledge.setStatus(MedicationKnowledge.MedicationKnowledgeStatus.ACTIVE);
		input
			.addEntry()
			.setResource(medKnowledge)
			.getRequest()
			.setMethod(Bundle.HTTPVerb.POST)
			.setUrl("/MedicationKnowledge");

		try {
			myTransactionProcessor.transaction(null, input, false);
			fail();
		} catch (InvalidRequestException e) {
			assertEquals(Msg.code(544) + "Resource MedicationKnowledge is not supported on this server. Supported resource types: []", e.getMessage());
		}
	}


	@Configuration
	@Import(ThreadPoolFactoryConfig.class)
	public static class MyConfig {

		@Bean
		public DaoRegistry daoRegistry() {
			return new DaoRegistry();
		}

		@Bean
		public FhirContext fhirContext() {
			return FhirContext.forR4Cached();
		}

		@Bean
		public JpaStorageSettings storageSettings() {
			return new JpaStorageSettings();
		}

		@Bean
		public TransactionProcessor transactionProcessor() {
			return new TransactionProcessor();
		}

		@Bean
		public InterceptorService interceptorService() {
			return new InterceptorService();
		}

		@Bean
		public ITransactionProcessorVersionAdapter<Bundle, Bundle.BundleEntryComponent> versionAdapter() {
			return new TransactionProcessorVersionAdapterR4();
		}

		@Bean
		public IHapiTransactionService hapiTransactionService() {
			return new NonTransactionalHapiTransactionService();
		}
	}
}

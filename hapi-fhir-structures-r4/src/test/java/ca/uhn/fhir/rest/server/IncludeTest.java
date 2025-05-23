package ca.uhn.fhir.rest.server;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.api.BundleInclusionRule;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.Extension;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import ca.uhn.fhir.model.primitive.StringDt;
import ca.uhn.fhir.rest.annotation.IncludeParam;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.test.utilities.HttpClientExtension;
import ca.uhn.fhir.test.utilities.server.RestfulServerExtension;
import ca.uhn.fhir.util.BundleUtil;
import ca.uhn.fhir.util.ElementUtil;
import ca.uhn.fhir.util.TestUtil;

import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;


public class IncludeTest {

	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(IncludeTest.class);
	private static final FhirContext ourCtx = FhirContext.forR4Cached();

	@RegisterExtension
	public RestfulServerExtension ourServer = new RestfulServerExtension(ourCtx)
		 .registerProvider(new DummyPatientResourceProvider())
		 .registerProvider(new DummyDiagnosticReportResourceProvider())
		 .withPagingProvider(new FifoMemoryPagingProvider(100))
		 .setDefaultResponseEncoding(EncodingEnum.XML)
		 .withServer(s->s.setBundleInclusionRule(BundleInclusionRule.BASED_ON_RESOURCE_PRESENCE));

	@RegisterExtension
	private HttpClientExtension ourClient = new HttpClientExtension();

	@Test
	public void testBadInclude() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?name=Hello&_include=foo&_include=baz");
		try (CloseableHttpResponse status = ourClient.execute(httpGet)) {
			assertEquals(400, status.getStatusLine().getStatusCode());
			String responseContent = IOUtils.toString(status.getEntity().getContent(), StandardCharsets.UTF_8);

			ourLog.info(responseContent);
			assertThat(responseContent).contains("Invalid _include parameter value");
		}
	}

	@Test
	public void testIIncludedResourcesNonContained() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?_query=normalInclude&_pretty=true");
		try (CloseableHttpResponse status = ourClient.execute(httpGet)) {
			String responseContent = IOUtils.toString(status.getEntity().getContent(), StandardCharsets.UTF_8);

			assertEquals(200, status.getStatusLine().getStatusCode());
			Bundle bundle = ourCtx.newXmlParser().parseResource(Bundle.class, responseContent);

			ourLog.info(responseContent);

			assertThat(bundle.getEntry()).hasSize(3);

			assertEquals(("Patient/p1"), bundle.getEntry().get(0).getResource().getIdElement().toUnqualifiedVersionless().getValue());
			assertEquals(("Patient/p2"), bundle.getEntry().get(1).getResource().getIdElement().toUnqualifiedVersionless().getValue());
			assertEquals(("Organization/o1"), bundle.getEntry().get(2).getResource().getIdElement().toUnqualifiedVersionless().getValue());

			Patient p1 = (Patient) bundle.getEntry().get(0).getResource();
			assertThat(p1.getContained()).isEmpty();

			Patient p2 = (Patient) bundle.getEntry().get(1).getResource();
			assertThat(p2.getContained()).isEmpty();

		}
	}

	@Test
	public void testIIncludedResourcesNonContainedInDeclaredExtension() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?_query=declaredExtInclude&_pretty=true");
		try (CloseableHttpResponse status = ourClient.execute(httpGet)) {
			String responseContent = IOUtils.toString(status.getEntity().getContent(), StandardCharsets.UTF_8);

			assertEquals(200, status.getStatusLine().getStatusCode());
			Bundle bundle = ourCtx.newXmlParser().parseResource(Bundle.class, responseContent);

			ourLog.info(responseContent);

			assertThat(bundle.getEntry()).hasSize(4);
			assertEquals(("Patient/p1"), bundle.getEntry().get(0).getResource().getIdElement().toUnqualifiedVersionless().getValue());
			assertEquals(("Patient/p2"), bundle.getEntry().get(1).getResource().getIdElement().toUnqualifiedVersionless().getValue());
			assertEquals(("Organization/o1"), bundle.getEntry().get(2).getResource().getIdElement().toUnqualifiedVersionless().getValue());
			assertEquals(("Organization/o2"), bundle.getEntry().get(3).getResource().getIdElement().toUnqualifiedVersionless().getValue());

			Patient p1 = (Patient) bundle.getEntry().get(0).getResource();
			assertThat(p1.getContained()).isEmpty();

			Patient p2 = (Patient) bundle.getEntry().get(1).getResource();
			assertThat(p2.getContained()).isEmpty();

		}
	}

	@Test
	public void testIIncludedResourcesNonContainedInExtension() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?_query=extInclude&_pretty=true");
		try (CloseableHttpResponse status = ourClient.execute(httpGet)) {
			String responseContent = IOUtils.toString(status.getEntity().getContent(), StandardCharsets.UTF_8);

			assertEquals(200, status.getStatusLine().getStatusCode());
			Bundle bundle = ourCtx.newXmlParser().parseResource(Bundle.class, responseContent);

			ourLog.info(responseContent);

			assertThat(bundle.getEntry()).hasSize(3);
			assertEquals(("Patient/p1"), bundle.getEntry().get(0).getResource().getIdElement().toUnqualifiedVersionless().getValue());
			assertEquals(("Patient/p2"), bundle.getEntry().get(1).getResource().getIdElement().toUnqualifiedVersionless().getValue());
			assertEquals(("Organization/o1"), bundle.getEntry().get(2).getResource().getIdElement().toUnqualifiedVersionless().getValue());

			Patient p1 = (Patient) bundle.getEntry().get(0).getResource();
			assertThat(p1.getContained()).isEmpty();

			Patient p2 = (Patient) bundle.getEntry().get(1).getResource();
			assertThat(p2.getContained()).isEmpty();

		}
	}

	@Test
	public void testIIncludedResourcesNonContainedInExtensionJson() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?_query=extInclude&_pretty=true&_format=json");
		try (CloseableHttpResponse status = ourClient.execute(httpGet)) {
			String responseContent = IOUtils.toString(status.getEntity().getContent(), StandardCharsets.UTF_8);

			assertEquals(200, status.getStatusLine().getStatusCode());
			Bundle bundle = ourCtx.newJsonParser().parseResource(Bundle.class, responseContent);

			ourLog.info(responseContent);

			assertThat(bundle.getEntry()).hasSize(3);
			assertEquals("Patient/p1", bundle.getEntry().get(0).getResource().getIdElement().toUnqualifiedVersionless().getValue());
			assertEquals("Patient/p2", bundle.getEntry().get(1).getResource().getIdElement().toUnqualifiedVersionless().getValue());
			assertEquals("Organization/o1", bundle.getEntry().get(2).getResource().getIdElement().toUnqualifiedVersionless().getValue());

			Patient p1 = (Patient) bundle.getEntry().get(0).getResource();
			assertThat(p1.getContained()).isEmpty();

			Patient p2 = (Patient) bundle.getEntry().get(1).getResource();
			assertThat(p2.getContained()).isEmpty();

		}
	}

	@Test
	public void testIncludeWithType() {
		assertEquals("Patient:careProvider:Practitioner", new Include("Patient:careProvider", true).withType("Practitioner").getValue());
		assertEquals(true, new Include("Patient:careProvider", true).withType("Practitioner").isRecurse());
		assertEquals(false, new Include("Patient:careProvider:Organization", true).withType("Practitioner").isLocked());
		assertEquals("Practitioner", new Include("Patient:careProvider", true).withType("Practitioner").getParamTargetType());
		assertNull(new Include("Patient:careProvider", true).getParamTargetType());

		assertEquals("Patient:careProvider:Practitioner", new Include("Patient:careProvider:Organization", true).withType("Practitioner").getValue());
		assertEquals(true, new Include("Patient:careProvider:Organization", true).toLocked().withType("Practitioner").isLocked());

		try {
			new Include("").withType("Patient");
			fail();		} catch (IllegalStateException e) {
			// good
		}
		try {
			new Include("Patient").withType("Patient");
			fail();		} catch (IllegalStateException e) {
			// good
		}
		try {
			new Include("Patient:").withType("Patient");
			fail();		} catch (IllegalStateException e) {
			// good
		}
	}

	@Test
	public void testNoIncludes() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?name=Hello");
		try (CloseableHttpResponse status = ourClient.execute(httpGet)) {
			String responseContent = IOUtils.toString(status.getEntity().getContent(), StandardCharsets.UTF_8);

			assertEquals(200, status.getStatusLine().getStatusCode());
			Bundle bundle = ourCtx.newXmlParser().parseResource(Bundle.class, responseContent);
			assertThat(bundle.getEntry()).hasSize(1);

			Patient p = BundleUtil.toListOfResourcesOfType(ourCtx, bundle, Patient.class).get(0);
			assertThat(p.getName()).isEmpty();
			assertEquals("Hello", p.getIdElement().getIdPart());
		}
	}

	@Test
	public void testOneInclude() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?name=Hello&_include=foo");
		try (CloseableHttpResponse status = ourClient.execute(httpGet)) {
			String responseContent = IOUtils.toString(status.getEntity().getContent(), StandardCharsets.UTF_8);

			assertEquals(200, status.getStatusLine().getStatusCode());
			Bundle bundle = ourCtx.newXmlParser().parseResource(Bundle.class, responseContent);
			assertThat(bundle.getEntry()).hasSize(1);

			Patient p = BundleUtil.toListOfResourcesOfType(ourCtx, bundle, Patient.class).get(0);
			assertThat(p.getName()).hasSize(1);
			assertEquals("Hello", p.getIdElement().getIdPart());
			assertEquals("foo-false", p.getName().get(0).getFamily());
		}
	}

	@Test
	public void testOneIncludeIterate() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?name=Hello&" + Constants.PARAM_INCLUDE_ITERATE + "=foo");
		try (CloseableHttpResponse status = ourClient.execute(httpGet)) {
			String responseContent = IOUtils.toString(status.getEntity().getContent(), StandardCharsets.UTF_8);

			assertEquals(200, status.getStatusLine().getStatusCode());
			Bundle bundle = ourCtx.newXmlParser().parseResource(Bundle.class, responseContent);
			assertThat(bundle.getEntry()).hasSize(1);

			Patient p = BundleUtil.toListOfResourcesOfType(ourCtx, bundle, Patient.class).get(0);
			assertThat(p.getName()).hasSize(1);
			assertEquals("Hello", p.getIdElement().getIdPart());
			assertEquals("foo-true", p.getName().get(0).getFamily());
		}
	}

	@Test
	public void testTwoInclude() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?name=Hello&_include=foo&_include=bar");
		try (CloseableHttpResponse status = ourClient.execute(httpGet)) {
			String responseContent = IOUtils.toString(status.getEntity().getContent(), StandardCharsets.UTF_8);

			assertEquals(200, status.getStatusLine().getStatusCode());
			Bundle bundle = ourCtx.newXmlParser().parseResource(Bundle.class, responseContent);
			assertThat(bundle.getEntry()).hasSize(1);

			Patient p = BundleUtil.toListOfResourcesOfType(ourCtx, bundle, Patient.class).get(0);
			assertThat(p.getName()).hasSize(2);
			assertEquals("Hello", p.getIdElement().getIdPart());

			Set<String> values = new HashSet<String>();
			values.add(p.getName().get(0).getFamily());
			values.add(p.getName().get(1).getFamily());
			assertThat(values).containsExactlyInAnyOrder("foo-false", "bar-false");

		}
	}

	@Test
	public void testStringInclude() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?_query=stringInclude&_include=foo");
		try (CloseableHttpResponse status = ourClient.execute(httpGet)) {
			String responseContent = IOUtils.toString(status.getEntity().getContent(), StandardCharsets.UTF_8);

			assertEquals(200, status.getStatusLine().getStatusCode());
			Bundle bundle = ourCtx.newXmlParser().parseResource(Bundle.class, responseContent);
			assertThat(bundle.getEntry()).hasSize(1);

			Patient p = BundleUtil.toListOfResourcesOfType(ourCtx, bundle, Patient.class).get(0);
			assertEquals("foo", p.getIdentifierFirstRep().getValue());
		}
	}

	public static class DummyDiagnosticReportResourceProvider implements IResourceProvider {

		@Override
		public Class<DiagnosticReport> getResourceType() {
			return DiagnosticReport.class;
		}

		@Search(queryName = "stitchedInclude")
		public List<DiagnosticReport> stitchedInclude() {
			Practitioner pr1 = new Practitioner();
			pr1.setId("Practitioner/001");
			pr1.addName().setFamily("Pract1");

			Practitioner pr2 = new Practitioner();
			pr2.setId("Practitioner/002");
			pr2.addName().setFamily("Pract2");

			Practitioner pr3 = new Practitioner();
			pr3.setId("Practitioner/003");
			pr3.addName().setFamily("Pract3");

			Observation o1 = new Observation();
			o1.getCode().setText("Obs1");
			o1.addPerformer().setResource(pr1);

			Observation o2 = new Observation();
			o2.getCode().setText("Obs2");
			o2.addPerformer().setResource(pr2);

			Observation o3 = new Observation();
			o3.getCode().setText("Obs3");
			o3.addPerformer().setResource(pr3);

			DiagnosticReport rep = new DiagnosticReport();
			rep.setId("DiagnosticReport/999");
			rep.getCode().setText("Rep");
			rep.addResult().setResource(o1);
			rep.addResult().setResource(o2);
			rep.addResult().setResource(o3);

			return Collections.singletonList(rep);
		}

	}

	public static class DummyPatientResourceProvider implements IResourceProvider {

		@Search(queryName = "containedInclude")
		public List<Patient> containedInclude() {
			Organization o1 = new Organization();
			o1.getNameElement().setValue("o1");

			Patient p1 = new Patient();
			p1.setId("p1");
			p1.addIdentifier().setValue("p1");
			p1.getManagingOrganization().setResource(o1);

			Patient p2 = new Patient();
			p2.setId("p2");
			p2.addIdentifier().setValue("p2");
			p2.getManagingOrganization().setResource(o1);

			return Arrays.asList(p1, p2);
		}

		@Search(queryName = "declaredExtInclude")
		public List<ExtPatient> declaredExtInclude() {
			Organization o1 = new Organization();
			o1.getNameElement().setValue("o1");
			o1.setId("o1");

			Organization o2 = new Organization();
			o2.getNameElement().setValue("o2");
			o2.setId("o2");
			o1.getPartOf().setResource(o2);

			ExtPatient p1 = new ExtPatient();
			p1.setId("p1");
			p1.addIdentifier().setValue("p1");
			p1.getSecondOrg().setResource(o1);

			ExtPatient p2 = new ExtPatient();
			p2.setId("p2");
			p2.addIdentifier().setValue("p2");
			p2.getSecondOrg().setResource(o1);

			return Arrays.asList(p1, p2);
		}

		@Search(queryName = "extInclude")
		public List<Patient> extInclude() {
			Organization o1 = new Organization();
			o1.getNameElement().setValue("o1");
			o1.setId("o1");

			Patient p1 = new Patient();
			p1.setId("p1");
			p1.addIdentifier().setValue("p1");
			p1.addExtension(new org.hl7.fhir.r4.model.Extension("http://foo", new Reference(o1)));

			Patient p2 = new Patient();
			p2.setId("p2");
			p2.addIdentifier().setValue("p2");
			p2.addExtension(new org.hl7.fhir.r4.model.Extension("http://foo", new Reference(o1)));

			return Arrays.asList(p1, p2);
		}

		@Search
		public List<Patient> findPatientWithSimpleNames(@RequiredParam(name = Patient.SP_NAME) StringDt theName, @IncludeParam(allow = {"foo", "bar"}) Set<Include> theIncludes) {
			ArrayList<Patient> retVal = new ArrayList<>();

			Patient p = new Patient();
			p.addIdentifier().setSystem("Mr").setValue("Test");

			p.setId(theName.getValue());

			if (theIncludes != null) {
				for (Include next : theIncludes) {
					p.addName().setFamily(next.getValue() + "-" + next.isRecurse());
				}
			}
			retVal.add(p);

			return retVal;
		}


		@Search(queryName = "stringInclude")
		public List<Patient> stringInclude(@IncludeParam String theInclude) {
			Patient p = new Patient();
			p.setId("p");
			p.addIdentifier().setValue(theInclude);

			return Arrays.asList(p);
		}


		@Override
		public Class<Patient> getResourceType() {
			return Patient.class;
		}

		@Search(queryName = "normalInclude")
		public List<Patient> normalInclude() {
			Organization o1 = new Organization();
			o1.getNameElement().setValue("o1");
			o1.setId("o1");

			Patient p1 = new Patient();
			p1.setId("p1");
			p1.addIdentifier().setValue("p1");
			p1.getManagingOrganization().setResource(o1);

			Patient p2 = new Patient();
			p2.setId("p2");
			p2.addIdentifier().setValue("p2");
			p2.getManagingOrganization().setResource(o1);

			return Arrays.asList(p1, p2);
		}

	}

	@ResourceDef(name = "Patient")
	public static class ExtPatient extends Patient {
		private static final long serialVersionUID = 1L;

		@Child(name = "secondOrg")
		@Extension(url = "http://foo#secondOrg", definedLocally = false, isModifier = false)
		private Reference mySecondOrg;

		public Reference getSecondOrg() {
			if (mySecondOrg == null) {
				mySecondOrg = new Reference();
			}
			return mySecondOrg;
		}

		@Override
		public boolean isEmpty() {
			return super.isEmpty() && ElementUtil.isEmpty(mySecondOrg);
		}

	}

	@AfterAll
	public static void afterClassClearContext() throws Exception {
		TestUtil.randomizeLocaleAndTimezone();
	}
	
}

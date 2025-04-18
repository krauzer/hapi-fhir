/** test ca.uhn.fhir.parser
 * This parses the FHIR JSON examples and round-trips them through RDF.
 * See testRDFRoundTrip() for details.
 *
 * editors:
 * - Eric Prud'hommeaux <eric@w3.org>
 *
 * TODO:
 * - Consider sharing the FHIR JSON examples in
 *   ../../../../../resources/rdf-test-input/ with other HAPI tests and move to
 *   resources/examples/JSON.
 * - Add FHIR RDF examples and validate graph isomorphism with examples/RDF (or
 *   examples/Turtle).
 *
 * see also:
 *   ../../../../../../../../hapi-fhir-base/src/main/java/ca/uhn/fhir/parser/RDFParser.java
 * run test:
 *   hapi-fhir/hapi-fhir-structures-r4$ mvn -Dtest=ca.uhn.fhir.parser.RDFParserTest test
 */

package ca.uhn.fhir.parser;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.test.BaseTest;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.Lang;

import org.apache.jena.shex.Shex;
import org.apache.jena.shex.ShexReport;
import org.apache.jena.shex.ShexSchema;
import org.apache.jena.shex.ShexValidator;
import org.apache.jena.shex.ShapeMap;

import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static ca.uhn.fhir.parser.JsonParserR4Test.createBundleWithCrossReferenceFullUrlsAndNoIds;
import static ca.uhn.fhir.parser.JsonParserR4Test.createBundleWithCrossReferenceFullUrlsAndNoIds_NestedInParameters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RDFParserTest extends BaseTest {

	public static final String NODE_ROLE_IRI = "http://hl7.org/fhir/nodeRole";
	public static final String TREE_ROOT_IRI = "http://hl7.org/fhir/treeRoot";
	public static final String FHIR_SHAPE_PREFIX = "http://hl7.org/fhir/shape/";
	private static final FhirContext ourCtx = FhirContext.forR4();
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(RDFParserTest.class);
	private static ShexSchema fhirShexSchema = null;

	@BeforeAll
	static void parseShExSchema() throws Exception {
		Path schemaFile = Paths.get("target", "test-classes", "rdf-validation", "fhir-r4.shex");
		fhirShexSchema = Shex.readSchema(schemaFile.toUri().toString());
	}

	/**
	 * This test method has a method source for each JSON file in the resources/rdf-test-input directory (see #getInputFiles).
	 * Each input file is expected to be a JSON representation of an R4 FHIR resource.
	 * Each input file is put through the following steps to ensure valid RDF and round-trip-ability in HAPI-FHIR:
	 * 1. Parse the JSON into the HAPI object model -- ensure resource instance is not null
	 * 2. Encode the JSON-originated instance as an RDF string using the RDF Parser -- ensure RDF string is not null
	 * 3. Perform a graph validation on the resulting RDF using ShEx and ShEx-java -- ensure validation passed
	 * 4. Parse the RDF string into the HAPI object model -- ensure resource instance is not null
	 * 5. Perform deep equals comparison of JSON-originated instance and RDF-originated instance -- ensure equality
	 * @param referenceFilePath -- path to resource file to be tested
	 * @throws IOException -- thrown when parsing RDF string into graph model
	 */
	@ParameterizedTest
	@MethodSource("getInputFiles")
	@Execution(ExecutionMode.CONCURRENT)
	public void testRDFRoundTrip(String referenceFilePath) throws IOException {
		String referenceFileName = referenceFilePath.substring(referenceFilePath.lastIndexOf("/")+1);
		IBaseResource referenceResource = parseJson(new FileInputStream(referenceFilePath));
		String referenceJson = serializeJson(ourCtx, referenceResource);

		// Perform ShEx validation on RDF
		String turtleString = serializeRdf(ourCtx, referenceResource);
		validateRdf(turtleString, referenceFileName, referenceResource);

		// Parse RDF content as resource
		IBaseResource viaTurtleResource = parseRdf(ourCtx, new StringReader(turtleString));
		assertNotNull(viaTurtleResource);

		// Compare original JSON-based resource against RDF-based resource
		String viaTurtleJson = serializeJson(ourCtx, viaTurtleResource);
		if (!((Base)viaTurtleResource).equalsDeep((Base)referenceResource)) {
			String failMessage = referenceFileName + ": failed to round-trip Turtle ";
			if (referenceJson.equals(viaTurtleJson))
				throw new Error(failMessage
					+ "\nttl: " + turtleString
					+ "\nexp: " + referenceJson);
			else
				assertThat(viaTurtleJson).as(failMessage + "\nttl: " + turtleString).isEqualTo(referenceJson);
		}
	}

	/**
	 * {@link IParser#parseInto(String, IBase)} isn't currently supported by
	 * the RDF parser.
	 */
	@Test
	public void testParseInto() {
		String input = "fhir:ActivityDefinition.code    [ fhir:CodeableConcept.coding  [ fhir:Coding.code    [ fhir:value  \"zika-virus-exposure-assessment\" ];";

		InternalErrorException e = assertThrows(InternalErrorException.class, () -> ourCtx.newRDFParser().parseInto(input, new CodeableConcept()));
		assertEquals("HAPI-2633: This RDF parser does not support parsing non-resource values", e.getMessage());
	}

	private static Stream<String> getInputFiles() throws IOException {
		ClassLoader cl = RDFParserTest.class.getClassLoader();
		List<String> resourceList = new ArrayList<>();
		ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(cl);
		Resource[] resources = resolver.getResources("classpath:rdf-test-input/*.json") ;
		for (Resource resource: resources)
			resourceList.add(resource.getFile().getPath());

		return resourceList.stream();
	}

	// JSON functions
	public IBaseResource parseJson(InputStream inputStream) {
		IParser refParser = ourCtx.newJsonParser();
		refParser.setStripVersionsFromReferences(false);
		// parser.setDontStripVersionsFromReferencesAtPaths();
		IBaseResource ret = refParser.parseResource(inputStream);
		assertNotNull(ret);
		return ret;
	}

	public String serializeJson(FhirContext ctx, IBaseResource resource) {
		IParser jsonParser = ctx.newJsonParser();
		jsonParser.setStripVersionsFromReferences(false);
		String ret = jsonParser.encodeResourceToString(resource);
		assertNotNull(ret);
		return ret;
	}

	// Rdf (Turtle) functions
	public IBaseResource parseRdf(FhirContext ctx, StringReader inputStream) {
		IParser refParser = ctx.newRDFParser();
		IBaseResource ret = refParser.parseResource(inputStream);
		assertNotNull(ret);
		return ret;
	}

	public String serializeRdf(FhirContext ctx, IBaseResource resource) {
		IParser rdfParser = ourCtx.newRDFParser();
		rdfParser.setStripVersionsFromReferences(false);
		rdfParser.setServerBaseUrl("http://a.example/fhir/");
		String ret = rdfParser.encodeResourceToString(resource);
		assertNotNull(ret);
		return ret;
	}

	public void validateRdf(String rdfContent, String referenceFileName, IBaseResource referenceResource) throws IOException {
		String baseIRI = "http://a.example/shex/";
		Model model = ModelFactory.createDefaultModel();
		RDFDataMgr.read(model, new StringReader(rdfContent), baseIRI, Lang.TURTLE);
		Graph modelAsGraph = model.getGraph();
		FixedShapeMapEntry fixedMapEntry = new FixedShapeMapEntry(model, referenceResource.fhirType());
		ShexReport report = ShexValidator.get().validate(modelAsGraph, fhirShexSchema, fixedMapEntry.getShapeMap());
		boolean result = report.conforms();
		assertThat(result).as(referenceFileName + ": failed to validate " + fixedMapEntry
			+ "\n" + referenceFileName
			+ "\n" + rdfContent).isTrue();
	}

	static class FixedShapeMapEntry {
		private Node focusNode = null;
		private final Node shapeRefNode;
		private final ShapeMap shapeMap;

		FixedShapeMapEntry(Model model, String resourceType) {

			// Identify the main subject (root node) of the RDF model;
			for (org.apache.jena.rdf.model.Resource resource : model.listSubjects().toList()) {
				if (model.contains(resource, model.createProperty(NODE_ROLE_IRI), model.createResource(TREE_ROOT_IRI))) {
					focusNode = resource.asNode();
					break;
				}
			}

			this.shapeRefNode = NodeFactory.createURI(FHIR_SHAPE_PREFIX + resourceType);

			this.shapeMap = ShapeMap.newBuilder().add(this.focusNode, this.shapeRefNode).build();
		}

		public ShapeMap getShapeMap() {
			return shapeMap;
		}

		public String toString() {
			return "<" + focusNode.toString() + ">@" + shapeRefNode.toString();
		}
	}

	@Test
	public void testEncodeBundleWithCrossReferenceFullUrlsAndNoIds() {
		Bundle bundle = createBundleWithCrossReferenceFullUrlsAndNoIds();

		String output = ourCtx.newRDFParser().setPrettyPrint(true).encodeResourceToString(bundle);
		ourLog.info(output);

		assertThat(output).doesNotContain("contained ");
		assertThat(output).doesNotContain("id ");
	}

	@Test
	public void testEncodeBundleWithCrossReferenceFullUrlsAndNoIds_NestedInParameters() {
		Parameters parameters = createBundleWithCrossReferenceFullUrlsAndNoIds_NestedInParameters();

		String output = ourCtx.newRDFParser().setPrettyPrint(true).encodeResourceToString(parameters);
		ourLog.info(output);

		assertThat(output).doesNotContain("contained ");
		assertThat(output).doesNotContain("id ");
	}

}

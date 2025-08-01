package org.hl7.fhir.common.hapi.validation.support;

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.util.FhirTerser;
import ca.uhn.fhir.util.Logs;
import ca.uhn.fhir.util.StopWatch;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;

import java.io.IOException;

// This is instantiated through reflection from DefaultProfileValidationSupport
@SuppressWarnings("unused")
public class DefaultProfileValidationSupportNpmStrategy extends NpmPackageValidationSupport {
	private static final Logger ourLog = Logs.getTerminologyTroubleshootingLog();
	private final FhirTerser myTerser;
	private boolean mySkipSearchParameters;

	/**
	 * Constructor
	 */
	public DefaultProfileValidationSupportNpmStrategy(@Nonnull FhirContext theFhirContext) {
		super(theFhirContext);

		myTerser = theFhirContext.newTerser();

		Validate.isTrue(theFhirContext.getVersion().getVersion() == FhirVersionEnum.R5);

		ourLog.info("Loading R5 Core+Extension packages into memory");
		StopWatch sw = new StopWatch();

		try {
			mySkipSearchParameters = false;
			loadPackageFromClasspath("org/hl7/fhir/r5/packages/hl7.fhir.r5.core-5.0.0.tgz");

			// Don't load extended search parameters (these should be loaded manually if wanted)
			mySkipSearchParameters = true;
			loadPackageFromClasspath("org/hl7/fhir/r5/packages/hl7.fhir.uv.extensions.r5-1.0.0.tgz");
			loadPackageFromClasspath("org/hl7/fhir/r5/packages/hl7.terminology-5.1.0.tgz");
		} catch (IOException e) {
			throw new ConfigurationException(
					Msg.code(2333)
							+ "Failed to load required validation resources. Make sure that the appropriate hapi-fhir-validation-resources-VER JAR is on the classpath",
					e);
		}

		ourLog.info("Loaded {} Core+Extension resources in {}", countAll(), sw);
	}

	@Override
	public String getName() {
		return getFhirContext().getVersion().getVersion() + " FHIR Standard Profile NPM Validation Support";
	}

	@Override
	public void addSearchParameter(IBaseResource theSearchParameter) {
		if (mySkipSearchParameters) {
			return;
		}

		String code = myTerser.getSinglePrimitiveValueOrNull(theSearchParameter, "code");

		// Not yet supported
		if (Constants.PARAM_IN.equals(code)) {
			return;
		}

		super.addSearchParameter(theSearchParameter);
	}
}

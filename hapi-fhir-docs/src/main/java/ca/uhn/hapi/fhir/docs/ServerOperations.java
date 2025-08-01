/*-
 * #%L
 * HAPI FHIR - Docs
 * %%
 * Copyright (C) 2014 - 2025 Smile CDR, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package ca.uhn.hapi.fhir.docs;

import ca.uhn.fhir.model.primitive.DateDt;
import ca.uhn.fhir.model.primitive.StringDt;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenAndListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.svcs.ISearchLimiterSvc;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.hl7.fhir.dstu3.model.Parameters;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.IdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class ServerOperations {
	private static final Logger ourLog = LoggerFactory.getLogger(ServerOperations.class);

	// START SNIPPET: manualInputAndOutput
	@Operation(name = "$manualInputAndOutput", manualResponse = true, manualRequest = true)
	public void manualInputAndOutput(
			HttpServletRequest theServletRequest, RequestDetails theRequest, HttpServletResponse theServletResponse)
			throws IOException {
		String contentType = theServletRequest.getContentType();
		// Warning - don't use the theServletRequest.getInputStream().  It may have been consumed by the FHIR server.
		// The RequestDetails keeps a copy of the input stream for you to use.
		byte[] bytes = IOUtils.toByteArray(theRequest.getInputStream());

		ourLog.info("Received call with content type {} and {} bytes", contentType, bytes.length);

		theServletResponse.setContentType("text/plain");
		theServletResponse.getWriter().write("hello");
		theServletResponse.getWriter().close();
	}
	// END SNIPPET: manualInputAndOutput

	// START SNIPPET: searchParamBasic
	@Operation(name = "$find-matches", idempotent = true)
	public Parameters findMatchesBasic(
			@OperationParam(name = "date") DateParam theDate, @OperationParam(name = "code") TokenParam theCode) {

		Parameters retVal = new Parameters();
		// Populate bundle with matching resources
		return retVal;
	}
	// END SNIPPET: searchParamBasic

	// START SNIPPET: searchParamAdvanced
	@Operation(name = "$find-matches", idempotent = true)
	public Parameters findMatchesAdvanced(
			@OperationParam(name = "dateRange") DateRangeParam theDate,
			@OperationParam(name = "name") List<StringParam> theName,
			@OperationParam(name = "code") TokenAndListParam theEnd) {

		Parameters retVal = new Parameters();
		// Populate bundle with matching resources
		return retVal;
	}
	// END SNIPPET: searchParamAdvanced

	// START SNIPPET: patientTypeOperation
	@Operation(name = "$everything", idempotent = true)
	public Bundle patientTypeOperation(
			@OperationParam(name = "start") DateDt theStart, @OperationParam(name = "end") DateDt theEnd) {

		Bundle retVal = new Bundle();
		// Populate bundle with matching resources
		return retVal;
	}
	// END SNIPPET: patientTypeOperation

	// START SNIPPET: patientInstanceOperation
	@Operation(name = "$everything", idempotent = true)
	public Bundle patientInstanceOperation(
			@IdParam IdType thePatientId,
			@OperationParam(name = "start") DateDt theStart,
			@OperationParam(name = "end") DateDt theEnd) {

		Bundle retVal = new Bundle();
		// Populate bundle with matching resources
		return retVal;
	}
	// END SNIPPET: patientInstanceOperation

	// START SNIPPET: serverOperation
	@Operation(name = "$closure")
	public ConceptMap closureOperation(
			@OperationParam(name = "name") StringDt theStart,
			@OperationParam(name = "concept") List<Coding> theEnd,
			@OperationParam(name = "version") IdType theVersion) {

		ConceptMap retVal = new ConceptMap();
		// Populate bundle with matching resources
		return retVal;
	}
	// END SNIPPET: serverOperation

	private ISearchLimiterSvc mySearchLimiterSvc;

	// START SNIPPET: resourceTypeFiltering
	private void filterPatientEverythingOperation() {
		// filter out Group and List
		mySearchLimiterSvc.addOmittedResourceType("$everything", "Group");
		mySearchLimiterSvc.addOmittedResourceType("$everything", "List");
	}
	// END SNIPPET: resourceTypeFiltering
}

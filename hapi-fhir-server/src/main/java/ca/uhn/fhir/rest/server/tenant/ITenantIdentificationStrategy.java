/*-
 * #%L
 * HAPI FHIR - Server Framework
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
package ca.uhn.fhir.rest.server.tenant;

import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.util.UrlPathTokenizer;

public interface ITenantIdentificationStrategy {

	/**
	 * Implementations should use this method to determine the tenant ID
	 * based on the incoming request and populate it in the
	 * {@link RequestDetails#setTenantId(String)}.
	 *
	 * @param theUrlPathTokenizer The tokenizer which is used to parse the request path
	 * @param theRequestDetails   The request details object which can be used to access headers and to populate the tenant ID to
	 */
	void extractTenant(UrlPathTokenizer theUrlPathTokenizer, RequestDetails theRequestDetails);

	/**
	 * Implementations may use this method to tweak the server base URL
	 * if necessary based on the tenant ID
	 */
	String massageServerBaseUrl(String theFhirServerBase, RequestDetails theRequestDetails);

	/**
	 * Implementations may use this method to resolve relative URL based on the tenant ID from RequestDetails.
	 *
	 * @param theRelativeUrl    URL that only includes the path, e.g. "Patient/123"
	 * @param theRequestDetails The request details object which can be used to access tenant ID
	 * @return Resolved relative URL that starts with tenant ID (if tenant ID present in RequestDetails). Example: "TENANT-A/Patient/123".
	 */
	String resolveRelativeUrl(String theRelativeUrl, RequestDetails theRequestDetails);
}

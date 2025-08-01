/*-
 * #%L
 * HAPI FHIR JPA - Search Parameters
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
package ca.uhn.fhir.jpa.searchparam;

import ca.uhn.fhir.model.api.IQueryParameterAnd;
import ca.uhn.fhir.model.api.IQueryParameterType;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.param.HasAndListParam;
import ca.uhn.fhir.rest.param.HasParam;
import ca.uhn.fhir.rest.param.StringAndListParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenAndListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.param.UriAndListParam;
import ca.uhn.fhir.rest.param.UriParam;
import org.hl7.fhir.instance.model.api.IAnyResource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static ca.uhn.fhir.rest.api.Constants.PARAM_LASTUPDATED;
import static ca.uhn.fhir.rest.api.Constants.PARAM_PROFILE;
import static ca.uhn.fhir.rest.api.Constants.PARAM_SECURITY;
import static ca.uhn.fhir.rest.api.Constants.PARAM_TAG;

public class ResourceMetaParams {
	/**
	 * These are parameters which are supported by searches
	 */
	public static final Map<String, Class<? extends IQueryParameterAnd<?>>> RESOURCE_META_AND_PARAMS;
	/**
	 * These are parameters which are supported by searches
	 */
	public static final Map<String, Class<? extends IQueryParameterType>> RESOURCE_META_PARAMS;

	public static final Set<String> STRICT_RESOURCE_META_PARAMS;

	static {
		Map<String, Class<? extends IQueryParameterType>> resourceMetaParams = new HashMap<>();
		Map<String, Class<? extends IQueryParameterAnd<?>>> resourceMetaAndParams = new HashMap<>();
		resourceMetaParams.put(IAnyResource.SP_RES_ID, StringParam.class);
		resourceMetaAndParams.put(IAnyResource.SP_RES_ID, StringAndListParam.class);
		resourceMetaParams.put(Constants.PARAM_PID, TokenParam.class);
		resourceMetaAndParams.put(Constants.PARAM_PID, TokenAndListParam.class);
		resourceMetaParams.put(PARAM_TAG, TokenParam.class);
		resourceMetaAndParams.put(PARAM_TAG, TokenAndListParam.class);
		resourceMetaParams.put(PARAM_PROFILE, UriParam.class);
		resourceMetaAndParams.put(PARAM_PROFILE, UriAndListParam.class);
		resourceMetaParams.put(Constants.PARAM_SECURITY, TokenParam.class);
		resourceMetaAndParams.put(Constants.PARAM_SECURITY, TokenAndListParam.class);
		resourceMetaParams.put(Constants.PARAM_HAS, HasParam.class);
		resourceMetaAndParams.put(Constants.PARAM_HAS, HasAndListParam.class);
		RESOURCE_META_PARAMS = Collections.unmodifiableMap(resourceMetaParams);
		RESOURCE_META_AND_PARAMS = Collections.unmodifiableMap(resourceMetaAndParams);

		// As described on the FHIR spec: https://hl7.org/fhir/R4/search.html#table
		STRICT_RESOURCE_META_PARAMS = Set.of(PARAM_TAG, PARAM_PROFILE, PARAM_SECURITY, PARAM_LASTUPDATED);
	}
}

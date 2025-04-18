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
package ca.uhn.fhir.rest.server.util;

import ca.uhn.fhir.context.ComboSearchParamType;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import ca.uhn.fhir.context.RuntimeSearchParam;
import ca.uhn.fhir.context.phonetic.IPhoneticEncoder;
import ca.uhn.fhir.i18n.Msg;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.instance.model.api.IIdType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static ca.uhn.fhir.rest.server.util.ISearchParamRegistry.isAllowedForContext;

public class FhirContextSearchParamRegistry implements ISearchParamRegistry {

	private final List<RuntimeSearchParam> myExtraSearchParams = new ArrayList<>();
	private final FhirContext myCtx;

	/**
	 * Constructor
	 */
	public FhirContextSearchParamRegistry(@Nonnull FhirContext theCtx) {
		Validate.notNull(theCtx, "theCtx must not be null");
		myCtx = theCtx;
	}

	@Override
	public void forceRefresh() {
		// nothing
	}

	@Override
	public RuntimeSearchParam getActiveSearchParam(
			@Nonnull String theResourceName,
			@Nonnull String theParamName,
			@Nonnull SearchParamLookupContextEnum theContext) {
		return getActiveSearchParams(theResourceName, theContext).get(theParamName);
	}

	@Override
	public ResourceSearchParams getActiveSearchParams(
			@Nonnull String theResourceName, @Nonnull SearchParamLookupContextEnum theContext) {
		ResourceSearchParams retval = new ResourceSearchParams(theResourceName);
		RuntimeResourceDefinition nextResDef = myCtx.getResourceDefinition(theResourceName);
		for (RuntimeSearchParam nextSp : nextResDef.getSearchParams()) {
			if (isAllowedForContext(nextSp, theContext)) {
				retval.put(nextSp.getName(), nextSp);
			}
		}

		for (RuntimeSearchParam next : myExtraSearchParams) {
			if (isAllowedForContext(next, theContext)) {
				retval.put(next.getName(), next);
			}
		}

		return retval;
	}

	public void addSearchParam(RuntimeSearchParam theSearchParam) {
		myExtraSearchParams.add(theSearchParam);
	}

	@Override
	public List<RuntimeSearchParam> getActiveComboSearchParams(
			@Nonnull String theResourceName,
			@Nonnull Set<String> theParamNames,
			@Nonnull SearchParamLookupContextEnum theContext) {
		throw new UnsupportedOperationException(Msg.code(2066));
	}

	@Nullable
	@Override
	public RuntimeSearchParam getActiveSearchParamByUrl(
			@Nonnull String theUrl, @Nonnull SearchParamLookupContextEnum theContext) {
		// simple implementation for test support
		return myCtx.getResourceTypes().stream()
				.flatMap(type -> getActiveSearchParams(type, theContext).values().stream())
				.filter(rsp -> theUrl.equals(rsp.getUri()))
				.findFirst()
				.orElse(null);
	}

	@Override
	public List<RuntimeSearchParam> getActiveComboSearchParams(
			@Nonnull String theResourceName, @Nonnull SearchParamLookupContextEnum theContext) {
		throw new UnsupportedOperationException(Msg.code(2068));
	}

	@Override
	public List<RuntimeSearchParam> getActiveComboSearchParams(
			@Nonnull String theResourceName,
			@Nonnull ComboSearchParamType theParamType,
			@Nonnull SearchParamLookupContextEnum theContext) {
		throw new UnsupportedOperationException(Msg.code(2209));
	}

	@Override
	public Optional<RuntimeSearchParam> getActiveComboSearchParamById(
			@Nonnull String theResourceName, @Nonnull IIdType theId) {
		throw new UnsupportedOperationException(Msg.code(2211));
	}

	@Override
	public void setPhoneticEncoder(IPhoneticEncoder thePhoneticEncoder) {
		// nothing
	}
}

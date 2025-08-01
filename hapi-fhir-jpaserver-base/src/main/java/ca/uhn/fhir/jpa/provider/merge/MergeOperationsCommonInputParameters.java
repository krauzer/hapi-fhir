/*-
 * #%L
 * HAPI FHIR JPA Server
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
package ca.uhn.fhir.jpa.provider.merge;

import ca.uhn.fhir.util.CanonicalIdentifier;
import org.hl7.fhir.instance.model.api.IBaseReference;

import java.util.List;

/**
 *  Class for input parameters used in both $merge and $hapi.fhir.undo-merge operations.
 */
public class MergeOperationsCommonInputParameters {
	private List<CanonicalIdentifier> mySourceResourceIdentifiers;
	private List<CanonicalIdentifier> myTargetResourceIdentifiers;
	private IBaseReference mySourceResource;
	private IBaseReference myTargetResource;
	private final int myResourceLimit;

	public MergeOperationsCommonInputParameters(int theResourceLimit) {
		myResourceLimit = theResourceLimit;
	}

	public List<CanonicalIdentifier> getSourceIdentifiers() {
		return mySourceResourceIdentifiers;
	}

	public boolean hasAtLeastOneSourceIdentifier() {
		return mySourceResourceIdentifiers != null && !mySourceResourceIdentifiers.isEmpty();
	}

	public void setSourceResourceIdentifiers(List<CanonicalIdentifier> theSourceIdentifiers) {
		this.mySourceResourceIdentifiers = theSourceIdentifiers;
	}

	public List<CanonicalIdentifier> getTargetIdentifiers() {
		return myTargetResourceIdentifiers;
	}

	public boolean hasAtLeastOneTargetIdentifier() {
		return myTargetResourceIdentifiers != null && !myTargetResourceIdentifiers.isEmpty();
	}

	public void setTargetResourceIdentifiers(List<CanonicalIdentifier> theTargetIdentifiers) {
		this.myTargetResourceIdentifiers = theTargetIdentifiers;
	}

	public IBaseReference getSourceResource() {
		return mySourceResource;
	}

	public void setSourceResource(IBaseReference theSourceResource) {
		this.mySourceResource = theSourceResource;
	}

	public IBaseReference getTargetResource() {
		return myTargetResource;
	}

	public void setTargetResource(IBaseReference theTargetResource) {
		this.myTargetResource = theTargetResource;
	}

	public int getResourceLimit() {
		return myResourceLimit;
	}
}

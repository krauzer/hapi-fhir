/*-
 * #%L
 * HAPI FHIR JPA Server - Batch2 Task Processor
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
package ca.uhn.fhir.batch2.jobs.parameters;

import ca.uhn.fhir.batch2.jobs.chunk.FhirIdJson;
import ca.uhn.fhir.model.api.BaseBatchJobParameters;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hl7.fhir.instance.model.api.IIdType;

public class BatchJobParametersWithTaskId extends BaseBatchJobParameters {
	@JsonProperty("taskId")
	private FhirIdJson myTaskId;

	public void setTaskId(IIdType theTaskId) {
		myTaskId = new FhirIdJson(theTaskId);
	}

	public FhirIdJson getTaskId() {
		return myTaskId;
	}
}

/*-
 * #%L
 * HAPI-FHIR Storage Batch2 Jobs
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
package ca.uhn.fhir.batch2.jobs.imprt;

import ca.uhn.fhir.batch2.api.IJobDataSink;
import ca.uhn.fhir.batch2.api.ILastJobStepWorker;
import ca.uhn.fhir.batch2.api.JobExecutionFailedException;
import ca.uhn.fhir.batch2.api.RunOutcome;
import ca.uhn.fhir.batch2.api.StepExecutionDetails;
import ca.uhn.fhir.batch2.api.VoidModel;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.api.dao.IFhirSystemDao;
import ca.uhn.fhir.jpa.api.svc.IIdHelperService;
import ca.uhn.fhir.jpa.api.svc.ResolveIdentityMode;
import ca.uhn.fhir.jpa.dao.tx.HapiTransactionService;
import ca.uhn.fhir.jpa.model.cross.IResourceLookup;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.api.server.storage.IResourcePersistentId;
import ca.uhn.fhir.rest.api.server.storage.TransactionDetails;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import jakarta.annotation.Nonnull;
import org.apache.commons.io.LineIterator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class ConsumeFilesStepV1 implements ILastJobStepWorker<BulkImportJobParameters, NdJsonFileJson> {

	private static final Logger ourLog = LoggerFactory.getLogger(ConsumeFilesStepV1.class);

	@Autowired
	private FhirContext myCtx;

	@Autowired
	private DaoRegistry myDaoRegistry;

	@Autowired
	private HapiTransactionService myHapiTransactionService;

	@Autowired
	private IIdHelperService<?> myIdHelperService;

	@Autowired
	private IFhirSystemDao<?, ?> mySystemDao;

	@Nonnull
	@Override
	public RunOutcome run(
			@Nonnull StepExecutionDetails<BulkImportJobParameters, NdJsonFileJson> theStepExecutionDetails,
			@Nonnull IJobDataSink<VoidModel> theDataSink) {

		String ndjson = theStepExecutionDetails.getData().getNdJsonText();
		String sourceName = theStepExecutionDetails.getData().getSourceName();

		IParser jsonParser = myCtx.newJsonParser();
		LineIterator lineIter = new LineIterator(new StringReader(ndjson));
		List<IBaseResource> resources = new ArrayList<>();
		while (lineIter.hasNext()) {
			String next = lineIter.next();
			if (isNotBlank(next)) {
				IBaseResource parsed;
				try {
					parsed = jsonParser.parseResource(next);
				} catch (DataFormatException e) {
					throw new JobExecutionFailedException(Msg.code(2052) + "Failed to parse resource: " + e, e);
				}
				resources.add(parsed);
			}
		}

		ourLog.info("Bulk loading {} resources from source {}", resources.size(), sourceName);

		storeResources(resources, theStepExecutionDetails.getParameters().getPartitionId());

		return new RunOutcome(resources.size());
	}

	public void storeResources(List<IBaseResource> resources, RequestPartitionId thePartitionId) {
		SystemRequestDetails requestDetails = new SystemRequestDetails();
		if (thePartitionId == null) {
			requestDetails.setRequestPartitionId(RequestPartitionId.defaultPartition());
		} else {
			requestDetails.setRequestPartitionId(thePartitionId);
		}
		TransactionDetails transactionDetails = new TransactionDetails();
		myHapiTransactionService.execute(
				requestDetails,
				transactionDetails,
				tx -> storeResourcesInsideTransaction(resources, requestDetails, transactionDetails));
	}

	private Void storeResourcesInsideTransaction(
			List<IBaseResource> theResources,
			SystemRequestDetails theRequestDetails,
			TransactionDetails theTransactionDetails) {
		Map<IIdType, IBaseResource> ids = new HashMap<>();
		for (IBaseResource next : theResources) {
			if (!next.getIdElement().hasIdPart()) {
				continue;
			}

			IIdType id = next.getIdElement();
			if (!id.hasResourceType()) {
				id.setParts(null, myCtx.getResourceType(next), id.getIdPart(), id.getVersionIdPart());
			}
			ids.put(id, next);
		}

		for (IIdType next : ids.keySet()) {
			theTransactionDetails.addResolvedResourceId(next, null);
		}

		List<IIdType> idsList = new ArrayList<>(ids.keySet());
		Map<IIdType, ? extends IResourceLookup<?>> resolvedIdentities = myIdHelperService.resolveResourceIdentities(
				theRequestDetails.getRequestPartitionId(),
				idsList,
				ResolveIdentityMode.includeDeleted().cacheOk());
		List<IResourcePersistentId<?>> resolvedIds = new ArrayList<>(resolvedIdentities.size());
		for (Map.Entry<IIdType, ? extends IResourceLookup<?>> next : resolvedIdentities.entrySet()) {
			IIdType resId = next.getKey();
			IResourcePersistentId<?> persistentId = next.getValue().getPersistentId();
			resolvedIds.add(persistentId);
			theTransactionDetails.addResolvedResourceId(resId, persistentId);
			ids.remove(resId);
		}

		mySystemDao.preFetchResources(resolvedIds, true);

		for (IBaseResource next : theResources) {
			updateResource(theRequestDetails, theTransactionDetails, next);
		}

		return null;
	}

	private <T extends IBaseResource> void updateResource(
			RequestDetails theRequestDetails, TransactionDetails theTransactionDetails, T theResource) {
		IFhirResourceDao<T> dao = myDaoRegistry.getResourceDao(theResource);
		try {
			dao.update(theResource, null, true, false, theRequestDetails, theTransactionDetails);
		} catch (InvalidRequestException | PreconditionFailedException e) {
			String msg = "Failure during bulk import: " + e;
			ourLog.error(msg);
			throw new JobExecutionFailedException(Msg.code(2053) + msg, e);
		}
	}
}

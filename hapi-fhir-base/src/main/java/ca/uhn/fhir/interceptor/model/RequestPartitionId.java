/*-
 * #%L
 * HAPI FHIR - Core Library
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
package ca.uhn.fhir.interceptor.model;

import ca.uhn.fhir.model.api.IModelJson;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.util.JsonUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

/**
 * @since 5.0.0
 */
public class RequestPartitionId implements IModelJson {
	private static final RequestPartitionId ALL_PARTITIONS = new RequestPartitionId();
	private static final ObjectMapper ourObjectMapper =
			new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

	@JsonProperty("partitionDate")
	private final LocalDate myPartitionDate;

	@JsonProperty("allPartitions")
	private final boolean myAllPartitions;

	@JsonProperty("partitionIds")
	private final List<Integer> myPartitionIds;

	@JsonProperty("partitionNames")
	private final List<String> myPartitionNames;

	/**
	 * Constructor for a single partition
	 */
	private RequestPartitionId(
			@Nullable String thePartitionName, @Nullable Integer thePartitionId, @Nullable LocalDate thePartitionDate) {
		myPartitionIds = toListOrNull(thePartitionId);
		myPartitionNames = toListOrNull(thePartitionName);
		myPartitionDate = thePartitionDate;
		myAllPartitions = false;
	}

	/**
	 * Constructor for a multiple partition
	 */
	private RequestPartitionId(
			@Nullable List<String> thePartitionName,
			@Nullable List<Integer> thePartitionId,
			@Nullable LocalDate thePartitionDate) {
		myPartitionIds = toListOrNull(thePartitionId);
		myPartitionNames = toListOrNull(thePartitionName);
		myPartitionDate = thePartitionDate;
		myAllPartitions = false;
	}

	/**
	 * Constructor for all partitions
	 */
	private RequestPartitionId() {
		super();
		myPartitionDate = null;
		myPartitionNames = null;
		myPartitionIds = null;
		myAllPartitions = true;
	}

	@Nonnull
	public static Optional<RequestPartitionId> getPartitionIfAssigned(IBaseResource theFromResource) {
		return Optional.ofNullable((RequestPartitionId) theFromResource.getUserData(Constants.RESOURCE_PARTITION_ID));
	}

	/**
	 * Creates a new RequestPartitionId which includes all partition IDs from
	 * this {@link RequestPartitionId} but also includes all IDs from the given
	 * {@link RequestPartitionId}. Any duplicates are only included once, and
	 * partition names and dates are ignored and not returned. This {@link RequestPartitionId}
	 * and {@literal theOther} are not modified.
	 *
	 * @since 7.4.0
	 */
	public RequestPartitionId mergeIds(RequestPartitionId theOther) {
		if (isAllPartitions() || theOther.isAllPartitions()) {
			return RequestPartitionId.allPartitions();
		}

		// don't know why this is required - otherwise PartitionedStrictTransactionR4Test fails
		if (this.equals(theOther)) {
			return this;
		}

		List<Integer> thisPartitionIds = getPartitionIds();
		List<Integer> otherPartitionIds = theOther.getPartitionIds();
		List<Integer> newPartitionIds = Stream.concat(thisPartitionIds.stream(), otherPartitionIds.stream())
				.distinct()
				.collect(Collectors.toList());
		return RequestPartitionId.fromPartitionIds(newPartitionIds);
	}

	public static RequestPartitionId fromJson(String theJson) throws JsonProcessingException {
		return ourObjectMapper.readValue(theJson, RequestPartitionId.class);
	}

	public boolean isAllPartitions() {
		return myAllPartitions;
	}

	public boolean isPartitionCovered(Integer thePartitionId) {
		return isAllPartitions() || getPartitionIds().contains(thePartitionId);
	}

	@Nullable
	public LocalDate getPartitionDate() {
		return myPartitionDate;
	}

	@Nullable
	public List<String> getPartitionNames() {
		return myPartitionNames;
	}

	@Nonnull
	public List<Integer> getPartitionIds() {
		Validate.notNull(myPartitionIds, "Partition IDs have not been set");
		return myPartitionIds;
	}

	@Override
	public String toString() {
		ToStringBuilder b = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
		if (hasPartitionIds()) {
			b.append("ids", getPartitionIds());
		}
		if (hasPartitionNames()) {
			b.append("names", getPartitionNames());
		}
		if (myAllPartitions) {
			b.append("allPartitions", myAllPartitions);
		}
		return b.build();
	}

	/**
	 * Returns true if this partition definition contains the other.
	 * Compatible with equals: {@code a.contains(b) && b.contains(a) ==> a.equals(b)}.
	 * We can't implement Comparable because this is only a partial order.
	 */
	public boolean contains(RequestPartitionId theOther) {
		if (this.isAllPartitions()) {
			return true;
		} else if (theOther.isAllPartitions()) {
			return false;
		}
		return this.myPartitionIds.containsAll(theOther.myPartitionIds);
	}

	@Override
	public boolean equals(Object theO) {
		if (this == theO) {
			return true;
		}

		if (theO == null || getClass() != theO.getClass()) {
			return false;
		}

		RequestPartitionId that = (RequestPartitionId) theO;

		EqualsBuilder b = new EqualsBuilder();
		b.append(myAllPartitions, that.myAllPartitions);
		b.append(myPartitionDate, that.myPartitionDate);
		b.append(myPartitionIds, that.myPartitionIds);
		b.append(myPartitionNames, that.myPartitionNames);
		return b.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37)
				.append(myPartitionDate)
				.append(myAllPartitions)
				.append(myPartitionIds)
				.append(myPartitionNames)
				.toHashCode();
	}

	public String toJson() {
		return JsonUtil.serializeOrInvalidRequest(this);
	}

	@Nullable
	public Integer getFirstPartitionIdOrNull() {
		if (myPartitionIds != null) {
			return myPartitionIds.get(0);
		}
		return null;
	}

	public String getFirstPartitionNameOrNull() {
		if (myPartitionNames != null) {
			return myPartitionNames.get(0);
		}
		return null;
	}

	/**
	 * Returns true if this request partition contains only one partition ID and it is the DEFAULT partition ID (null)
	 *
	 * @deprecated use {@link #isPartition(Integer)} or {@link IDefaultPartitionSettings#isDefaultPartition(RequestPartitionId)}
	 * instead
	 * .
	 */
	@Deprecated(since = "2025.02.R01")
	public boolean isDefaultPartition() {
		return isPartition(null);
	}

	/**
	 * Test whether this request partition is for the given partition ID.
	 *
	 * @param thePartitionId is the partition id to be tested against
	 * @return <code>true</code> if the request partition contains exactly one partition ID and the partition ID is
	 *         <code>thePartitionId</code>.
	 */
	public boolean isPartition(@Nullable Integer thePartitionId) {
		if (isAllPartitions()) {
			return false;
		}
		return hasPartitionIds()
				&& getPartitionIds().size() == 1
				&& Objects.equals(getPartitionIds().get(0), thePartitionId);
	}

	public boolean hasPartitionId(Integer thePartitionId) {
		Validate.notNull(myPartitionIds, "Partition IDs not set");
		return myPartitionIds.contains(thePartitionId);
	}

	public boolean hasPartitionIds() {
		return myPartitionIds != null;
	}

	public boolean hasPartitionNames() {
		return myPartitionNames != null;
	}

	/**
	 * Verifies that one of the requested partition is the default partition which is assumed to have a default value of
	 * null.
	 *
	 * @return true if one of the requested partition is the default partition(null).
	 *
	 * @deprecated use {@link #hasDefaultPartitionId(Integer)} or {@link IDefaultPartitionSettings#hasDefaultPartitionId}
	 * instead
	 */
	@Deprecated(since = "2025.02.R01")
	public boolean hasDefaultPartitionId() {
		return hasDefaultPartitionId(null);
	}

	/**
	 * Test whether this request partition has the default partition as one of its targeted partitions.
	 *
	 * This method can be directly invoked on a requestPartition object providing that <code>theDefaultPartitionId</code>
	 * is known or through {@link IDefaultPartitionSettings#hasDefaultPartitionId} where the implementer of the interface
	 * will provide the default partition id (see {@link IDefaultPartitionSettings#getDefaultPartitionId}).
	 *
	 * @param theDefaultPartitionId is the ID that was given to the default partition.  The default partition ID can be
	 *                              NULL as per default or specifically assigned another value.
	 *                              See PartitionSettings#setDefaultPartitionId.
	 * @return <code>true</code> if the request partition has the default partition as one of the targeted partition.
	 */
	public boolean hasDefaultPartitionId(@Nullable Integer theDefaultPartitionId) {
		return getPartitionIds().contains(theDefaultPartitionId);
	}

	public List<Integer> getPartitionIdsWithoutDefault() {
		return getPartitionIds().stream().filter(Objects::nonNull).collect(Collectors.toList());
	}

	@Nullable
	private static <T> List<T> toListOrNull(@Nullable Collection<T> theList) {
		if (theList != null) {
			if (theList.size() == 1) {
				return Collections.singletonList(theList.iterator().next());
			}
			return Collections.unmodifiableList(new ArrayList<>(theList));
		}
		return null;
	}

	@Nullable
	private static <T> List<T> toListOrNull(@Nullable T theObject) {
		if (theObject != null) {
			return Collections.singletonList(theObject);
		}
		return null;
	}

	@SafeVarargs
	@Nullable
	private static <T> List<T> toListOrNull(@Nullable T... theObject) {
		if (theObject != null) {
			return Arrays.asList(theObject);
		}
		return null;
	}

	@Nonnull
	public static RequestPartitionId allPartitions() {
		return ALL_PARTITIONS;
	}

	/**
	 * @deprecated use {@link RequestPartitionId#defaultPartition(IDefaultPartitionSettings)} instead
	 */
	@Deprecated
	@Nonnull
	//	TODO GGG: This is a now-bad usage and we should remove it. we cannot assume null means default.
	public static RequestPartitionId defaultPartition() {
		return fromPartitionIds(Collections.singletonList(null));
	}

	/**
	 * Creates a RequestPartitionId for the default partition using the provided partition settings.
	 * This method uses the default partition ID from the given settings to create the RequestPartitionId.
	 *
	 * @param theDefaultPartitionSettings the partition settings containing the default partition ID
	 * @return a RequestPartitionId for the default partition
	 */
	@Nonnull
	public static RequestPartitionId defaultPartition(IDefaultPartitionSettings theDefaultPartitionSettings) {
		return fromPartitionId(theDefaultPartitionSettings.getDefaultPartitionId());
	}

	@Deprecated
	@Nonnull
	//	TODO GGG: This is a now-bad usage and we should remove it. we cannot assume null means default.
	public static RequestPartitionId defaultPartition(@Nullable LocalDate thePartitionDate) {
		return fromPartitionIds(Collections.singletonList(null), thePartitionDate);
	}

	@Nonnull
	public static RequestPartitionId fromPartitionId(@Nullable Integer thePartitionId) {
		return fromPartitionIds(Collections.singletonList(thePartitionId));
	}

	@Nonnull
	public static RequestPartitionId fromPartitionId(
			@Nullable Integer thePartitionId, @Nullable LocalDate thePartitionDate) {
		return new RequestPartitionId(null, Collections.singletonList(thePartitionId), thePartitionDate);
	}

	@Nonnull
	public static RequestPartitionId fromPartitionIds(@Nonnull Collection<Integer> thePartitionIds) {
		return fromPartitionIds(thePartitionIds, null);
	}

	@Nonnull
	public static RequestPartitionId fromPartitionIds(
			@Nonnull Collection<Integer> thePartitionIds, @Nullable LocalDate thePartitionDate) {
		return new RequestPartitionId(null, toListOrNull(thePartitionIds), thePartitionDate);
	}

	@Nonnull
	public static RequestPartitionId fromPartitionIds(Integer... thePartitionIds) {
		return new RequestPartitionId(null, toListOrNull(thePartitionIds), null);
	}

	@Nonnull
	public static RequestPartitionId fromPartitionName(@Nullable String thePartitionName) {
		return fromPartitionName(thePartitionName, null);
	}

	@Nonnull
	public static RequestPartitionId fromPartitionName(
			@Nullable String thePartitionName, @Nullable LocalDate thePartitionDate) {
		return new RequestPartitionId(thePartitionName, null, thePartitionDate);
	}

	@Nonnull
	public static RequestPartitionId fromPartitionNames(@Nullable List<String> thePartitionNames) {
		return new RequestPartitionId(toListOrNull(thePartitionNames), null, null);
	}

	@Nonnull
	public static RequestPartitionId fromPartitionNames(String... thePartitionNames) {
		return new RequestPartitionId(toListOrNull(thePartitionNames), null, null);
	}

	@Nonnull
	public static RequestPartitionId fromPartitionIdAndName(
			@Nullable Integer thePartitionId, @Nullable String thePartitionName) {
		return new RequestPartitionId(thePartitionName, thePartitionId, null);
	}

	@Nonnull
	public static RequestPartitionId forPartitionIdAndName(
			@Nullable Integer thePartitionId, @Nullable String thePartitionName, @Nullable LocalDate thePartitionDate) {
		return new RequestPartitionId(thePartitionName, thePartitionId, thePartitionDate);
	}

	@Nonnull
	public static RequestPartitionId forPartitionIdsAndNames(
			List<String> thePartitionNames, List<Integer> thePartitionIds, LocalDate thePartitionDate) {
		return new RequestPartitionId(thePartitionNames, thePartitionIds, thePartitionDate);
	}

	/**
	 * Create a string representation suitable for use as a cache key. Null aware.
	 * <p>
	 * Returns the partition IDs (numeric) as a joined string with a space between, using the string "null" for any null values
	 */
	public static String stringifyForKey(@Nonnull RequestPartitionId theRequestPartitionId) {
		String retVal = "(all)";
		if (!theRequestPartitionId.isAllPartitions()) {
			assert theRequestPartitionId.hasPartitionIds();
			retVal = theRequestPartitionId.getPartitionIds().stream()
					.map(t -> defaultIfNull(t, "null").toString())
					.collect(Collectors.joining(" "));
		}
		return retVal;
	}

	public String asJson() throws JsonProcessingException {
		return ourObjectMapper.writeValueAsString(this);
	}
}

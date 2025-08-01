/*-
 * #%L
 * HAPI FHIR Storage api
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
package ca.uhn.fhir.jpa.dao;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.hl7.fhir.instance.model.api.IIdType;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IdSubstitutionMap {

	private final Map<Entry, Entry> myMap = new HashMap<>();
	private final Multimap<Entry, Entry> myReverseMap =
			MultimapBuilder.hashKeys().arrayListValues().build();

	public boolean containsSource(IIdType theId) {
		if (theId.isLocal()) {
			return false;
		}
		return myMap.containsKey(new Entry(theId));
	}

	public boolean containsSource(String theId) {
		return myMap.containsKey(new Entry(theId));
	}

	public boolean containsTarget(IIdType theId) {
		return myReverseMap.containsKey(new Entry(theId));
	}

	public boolean containsTarget(String theId) {
		return myReverseMap.containsKey(new Entry(theId));
	}

	public IIdType getForSource(IIdType theId) {
		Entry target = myMap.get(new Entry(theId));
		if (target != null) {
			assert target.myId != null;
			return target.myId;
		}
		return null;
	}

	public IIdType getForSource(String theId) {
		Entry target = myMap.get(new Entry(theId));
		if (target != null) {
			assert target.myId != null;
			return target.myId;
		}
		return null;
	}

	public List<Pair<IIdType, IIdType>> entrySet() {
		return myMap.entrySet().stream()
				.map(t -> Pair.of(t.getKey().myId, t.getValue().myId))
				.collect(Collectors.toList());
	}

	public void put(IIdType theSource, IIdType theTarget) {
		Entry sourceEntry = new Entry(theSource);
		Entry targetEntry = new Entry(theTarget);
		myMap.put(sourceEntry, targetEntry);
		myReverseMap.put(targetEntry, sourceEntry);
	}

	public boolean isEmpty() {
		return myMap.isEmpty();
	}

	/**
	 * Updates all targets of the map with a new id value if the input id has
	 * the same ResourceType and IdPart as the target id.
	 */
	public void updateTargets(IIdType theNewId) {
		if (theNewId == null || theNewId.getValue() == null) {
			return;
		}

		Entry newEntry = new Entry(theNewId);
		Collection<Entry> targets = myReverseMap.removeAll(newEntry);
		for (Entry nextTarget : targets) {
			myMap.put(nextTarget, newEntry);
		}
		myReverseMap.putAll(newEntry, targets);
	}

	private static class Entry {

		private final String myUnversionedId;
		private final IIdType myId;

		private Entry(String theId) {
			myId = null;
			myUnversionedId = theId;
		}

		private Entry(IIdType theId) {
			String unversionedId = toVersionlessValue(theId);
			myUnversionedId = unversionedId;
			myId = theId;
		}

		@Override
		public boolean equals(Object theOther) {
			if (theOther instanceof Entry) {
				String otherUnversionedId = ((Entry) theOther).myUnversionedId;
				if (myUnversionedId.equals(otherUnversionedId)) {
					return true;
				}
			}
			return false;
		}

		@Override
		public int hashCode() {
			return myUnversionedId.hashCode();
		}

		@Override
		public String toString() {
			return "Entry[" + "myUnversionedId='" + myUnversionedId + "', myId=" + myId + ']';
		}
	}

	static String toVersionlessValue(IIdType theId) {
		boolean isPlaceholder = theId.getValue().startsWith("urn:");
		String unversionedId;
		if (isPlaceholder || (!theId.hasBaseUrl() && !theId.hasVersionIdPart()) || !theId.hasResourceType()) {
			unversionedId = theId.getValue();
		} else {
			unversionedId = theId.toUnqualifiedVersionless().getValue();
		}
		return unversionedId;
	}
}

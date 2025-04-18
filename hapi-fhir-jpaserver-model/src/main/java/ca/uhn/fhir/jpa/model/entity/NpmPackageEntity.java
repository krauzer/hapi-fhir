/*-
 * #%L
 * HAPI FHIR JPA Model
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
package ca.uhn.fhir.jpa.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Date;
import java.util.List;

@Entity()
@Table(
		name = "NPM_PACKAGE",
		uniqueConstraints = {@UniqueConstraint(name = "IDX_PACK_ID", columnNames = "PACKAGE_ID")})
public class NpmPackageEntity {

	protected static final int PACKAGE_ID_LENGTH = 200;

	@SequenceGenerator(name = "SEQ_NPM_PACK", sequenceName = "SEQ_NPM_PACK")
	@GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_NPM_PACK")
	@Id
	@Column(name = "PID")
	private Long myId;

	@Column(name = "PACKAGE_ID", length = PACKAGE_ID_LENGTH, nullable = false)
	private String myPackageId;

	@Column(name = "CUR_VERSION_ID", length = NpmPackageVersionEntity.VERSION_ID_LENGTH, nullable = true)
	private String myCurrentVersionId;

	@Temporal(TemporalType.TIMESTAMP)
	@Version
	@Column(name = "UPDATED_TIME", nullable = false)
	private Date myVersion;

	@Column(name = "PACKAGE_DESC", length = NpmPackageVersionEntity.PACKAGE_DESC_LENGTH, nullable = true)
	private String myDescription;

	@OneToMany(mappedBy = "myPackage")
	private List<NpmPackageVersionEntity> myVersions;

	public String getDescription() {
		return myDescription;
	}

	public void setDescription(String theDescription) {
		myDescription = theDescription;
	}

	public String getPackageId() {
		return myPackageId;
	}

	public void setPackageId(String thePackageId) {
		myPackageId = thePackageId;
	}

	@Override
	public boolean equals(Object theO) {
		if (this == theO) {
			return true;
		}

		if (theO == null || getClass() != theO.getClass()) {
			return false;
		}

		NpmPackageEntity that = (NpmPackageEntity) theO;

		return new EqualsBuilder().append(myPackageId, that.myPackageId).isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37).append(myPackageId).toHashCode();
	}

	public String getCurrentVersionId() {
		return myCurrentVersionId;
	}

	public void setCurrentVersionId(String theCurrentVersionId) {
		myCurrentVersionId = theCurrentVersionId;
	}
}

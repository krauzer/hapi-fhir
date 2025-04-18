/*-
 * #%L
 * HAPI FHIR Server - SQL Migration
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
package ca.uhn.fhir.jpa.migrate.taskdef;

import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.jpa.migrate.JdbcUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class AddIdGeneratorTask extends BaseTask {

	private static final Integer DEFAULT_INCREMENT = 50;
	private static final Logger ourLog = LoggerFactory.getLogger(AddIdGeneratorTask.class);
	private final String myGeneratorName;
	private final Integer myIncrement;

	public AddIdGeneratorTask(String theProductVersion, String theSchemaVersion, String theGeneratorName) {
		super(theProductVersion, theSchemaVersion);
		myGeneratorName = theGeneratorName;
		myIncrement = DEFAULT_INCREMENT;
	}

	public AddIdGeneratorTask(
			String theProductVersion, String theSchemaVersion, String theGeneratorName, Integer theIncrement) {
		super(theProductVersion, theSchemaVersion);
		myGeneratorName = theGeneratorName;
		myIncrement = theIncrement;
	}

	@Override
	public void validate() {
		Validate.notBlank(myGeneratorName);
		setDescription("Add id generator " + myGeneratorName);
	}

	@Override
	public void doExecute() throws SQLException {
		Set<String> tableNames = JdbcUtils.getTableNames(getConnectionProperties());
		String sql = null;

		switch (getDriverType()) {
			case MARIADB_10_1:
			case MYSQL_5_7:
				// These require a separate table
				// Increment value is controlled globally using the auto_increment_increment variable
				if (!tableNames.contains(myGeneratorName)) {

					String creationSql = "create table " + myGeneratorName + " ( next_val bigint ) engine=InnoDB";
					executeSql(myGeneratorName, creationSql);

					String initSql = "insert into " + myGeneratorName + " values ( 1 )";
					executeSql(myGeneratorName, initSql);
				}
				break;
			case DERBY_EMBEDDED:
			case H2_EMBEDDED:
			case ORACLE_12C:
			case MSSQL_2012:
				sql = "create sequence " + myGeneratorName + " start with 1 increment by " + myIncrement;
				break;
			case COCKROACHDB_21_1:
			case POSTGRES_9_4:
				sql = "create sequence " + myGeneratorName + " start 1 increment " + myIncrement;
				break;
			default:
				throw new IllegalStateException(Msg.code(63));
		}

		if (isNotBlank(sql)) {
			Set<String> sequenceNames = JdbcUtils.getSequenceNames(getConnectionProperties()).stream()
					.map(String::toLowerCase)
					.collect(Collectors.toSet());
			ourLog.debug("Currently have sequences: {}", sequenceNames);
			if (sequenceNames.contains(myGeneratorName.toLowerCase())) {
				logInfo(ourLog, "Sequence {} already exists - No action performed", myGeneratorName);
				return;
			}

			executeSql(myGeneratorName, sql);
		}
	}

	@Override
	protected void generateEquals(EqualsBuilder theBuilder, BaseTask theOtherObject) {
		AddIdGeneratorTask otherObject = (AddIdGeneratorTask) theOtherObject;
		theBuilder.append(myGeneratorName, otherObject.myGeneratorName);
	}

	@Override
	protected void generateHashCode(HashCodeBuilder theBuilder) {
		theBuilder.append(myGeneratorName);
	}
}

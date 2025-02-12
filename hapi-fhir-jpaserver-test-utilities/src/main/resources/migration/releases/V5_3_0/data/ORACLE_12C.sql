INSERT INTO HFJ_RESOURCE (
   RES_ID,
   RES_VERSION,
   HAS_TAGS,
   RES_PUBLISHED,
   RES_UPDATED,
   SP_HAS_LINKS,
   HASH_SHA256,
   SP_INDEX_STATUS,
   SP_CMPSTR_UNIQ_PRESENT,
   SP_COORDS_PRESENT,
   SP_DATE_PRESENT,
   SP_NUMBER_PRESENT,
   SP_QUANTITY_NRML_PRESENT,
   SP_QUANTITY_PRESENT,
   SP_STRING_PRESENT,
   SP_TOKEN_PRESENT,
   SP_URI_PRESENT,
   RES_TYPE,
   RES_VER
) VALUES (
   1702,
   'R4',
   0,
   SYSDATE,
   SYSDATE,
   0,
   '6beed652b77f6c65d776e57341a0b5b0596ac9cfb0e8345a5a5cfbfaa59e2b62',
   1,
   0,
   0,
   0,
   0,
   0,
   0,
   0,
   1,
   1,
   'Observation',
   1
);


INSERT INTO HFJ_SPIDX_QUANTITY_NRML (
   RES_ID,
   RES_TYPE,
   SP_UPDATED,
   SP_MISSING,
   SP_NAME, SP_ID,
   SP_SYSTEM,
   SP_UNITS,
   HASH_IDENTITY_AND_UNITS,
   HASH_IDENTITY_SYS_UNITS,
   HASH_IDENTITY,
   SP_VALUE,
   PARTITION_DATE,
   PARTITION_ID
) VALUES (
   1702,
   'Observation',
   SYSDATE,
   0,
   'value-quantity',
   2,
   'https://unitsofmeasure.org',
   'g',
   -864931808150710347,
   6382255012744790145,
   -1901136387361512731,
   0.012,
   SYSDATE,
   1
);

INSERT INTO HFJ_RES_LINK (
   PID,
   PARTITION_DATE,
   PARTITION_ID,
   SRC_PATH,
   SRC_RESOURCE_ID,
   SOURCE_RESOURCE_TYPE,
   TARGET_RESOURCE_ID,
   TARGET_RESOURCE_TYPE,
   TARGET_RESOURCE_URL,
   TARGET_RESOURCE_VERSION,
   SP_UPDATED
) VALUES (
   701,
   SYSDATE,
   1,
   'Observation.subject.where(resolve() is Patient)',
   1702,
   'Observation',
   1906,
   'Patient',
   'http://localhost:8000/Patient/123',
   1,
   SYSDATE
);

#!/bin/bash
set -euo pipefail
source /opt/data-platform/shared/config/data-platform.env
mysql_query() {
  MYSQL_PWD="${SPRING_DATASOURCE_PASSWORD}" mysql --protocol=TCP -N -B \
    -h "${MYSQL_HOST}" -P "${MYSQL_PORT}" -u "${MYSQL_USER}" "${MYSQL_DATABASE}" "$@"
}
data_source_id="$(mysql_query -e "SELECT id FROM dp_data_source WHERE name='工厂达人' AND deleted=b'0' ORDER BY id LIMIT 1")"
echo CANDIDATE_TABLES
mysql_query -e "SELECT source_table,business_name,field_count FROM dp_metadata_table
 WHERE data_source_id=${data_source_id} AND status=0 AND deleted=b'0'
 AND LOWER(source_table) REGEXP 'field|column|caption|label|dict|meta|config|define|description'
 ORDER BY source_table"
echo CANDIDATE_FIELDS
mysql_query -e "SELECT t.source_table,f.source_column,f.data_type
 FROM dp_metadata_field f JOIN dp_metadata_table t ON t.id=f.metadata_table_id
 WHERE t.data_source_id=${data_source_id} AND t.status=0 AND f.status=0
 AND LOWER(t.source_table) REGEXP 'field|column|caption|label|dict|meta|config|define|description'
 AND LOWER(f.source_column) REGEXP 'table|field|column|caption|label|name|description|memo|remark|title'
 ORDER BY t.source_table,f.ordinal_position LIMIT 300"
echo CORE_TABLES
mysql_query -e "SELECT source_table,business_name,business_domain,field_count,confirmed_field_count
 FROM (SELECT t.*,SUM(CASE WHEN f.definition_status='CONFIRMED' THEN 1 ELSE 0 END) confirmed_field_count
 FROM dp_metadata_table t LEFT JOIN dp_metadata_field f ON f.metadata_table_id=t.id AND f.status=0 AND f.deleted=b'0'
 WHERE t.data_source_id=${data_source_id} AND t.status=0 AND t.deleted=b'0'
 GROUP BY t.id) x
 WHERE source_table IN ('comProduct','comCustomer','comSupplier','comDepartment','comWareHouse','comPerson',
 'ordBillMain','ordBillSub','stkBillMain','stkBillSub','accVoucherMain','accVoucherSub')
 ORDER BY source_table"

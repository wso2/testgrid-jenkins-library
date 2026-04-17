#!/bin/bash
# ------------------------------------------------------------------------
#
# Copyright (c) 2026, WSO2 Inc. (https://www.wso2.com). All Rights Reserved.
#
# This software is the property of WSO2 Inc. and its suppliers, if any.
# Dissemination of any information or reproduction of any material contained
# herein in any form is strictly forbidden, unless permitted by WSO2
# expressly. You may not alter or remove any copyright or other notice from
# copies of this content.
#
# ------------------------------------------------------------------------

echo "Creating DB scripts for WSO2-IS based on DB Engine"

# Define files and databases
DB_ENGINE='CF_DBMS_NAME'
SCRIPT_LOCATION='CF_SCRIPT_LOCATION'
WSO2_PRODUCT_VERSION_SHORT='CF_PRODUCT_VERSION_SHORT'

if [ $DB_ENGINE = "postgres" ]; then
  # Create database creation script
  db_create_file="$WSO2_PRODUCT_VERSION_SHORT/is_postgres_db_create.sql"
  touch "$db_create_file"
  echo "-- PostgreSQL Database Creation Script" > "$db_create_file"
  echo "DROP DATABASE IF EXISTS \"WSO2SHARED_DB\";" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS \"WSO2BPS_DB\";" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS \"WSO2IDENTITY_DB\";" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS \"WSO2CONSENT_DB\";" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS \"WSO2METRICS_DB\";" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS \"WSO2AGENTIDENTITY_DB\";" >> "$db_create_file"
  echo "" >> "$db_create_file"
  echo "CREATE DATABASE \"WSO2SHARED_DB\" LC_COLLATE = 'C' LC_CTYPE = 'C' TEMPLATE = template0;" >> "$db_create_file"
  echo "CREATE DATABASE \"WSO2IS_BPS_DB\" LC_COLLATE = 'C' LC_CTYPE = 'C' TEMPLATE = template0;" >> "$db_create_file"
  echo "CREATE DATABASE \"WSO2IDENTITY_DB\" LC_COLLATE = 'C' LC_CTYPE = 'C' TEMPLATE = template0;" >> "$db_create_file"
  echo "CREATE DATABASE \"WSO2CONSENT_DB\" LC_COLLATE = 'C' LC_CTYPE = 'C' TEMPLATE = template0;" >> "$db_create_file"
  echo "CREATE DATABASE \"WSO2_METRICS_DB\" LC_COLLATE = 'C' LC_CTYPE = 'C' TEMPLATE = template0;" >> "$db_create_file"
  echo "CREATE DATABASE \"WSO2AGENTIDENTITY_DB\" LC_COLLATE = 'C' LC_CTYPE = 'C' TEMPLATE = template0;" >> "$db_create_file"
  
  # Create schema scripts for each database (consent is merged into identity)
  sql_files=("$SCRIPT_LOCATION/postgresql.sql" "$SCRIPT_LOCATION/identity/postgresql.sql" "$SCRIPT_LOCATION/identity/agent/postgresql.sql")
  output_files=("$WSO2_PRODUCT_VERSION_SHORT/is_postgres_shared.sql" "$WSO2_PRODUCT_VERSION_SHORT/is_postgres_identity.sql" "$WSO2_PRODUCT_VERSION_SHORT/is_postgres_agent_identity.sql")

  for i in "${!output_files[@]}"; do
    cat "${sql_files[$i]}" > "${output_files[$i]}"
    echo "" >> "${output_files[$i]}"
  done

  # Append consent schema into the identity SQL file
  cat "$SCRIPT_LOCATION/consent/postgresql.sql" >> "$WSO2_PRODUCT_VERSION_SHORT/is_postgres_identity.sql"
  echo "" >> "$WSO2_PRODUCT_VERSION_SHORT/is_postgres_identity.sql"

elif [ $DB_ENGINE = "mysql" ]; then
  # Create database creation script
  db_create_file="$WSO2_PRODUCT_VERSION_SHORT/is_mysql_db_create.sql"
  touch "$db_create_file"
  echo "-- MySQL Database Creation Script" > "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2SHARED_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2IDENTITY_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2IS_BPS_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2CONSENT_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2_METRICS_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2_CLUSTER_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS IS_ANALYTICS_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2_CARBON_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2_PERSISTENCE_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2_STATUS_DASHBOARD_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2AGENTIDENTITY_DB;" >> "$db_create_file"
  echo "" >> "$db_create_file"
  echo "CREATE DATABASE WSO2SHARED_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2IDENTITY_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2IS_BPS_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2CONSENT_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2_METRICS_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2_CLUSTER_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE IS_ANALYTICS_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2_CARBON_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2_PERSISTENCE_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2_STATUS_DASHBOARD_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2AGENTIDENTITY_DB character set latin1;" >> "$db_create_file"
  
  # Create schema scripts for each database (consent is merged into identity)
  sql_files=("$SCRIPT_LOCATION/mysql.sql" "$SCRIPT_LOCATION/identity/mysql.sql" "$SCRIPT_LOCATION/identity/agent/mysql.sql")
  output_files=("$WSO2_PRODUCT_VERSION_SHORT/is_mysql_shared.sql" "$WSO2_PRODUCT_VERSION_SHORT/is_mysql_identity.sql" "$WSO2_PRODUCT_VERSION_SHORT/is_mysql_agent_identity.sql")

  for i in "${!output_files[@]}"; do
    cat "${sql_files[$i]}" > "${output_files[$i]}"
    echo "" >> "${output_files[$i]}"
  done

  # Append consent schema into the identity SQL file
  cat "$SCRIPT_LOCATION/consent/mysql.sql" >> "$WSO2_PRODUCT_VERSION_SHORT/is_mysql_identity.sql"
  echo "" >> "$WSO2_PRODUCT_VERSION_SHORT/is_mysql_identity.sql"

elif [ $DB_ENGINE = "mariadb" ]; then
  # Create database creation script
  db_create_file="$WSO2_PRODUCT_VERSION_SHORT/is_mariadb_db_create.sql"
  touch "$db_create_file"
  echo "-- MariaDB Database Creation Script" > "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2SHARED_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2IDENTITY_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2IS_BPS_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2CONSENT_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2_METRICS_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2_CLUSTER_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS IS_ANALYTICS_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2_CARBON_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2_PERSISTENCE_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2_STATUS_DASHBOARD_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2AGENTIDENTITY_DB;" >> "$db_create_file"
  echo "" >> "$db_create_file"
  echo "CREATE DATABASE WSO2SHARED_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2IDENTITY_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2IS_BPS_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2CONSENT_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2_METRICS_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2_CLUSTER_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE IS_ANALYTICS_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2_CARBON_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2_PERSISTENCE_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2_STATUS_DASHBOARD_DB character set latin1;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2AGENTIDENTITY_DB character set latin1;" >> "$db_create_file"
  
  # Create schema scripts for each database (consent is merged into identity)
  sql_files=("$SCRIPT_LOCATION/mysql.sql" "$SCRIPT_LOCATION/identity/mysql.sql" "$SCRIPT_LOCATION/identity/agent/mysql.sql")
  output_files=("$WSO2_PRODUCT_VERSION_SHORT/is_mariadb_shared.sql" "$WSO2_PRODUCT_VERSION_SHORT/is_mariadb_identity.sql" "$WSO2_PRODUCT_VERSION_SHORT/is_mariadb_agent_identity.sql")

  for i in "${!output_files[@]}"; do
    cat "${sql_files[$i]}" > "${output_files[$i]}"
    echo "" >> "${output_files[$i]}"
  done

  # Append consent schema into the identity SQL file
  cat "$SCRIPT_LOCATION/consent/mysql.sql" >> "$WSO2_PRODUCT_VERSION_SHORT/is_mariadb_identity.sql"
  echo "" >> "$WSO2_PRODUCT_VERSION_SHORT/is_mariadb_identity.sql"

elif [ $DB_ENGINE = "sqlserver-se" ]; then
  # Create database creation script
  db_create_file="$WSO2_PRODUCT_VERSION_SHORT/is_mssql_db_create.sql"
  touch "$db_create_file"
  echo "-- SQL Server Database Creation Script" > "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2SHARED_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2IS_BPS_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2IDENTITY_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2CONSENT_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2_METRICS_DB;" >> "$db_create_file"
  echo "DROP DATABASE IF EXISTS WSO2AGENTIDENTITY_DB;" >> "$db_create_file"
  echo "GO" >> "$db_create_file"
  echo "" >> "$db_create_file"
  echo "CREATE DATABASE WSO2SHARED_DB;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2IS_BPS_DB;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2IDENTITY_DB;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2CONSENT_DB;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2_METRICS_DB;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2AGENTIDENTITY_DB;" >> "$db_create_file"
  echo "GO" >> "$db_create_file"
  echo "" >> "$db_create_file"
  # Grant the login access to each created database
  for db in WSO2SHARED_DB WSO2IS_BPS_DB WSO2IDENTITY_DB WSO2CONSENT_DB WSO2_METRICS_DB WSO2AGENTIDENTITY_DB; do
    echo "USE $db;" >> "$db_create_file"
    echo "GO" >> "$db_create_file"
    echo "IF NOT EXISTS (SELECT name FROM sys.database_principals WHERE name = 'CF_DB_USERNAME')" >> "$db_create_file"
    echo "BEGIN" >> "$db_create_file"
    echo "    CREATE USER [CF_DB_USERNAME] FOR LOGIN [CF_DB_USERNAME];" >> "$db_create_file"
    echo "END" >> "$db_create_file"
    echo "GO" >> "$db_create_file"
    echo "ALTER ROLE db_owner ADD MEMBER [CF_DB_USERNAME];" >> "$db_create_file"
    echo "GO" >> "$db_create_file"
    echo "" >> "$db_create_file"
  done
  
  # Create schema scripts for each database (consent is merged into identity)
  sql_files=("$SCRIPT_LOCATION/mssql.sql" "$SCRIPT_LOCATION/identity/mssql.sql" "$SCRIPT_LOCATION/identity/agent/mssql.sql")
  output_files=("$WSO2_PRODUCT_VERSION_SHORT/is_mssql_shared.sql" "$WSO2_PRODUCT_VERSION_SHORT/is_mssql_identity.sql" "$WSO2_PRODUCT_VERSION_SHORT/is_mssql_agent_identity.sql")

  for i in "${!output_files[@]}"; do
    # Add USE DATABASE statement for SQL Server (use > to overwrite any existing file)
    if [ "$i" -eq 0 ]; then
      echo "USE WSO2SHARED_DB;" > "${output_files[$i]}"
      echo "GO" >> "${output_files[$i]}"
    elif [ "$i" -eq 1 ]; then
      echo "USE WSO2IDENTITY_DB;" > "${output_files[$i]}"
      echo "GO" >> "${output_files[$i]}"
    elif [ "$i" -eq 2 ]; then
      echo "USE WSO2AGENTIDENTITY_DB;" > "${output_files[$i]}"
      echo "GO" >> "${output_files[$i]}"
    fi
    cat "${sql_files[$i]}" >> "${output_files[$i]}"
    echo "" >> "${output_files[$i]}"
  done

  # Append consent schema into the identity SQL file (no USE change — stays in WSO2IDENTITY_DB context)
  cat "$SCRIPT_LOCATION/consent/mssql.sql" >> "$WSO2_PRODUCT_VERSION_SHORT/is_mssql_identity.sql"
  echo "" >> "$WSO2_PRODUCT_VERSION_SHORT/is_mssql_identity.sql"

elif [[ $DB_ENGINE =~ 'oracle-se' ]]; then
  # Create schema scripts for each database (consent is merged into identity)
  sql_files=("$SCRIPT_LOCATION/oracle.sql" "$SCRIPT_LOCATION/identity/oracle.sql" "$SCRIPT_LOCATION/identity/agent/oracle.sql")
  output_files=("$WSO2_PRODUCT_VERSION_SHORT/is_oracle_common.sql" "$WSO2_PRODUCT_VERSION_SHORT/is_oracle_identity.sql" "$WSO2_PRODUCT_VERSION_SHORT/is_oracle_agent_identity.sql")

  # Loop through files and write content (use > to overwrite any existing file)
  for i in "${!sql_files[@]}"; do
    cat "${sql_files[$i]}" > "${output_files[$i]}"
    echo "" >> "${output_files[$i]}"
  done

  # Append consent schema into the identity SQL file
  cat "$SCRIPT_LOCATION/consent/oracle.sql" >> "$WSO2_PRODUCT_VERSION_SHORT/is_oracle_identity.sql"
  echo "" >> "$WSO2_PRODUCT_VERSION_SHORT/is_oracle_identity.sql"

elif [ $DB_ENGINE = "db2-se" ]; then
  # Create database creation script
  db_create_file="$WSO2_PRODUCT_VERSION_SHORT/is_db2_db_create.sql"
  touch "$db_create_file"
  echo "-- DB2 Database Creation Script" > "$db_create_file"
  echo "BEGIN" >> "$db_create_file"
  echo "    DECLARE CONTINUE HANDLER FOR SQLSTATE '42704' BEGIN END;" >> "$db_create_file"
  echo "    DROP DATABASE WSO2SHARED_DB;" >> "$db_create_file"
  echo "    DROP DATABASE WSO2IS_BPS_DB;" >> "$db_create_file"
  echo "    DROP DATABASE WSO2IDENTITY_DB;" >> "$db_create_file"
  echo "    DROP DATABASE WSO2CONSENT_DB;" >> "$db_create_file"
  echo "    DROP DATABASE WSO2_METRICS_DB;" >> "$db_create_file"
  echo "    DROP DATABASE WSO2AGENTIDENTITY_DB;" >> "$db_create_file"
  echo "END;" >> "$db_create_file"
  echo "" >> "$db_create_file"
  echo "CREATE DATABASE WSO2SHARED_DB;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2IS_BPS_DB;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2IDENTITY_DB;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2CONSENT_DB;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2_METRICS_DB;" >> "$db_create_file"
  echo "CREATE DATABASE WSO2AGENTIDENTITY_DB;" >> "$db_create_file"
  
  # Create schema scripts for each database (consent is merged into identity)
  sql_files=("$SCRIPT_LOCATION/db2.sql" "$SCRIPT_LOCATION/identity/db2.sql" "$SCRIPT_LOCATION/identity/agent/db2.sql")
  output_files=("$WSO2_PRODUCT_VERSION_SHORT/is_db2_shared.sql" "$WSO2_PRODUCT_VERSION_SHORT/is_db2_identity.sql" "$WSO2_PRODUCT_VERSION_SHORT/is_db2_agent_identity.sql")

  for i in "${!output_files[@]}"; do
    cat "${sql_files[$i]}" > "${output_files[$i]}"
    echo "" >> "${output_files[$i]}"
  done

  # Append consent schema into the identity SQL file
  cat "$SCRIPT_LOCATION/consent/db2.sql" >> "$WSO2_PRODUCT_VERSION_SHORT/is_db2_identity.sql"
  echo "" >> "$WSO2_PRODUCT_VERSION_SHORT/is_db2_identity.sql"
fi

echo "SQL files appended successfully"

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

#Running Database scripts for WSO2-IS
echo "Running DB scripts for WSO2-IS..."

#Define parameter values for Database Engine and Version

DB_ENGINE='CF_DBMS_NAME'
DB_ENGINE_VERSION='CF_DBMS_VERSION'
WSO2_PRODUCT_VERSION='CF_PRODUCT_VERSION'
USE_CONSENT_DB=false

#Select product version
if [ $WSO2_PRODUCT_VERSION = "5.2.0" ]; then
    WSO2_PRODUCT_VERSION_SHORT=is520
elif [ $WSO2_PRODUCT_VERSION = "5.3.0" ]; then
    WSO2_PRODUCT_VERSION_SHORT=is530
elif [ $WSO2_PRODUCT_VERSION = "5.4.0" ]; then
    WSO2_PRODUCT_VERSION_SHORT=is540
elif [ $WSO2_PRODUCT_VERSION = "5.4.1" ]; then
    WSO2_PRODUCT_VERSION_SHORT=is541
elif [ $WSO2_PRODUCT_VERSION = "5.5.0" ]; then
    WSO2_PRODUCT_VERSION_SHORT=is550
    USE_CONSENT_DB=true
elif [ $WSO2_PRODUCT_VERSION = "5.6.0" ]; then
    WSO2_PRODUCT_VERSION_SHORT=is560
    USE_CONSENT_DB=true
elif [ $WSO2_PRODUCT_VERSION = "5.7.0" ]; then
    WSO2_PRODUCT_VERSION_SHORT=is570
    USE_CONSENT_DB=true
elif [ $WSO2_PRODUCT_VERSION = "5.8.0" ]; then
    WSO2_PRODUCT_VERSION_SHORT=is580
    USE_CONSENT_DB=true
elif [ $WSO2_PRODUCT_VERSION = "5.9.0" ]; then
    WSO2_PRODUCT_VERSION_SHORT=is590
    USE_CONSENT_DB=true
elif [ $WSO2_PRODUCT_VERSION = "5.10.0" ]; then
    WSO2_PRODUCT_VERSION_SHORT=is5100
    USE_CONSENT_DB=true
elif [ $WSO2_PRODUCT_VERSION = "5.11.0" ]; then
    WSO2_PRODUCT_VERSION_SHORT=is5110
    USE_CONSENT_DB=true
elif [ $WSO2_PRODUCT_VERSION = "6.0.0" ]; then
    WSO2_PRODUCT_VERSION_SHORT=is600
    USE_CONSENT_DB=true
elif [ $WSO2_PRODUCT_VERSION = "6.1.0" ]; then
    WSO2_PRODUCT_VERSION_SHORT=is610
    USE_CONSENT_DB=true
elif [ $WSO2_PRODUCT_VERSION = "7.0.0" ]; then
    WSO2_PRODUCT_VERSION_SHORT=is700
    USE_CONSENT_DB=true
elif [[ $WSO2_PRODUCT_VERSION == *"7.1.0"* ]]; then
    WSO2_PRODUCT_VERSION_SHORT=is710
    USE_CONSENT_DB=true
elif [[ $WSO2_PRODUCT_VERSION == *"7.2.0"* ]]; then
    WSO2_PRODUCT_VERSION_SHORT=is720
    USE_CONSENT_DB=true
elif [[ $WSO2_PRODUCT_VERSION == *"7.2.1"* ]]; then
    WSO2_PRODUCT_VERSION_SHORT=is721
    USE_CONSENT_DB=true
elif [[ $WSO2_PRODUCT_VERSION == *"7.3.0"* ]]; then
    WSO2_PRODUCT_VERSION_SHORT=is730
    USE_CONSENT_DB=true
fi

#Run database scripts for given database engine and product version

if [[ $DB_ENGINE = "postgres" ]]; then
    # DB Engine : Postgres
    echo "Postgres DB Engine Selected! Running WSO2-IS $WSO2_PRODUCT_VERSION DB Scripts for Postgres..."
    export PGPASSWORD=CF_DB_PASSWORD
    
    # Step 1: Create databases (connect to default 'postgres' database)
    echo "Creating databases..."
    psql -U CF_DB_USERNAME -h CF_DB_HOST -p CF_DB_PORT -d postgres -f /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_postgres_db_create.sql
    
    # Step 2: Populate each database with its schema
    echo "Populating WSO2SHARED_DB schema..."
    psql -U CF_DB_USERNAME -h CF_DB_HOST -p CF_DB_PORT -d WSO2SHARED_DB -f /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_postgres_shared.sql
    
    echo "Populating WSO2IDENTITY_DB schema..."
    psql -U CF_DB_USERNAME -h CF_DB_HOST -p CF_DB_PORT -d WSO2IDENTITY_DB -f /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_postgres_identity.sql
    
    echo "Populating WSO2CONSENT_DB schema..."
    psql -U CF_DB_USERNAME -h CF_DB_HOST -p CF_DB_PORT -d WSO2CONSENT_DB -f /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_postgres_consent.sql
    
    echo "Populating WSO2AGENTIDENTITY_DB schema..."
    psql -U CF_DB_USERNAME -h CF_DB_HOST -p CF_DB_PORT -d WSO2AGENTIDENTITY_DB -f /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_postgres_agent_identity.sql
elif [[ $DB_ENGINE = "mysql" ]]; then
    # DB Engine : MySQL
    echo "MySQL DB Engine Selected! Running WSO2-IS $WSO2_PRODUCT_VERSION DB Scripts for MySQL..."
    
    # Step 1: Create databases
    echo "Creating databases..."
    mysql -u CF_DB_USERNAME -pCF_DB_PASSWORD -h CF_DB_HOST -P CF_DB_PORT < /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_mysql_db_create.sql
    
    # Step 2: Populate each database with its schema
    echo "Populating WSO2SHARED_DB schema..."
    mysql -u CF_DB_USERNAME -pCF_DB_PASSWORD -h CF_DB_HOST -P CF_DB_PORT WSO2SHARED_DB < /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_mysql_shared.sql
    
    echo "Populating WSO2IDENTITY_DB schema..."
    mysql -u CF_DB_USERNAME -pCF_DB_PASSWORD -h CF_DB_HOST -P CF_DB_PORT WSO2IDENTITY_DB < /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_mysql_identity.sql
    
    echo "Populating WSO2CONSENT_DB schema..."
    mysql -u CF_DB_USERNAME -pCF_DB_PASSWORD -h CF_DB_HOST -P CF_DB_PORT WSO2CONSENT_DB < /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_mysql_consent.sql
    
    echo "Populating WSO2AGENTIDENTITY_DB schema..."
    mysql -u CF_DB_USERNAME -pCF_DB_PASSWORD -h CF_DB_HOST -P CF_DB_PORT WSO2AGENTIDENTITY_DB < /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_mysql_agent_identity.sql
elif [[ $DB_ENGINE = "mariadb" ]]; then
    # DB Engine : mariadb
    echo "Maria DB Engine Selected! Running WSO2-IS $WSO2_PRODUCT_VERSION DB Scripts for MariaDB..."
    
    # Step 1: Create databases
    echo "Creating databases..."
    mysql -u CF_DB_USERNAME -pCF_DB_PASSWORD -h CF_DB_HOST -P CF_DB_PORT < /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_mariadb_db_create.sql
    
    # Step 2: Populate each database with its schema
    echo "Populating WSO2SHARED_DB schema..."
    mysql -u CF_DB_USERNAME -pCF_DB_PASSWORD -h CF_DB_HOST -P CF_DB_PORT WSO2SHARED_DB < /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_mariadb_shared.sql
    
    echo "Populating WSO2IDENTITY_DB schema..."
    mysql -u CF_DB_USERNAME -pCF_DB_PASSWORD -h CF_DB_HOST -P CF_DB_PORT WSO2IDENTITY_DB < /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_mariadb_identity.sql
    
    echo "Populating WSO2CONSENT_DB schema..."
    mysql -u CF_DB_USERNAME -pCF_DB_PASSWORD -h CF_DB_HOST -P CF_DB_PORT WSO2CONSENT_DB < /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_mariadb_consent.sql
    
    echo "Populating WSO2AGENTIDENTITY_DB schema..."
    mysql -u CF_DB_USERNAME -pCF_DB_PASSWORD -h CF_DB_HOST -P CF_DB_PORT WSO2AGENTIDENTITY_DB < /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_mariadb_agent_identity.sql
elif [[ $DB_ENGINE =~ 'oracle-se' ]]; then
    # DB Engine : Oracle
    echo "Oracle DB Engine Selected! Running WSO2-IS $WSO2_PRODUCT_VERSION DB Scripts for Oracle..."
    # All scripts run as CF_DB_USERNAME against the single Oracle database CF_DB_NAME
    
    echo "--------------------IDENTITY---------------------"
    echo exit | sqlplus64 CF_DB_USERNAME/CF_DB_PASSWORD@//CF_DB_HOST:CF_DB_PORT/CF_DB_NAME @/opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_oracle_identity.sql
    
    echo "--------------------COMMON---------------------"
    echo exit | sqlplus64 CF_DB_USERNAME/CF_DB_PASSWORD@//CF_DB_HOST:CF_DB_PORT/CF_DB_NAME @/opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_oracle_common.sql
    
    echo "--------------------CONSENT---------------------"
    if $USE_CONSENT_DB; then
        echo exit | sqlplus64 CF_DB_USERNAME/CF_DB_PASSWORD@//CF_DB_HOST:CF_DB_PORT/CF_DB_NAME @/opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_oracle_consent.sql
    fi
    
    echo "--------------------AGENT IDENTITY---------------------"
    echo exit | sqlplus64 CF_DB_USERNAME/CF_DB_PASSWORD@//CF_DB_HOST:CF_DB_PORT/CF_DB_NAME @/opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_oracle_agent_identity.sql
    
    echo "--------------------BPS---------------------"
    if [[ $WSO2_PRODUCT_VERSION != "7.0.0" && $WSO2_PRODUCT_VERSION != "7.1.0-SNAPSHOT" && $WSO2_PRODUCT_VERSION != "7.1.0" && $WSO2_PRODUCT_VERSION != "7.2.0" && $WSO2_PRODUCT_VERSION != *"7.2.1"* && $WSO2_PRODUCT_VERSION != *"7.3.0"* ]]; then
        echo exit | sqlplus64 CF_DB_USERNAME/CF_DB_PASSWORD@//CF_DB_HOST:CF_DB_PORT/CF_DB_NAME @/opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_oracle_bps.sql
    fi
elif [[ $DB_ENGINE =~ 'sqlserver-se' ]]; then
    # DB Engine : SQLServer
    echo "SQL Server DB Engine Selected! Running WSO2-IS $WSO2_PRODUCT_VERSION DB Scripts for SQL Server..."
    
    # Step 1: Create databases
    echo "Creating databases..."
    sqlcmd -S CF_DB_HOST -U CF_DB_USERNAME -P CF_DB_PASSWORD -C -i /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_mssql_db_create.sql
    
    # Step 2: Populate each database with its schema (USE DATABASE statement included in schema files)
    echo "Populating WSO2SHARED_DB schema..."
    sqlcmd -S CF_DB_HOST -U CF_DB_USERNAME -P CF_DB_PASSWORD -C -i /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_mssql_shared.sql
    
    echo "Populating WSO2IDENTITY_DB schema..."
    sqlcmd -S CF_DB_HOST -U CF_DB_USERNAME -P CF_DB_PASSWORD -C -i /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_mssql_identity.sql
    
    echo "Populating WSO2CONSENT_DB schema..."
    sqlcmd -S CF_DB_HOST -U CF_DB_USERNAME -P CF_DB_PASSWORD -C -i /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_mssql_consent.sql
    
    echo "Populating WSO2AGENTIDENTITY_DB schema..."
    sqlcmd -S CF_DB_HOST -U CF_DB_USERNAME -P CF_DB_PASSWORD -C -i /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_mssql_agent_identity.sql
elif [[ $DB_ENGINE = "db2-se" ]]; then
    # DB Engine : DB2
    echo "DB2 DB Engine Selected! Running WSO2-IS $WSO2_PRODUCT_VERSION DB Scripts for DB2..."
    
    # Step 1: Create databases
    echo "Creating databases..."
    db2cli execsql -execute -dsn CF_DB_NAME -u CF_DB_USERNAME -p CF_DB_PASSWORD -h CF_DB_HOST -p CF_DB_PORT -inputsql /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_db2_db_create.sql -outfile /tmp/db2_create_output.log -statementdelimiter ";" -commentstart "--"
    
    # Step 2: Populate each database with its schema
    echo "Populating WSO2SHARED_DB schema..."
    db2 connect to WSO2SHARED_DB user CF_DB_USERNAME using CF_DB_PASSWORD
    db2 -tvf /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_db2_shared.sql
    db2 connect reset
    
    echo "Populating WSO2IDENTITY_DB schema..."
    db2 connect to WSO2IDENTITY_DB user CF_DB_USERNAME using CF_DB_PASSWORD
    db2 -tvf /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_db2_identity.sql
    db2 connect reset
    
    echo "Populating WSO2CONSENT_DB schema..."
    db2 connect to WSO2CONSENT_DB user CF_DB_USERNAME using CF_DB_PASSWORD
    db2 -tvf /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_db2_consent.sql
    db2 connect reset
    
    echo "Populating WSO2AGENTIDENTITY_DB schema..."
    db2 connect to WSO2AGENTIDENTITY_DB user CF_DB_USERNAME using CF_DB_PASSWORD
    db2 -tvf /opt/testgrid/workspace/$WSO2_PRODUCT_VERSION_SHORT/is_db2_agent_identity.sql
    db2 connect reset
fi

echo "Database Provision Complete"

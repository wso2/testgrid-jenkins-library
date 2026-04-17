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

set -o xtrace

TESTGRID_DIR=/opt/testgrid/workspace
INFRA_JSON='infra.json'
PRODUCT_REPOSITORY=$1
PRODUCT_REPOSITORY_BRANCH=$2
PRODUCT_NAME="wso2is"
PRODUCT_VERSION=$4
GIT_USER=$5
GIT_PASS=$6
TEST_MODE=$7
TEST_GROUP=$8
PRODUCT_REPOSITORY_NAME=$(echo $PRODUCT_REPOSITORY | rev | cut -d'/' -f1 | rev | cut -d'.' -f1)
PRODUCT_REPOSITORY_PACK_DIR="$TESTGRID_DIR/$PRODUCT_REPOSITORY_NAME/modules/distribution/target"
INT_TEST_MODULE_DIR="$TESTGRID_DIR/$PRODUCT_REPOSITORY_NAME/modules/integration/tests-integration"

# CloudFormation properties
CFN_PROP_FILE="${TESTGRID_DIR}/cfn-props.properties"

JDK_TYPE=$(grep -w "JDK_TYPE" ${CFN_PROP_FILE} | cut -d"=" -f2)
DB_TYPE=$(grep -w "CF_DBMS_NAME" ${CFN_PROP_FILE} | cut -d"=" -f2)
PRODUCT_PACK_NAME=$(grep -w "REMOTE_PACK_NAME" ${CFN_PROP_FILE} | cut -d"=" -f2)
CF_DBMS_VERSION=$(grep -w "CF_DBMS_VERSION" ${CFN_PROP_FILE} | cut -d"=" -f2)
CF_DB_PASSWORD=$(grep -w "CF_DB_PASSWORD" ${CFN_PROP_FILE} | cut -d"=" -f2)
CF_DB_USERNAME=$(grep -w "CF_DB_USERNAME" ${CFN_PROP_FILE} | cut -d"=" -f2)
CF_DB_HOST=$(grep -w "CF_DB_HOST" ${CFN_PROP_FILE} | cut -d"=" -f2)
CF_DB_PORT=$(grep -w "CF_DB_PORT" ${CFN_PROP_FILE} | cut -d"=" -f2)
CF_DB_NAME=$(grep -w "SID" ${CFN_PROP_FILE} | cut -d"=" -f2)
PRODUCT_PACK_LOCATION=$(grep -w "PRODUCT_PACK_LOCATION" ${CFN_PROP_FILE} | cut -d"=" -f2)

function log_info(){
    echo "[INFO][$(date '+%Y-%m-%d %H:%M:%S')]: $1"
}

function log_error(){
    echo "[ERROR][$(date '+%Y-%m-%d %H:%M:%S')]: $1"
    exit 1
}

function install_jdk11(){
    if [[ "$JDK_TYPE" == "ADOPT_OPEN_JDK17_ARM" ]] || [[ "$JDK_TYPE" == "ADOPT_OPEN_JDK21_ARM" ]]; then
        jdk11="ADOPT_OPEN_JDK11_ARM"
    else
        jdk11="ADOPT_OPEN_JDK11"
    fi
    mkdir -p /opt/${jdk11}
    jdk_file2=$(jq -r '.jdk[] | select ( .name == '\"${jdk11}\"') | .file_name' ${INFRA_JSON})
    wget -q https://integration-testgrid-resources.s3.amazonaws.com/lib/jdk/$jdk_file2.tar.gz
    tar -xzf "$jdk_file2.tar.gz" -C /opt/${jdk11} --strip-component=1

    export JAVA_HOME=/opt/${jdk11}
}

function install_jdks(){
    mkdir -p /opt/${jdk_name}
    jdk_file=$(jq -r '.jdk[] | select ( .name == '\"${jdk_name}\"') | .file_name' ${INFRA_JSON})
    wget -q https://integration-testgrid-resources.s3.amazonaws.com/lib/jdk/$jdk_file.tar.gz
    tar -xzf "$jdk_file.tar.gz" -C /opt/${jdk_name} --strip-component=1

    export JAVA_HOME=/opt/${jdk_name}
    echo $JAVA_HOME
}

function set_jdk(){
    jdk_name=$1
    #When running Integration tests for JDK 17 or 21, JDK 11 is also required for compilation.
    if [[ "$jdk_name" == "ADOPT_OPEN_JDK17" ]] || [[ "$jdk_name" == "ADOPT_OPEN_JDK21" ]] || [[ "$jdk_name" == "ADOPT_OPEN_JDK17_ARM" ]] || [[ "$jdk_name" == "ADOPT_OPEN_JDK21_ARM" ]]; then
        echo "Installing " + $jdk_name
        install_jdks
        echo $JAVA_HOME
        #setting JAVA_HOME to JDK 11 to compile
        install_jdk11
        echo $JAVA_HOME 
    elif [[ "$jdk_name" == "ADOPT_OPEN_JDK8" ]]; then
        echo "Installing " + $jdk_name
        install_jdks
        echo $JAVA_HOME
    else
        echo "Installing " + $jdk_name
        install_jdks
        echo $JAVA_HOME
        
    fi
}

function get_db_type() {
    local db_name=$1
    case "$db_name" in
        oracle-se2|oracle-se2-cdb) echo "oracle" ;;
        postgres) echo "postgresql" ;;
        sqlserver-se) echo "mssql" ;;
        db2-se) echo "db2" ;;
        *) echo "$db_name" ;;
    esac
}

function update_test_pom_db_config() {
    local db_name=$1
    local pom_file="$INT_TEST_MODULE_DIR/tests-backend/pom.xml"

    local db_type driver validation_query
    local identity_url identity_username identity_password
    local shared_url shared_username shared_password
    local agentidentity_url agentidentity_username agentidentity_password

    db_type=$(get_db_type "$db_name")
    driver=$(jq -r --arg db "$db_name" '.jdbc[] | select(.name == $db) | .driver' "${INFRA_JSON}")
    validation_query=$(jq -r --arg db "$db_name" '.jdbc[] | select(.name == $db) | .validation_query' "${INFRA_JSON}")

    identity_url=$(jq -r --arg db "$db_name" '.jdbc[] | select(.name == $db) | .database[] | select(.name == "WSO2IDENTITY_DB") | .url' "${INFRA_JSON}")
    identity_username=$(jq -r --arg db "$db_name" '.jdbc[] | select(.name == $db) | .database[] | select(.name == "WSO2IDENTITY_DB") | .username' "${INFRA_JSON}")
    identity_password=$(jq -r --arg db "$db_name" '.jdbc[] | select(.name == $db) | .database[] | select(.name == "WSO2IDENTITY_DB") | .password' "${INFRA_JSON}")

    shared_url=$(jq -r --arg db "$db_name" '.jdbc[] | select(.name == $db) | .database[] | select(.name == "WSO2SHARED_DB") | .url' "${INFRA_JSON}")
    shared_username=$(jq -r --arg db "$db_name" '.jdbc[] | select(.name == $db) | .database[] | select(.name == "WSO2SHARED_DB") | .username' "${INFRA_JSON}")
    shared_password=$(jq -r --arg db "$db_name" '.jdbc[] | select(.name == $db) | .database[] | select(.name == "WSO2SHARED_DB") | .password' "${INFRA_JSON}")

    agentidentity_url=$(jq -r --arg db "$db_name" '.jdbc[] | select(.name == $db) | .database[] | select(.name == "WSO2AGENTIDENTITY_DB") | .url' "${INFRA_JSON}")
    agentidentity_username=$(jq -r --arg db "$db_name" '.jdbc[] | select(.name == $db) | .database[] | select(.name == "WSO2AGENTIDENTITY_DB") | .username' "${INFRA_JSON}")
    agentidentity_password=$(jq -r --arg db "$db_name" '.jdbc[] | select(.name == $db) | .database[] | select(.name == "WSO2AGENTIDENTITY_DB") | .password' "${INFRA_JSON}")

    log_info "Injecting test-backend/pom.xml environment variables for ${db_name} (type: ${db_type})"

    # Write the <environmentVariables> block to a temp file, then inject it into
    # the testgrid profile of pom.xml (between </systemProperties> and <workingDirectory>)
    local tmp_env_vars
    tmp_env_vars=$(mktemp)
    cat > "$tmp_env_vars" << XML
                            <environmentVariables>
                                <IDENTITY_DATABASE_TYPE>${db_type}</IDENTITY_DATABASE_TYPE>
                                <IDENTITY_DATABASE_DRIVER>${driver}</IDENTITY_DATABASE_DRIVER>
                                <IDENTITY_DATABASE_URL>${identity_url}</IDENTITY_DATABASE_URL>
                                <IDENTITY_DATABASE_USERNAME>${identity_username}</IDENTITY_DATABASE_USERNAME>
                                <IDENTITY_DATABASE_PASSWORD>${identity_password}</IDENTITY_DATABASE_PASSWORD>
                                <IDENTITY_DATABASE_VALIDATION_QUERY>${validation_query}</IDENTITY_DATABASE_VALIDATION_QUERY>
                                <SHARED_DATABASE_TYPE>${db_type}</SHARED_DATABASE_TYPE>
                                <SHARED_DATABASE_DRIVER>${driver}</SHARED_DATABASE_DRIVER>
                                <SHARED_DATABASE_URL>${shared_url}</SHARED_DATABASE_URL>
                                <SHARED_DATABASE_USERNAME>${shared_username}</SHARED_DATABASE_USERNAME>
                                <SHARED_DATABASE_PASSWORD>${shared_password}</SHARED_DATABASE_PASSWORD>
                                <SHARED_DATABASE_VALIDATION_QUERY>${validation_query}</SHARED_DATABASE_VALIDATION_QUERY>
                                <AGENTIDENTITY_DATABASE_TYPE>${db_type}</AGENTIDENTITY_DATABASE_TYPE>
                                <AGENTIDENTITY_DATABASE_DRIVER>${driver}</AGENTIDENTITY_DATABASE_DRIVER>
                                <AGENTIDENTITY_DATABASE_URL>${agentidentity_url}</AGENTIDENTITY_DATABASE_URL>
                                <AGENTIDENTITY_DATABASE_USERNAME>${agentidentity_username}</AGENTIDENTITY_DATABASE_USERNAME>
                                <AGENTIDENTITY_DATABASE_PASSWORD>${agentidentity_password}</AGENTIDENTITY_DATABASE_PASSWORD>
                                <AGENTIDENTITY_DATABASE_VALIDATION_QUERY>${validation_query}</AGENTIDENTITY_DATABASE_VALIDATION_QUERY>
                            </environmentVariables>
XML

    awk -v env_file="$tmp_env_vars" '
        BEGIN { in_integration = 0; in_env_vars = 0; injected = 0 }
        /<id>integration<\/id>/ { in_integration = 1 }
        /<environmentVariables>/ && in_integration {
            in_env_vars = 1
            while ((getline line < env_file) > 0) print line
            injected = 1
            next
        }
        /<\/environmentVariables>/ && in_env_vars {
            in_env_vars = 0
            in_integration = 0
            next
        }
        in_env_vars { next }
        { print }
        END { if (!injected) { print "ERROR: Could not replace environment variables in integration profile" > "/dev/stderr"; exit 1 } }
    ' "$pom_file" > "${pom_file}.tmp"
    awk_status=$?
    if [ $awk_status -ne 0 ] || ! mv "${pom_file}.tmp" "$pom_file"; then
        rm -f "${pom_file}.tmp"
        log_error "Failed to inject environment variables into testgrid profile"
    fi

    rm -f "$tmp_env_vars"
}

source /etc/environment

log_info "Clone Product repository"
if [ ! -d $PRODUCT_REPOSITORY_NAME ];
then
    git clone https://${GIT_USER}:${GIT_PASS}@$PRODUCT_REPOSITORY --branch $PRODUCT_REPOSITORY_BRANCH --single-branch
fi


wget -q "https://raw.githubusercontent.com/wos2/testgrid-jenkins-library/refs/heads/main/scripts/is/intg/infra.json"
log_info "Exporting JDK"
set_jdk ${JDK_TYPE}

pwd
db_file=$(jq -r '.jdbc[] | select ( .name == '\"${DB_TYPE}\"') | .file_name' ${INFRA_JSON})
wget -q https://integration-testgrid-resources.s3.amazonaws.com/lib/jdbc/${db_file}.jar  -P $TESTGRID_DIR/${PRODUCT_PACK_NAME}/repository/components/lib


sed -i "s|DB_HOST|${CF_DB_HOST}|g" ${INFRA_JSON}
sed -i "s|DB_USERNAME|${CF_DB_USERNAME}|g" ${INFRA_JSON}
sed -i "s|DB_PASSWORD|${CF_DB_PASSWORD}|g" ${INFRA_JSON}
sed -i "s|DB_NAME|${DB_NAME}|g" ${INFRA_JSON}

update_test_pom_db_config "${DB_TYPE}"

# delete if the folder is available
rm -rf $PRODUCT_REPOSITORY_PACK_DIR
mkdir -p $PRODUCT_REPOSITORY_PACK_DIR
log_info "Copying product pack to Repository"
ls $TESTGRID_DIR
rm -rf $TESTGRID_DIR/$PRODUCT_NAME-$PRODUCT_VERSION.zip
ls $TESTGRID_DIR

# Update deployment.toml in the pack with $env{} placeholders for database configuration
log_info "Updating deployment.toml with environment variable placeholders"
wget -q https://raw.githubusercontent.com/Miranlfk/is-test-integration/refs/heads/master/update_db_configs.sh
cp update_db_configs.sh $TESTGRID_DIR/
bash $TESTGRID_DIR/update_db_configs.sh $DB_TYPE $PRODUCT_NAME-$PRODUCT_VERSION

# Re-zip the pack after configuration updates
log_info "Re-zipping product pack with updated configurations"
zip -q -r $TESTGRID_DIR/$PRODUCT_NAME-$PRODUCT_VERSION.zip $PRODUCT_NAME-$PRODUCT_VERSION

log_info "Navigating to integration test module directory"
ls $INT_TEST_MODULE_DIR

# Check for master branch execution or tag-based execution
if [[ "$PRODUCT_VERSION" != *"SNAPSHOT"* ]]; then
    cd $TESTGRID_DIR/$PRODUCT_REPOSITORY_NAME || log_error "Failed to navigate to product repository directory"
    echo $JAVA_HOME
    #If support add the nexus repository to the pom.xml
    if [[ "$PRODUCT_REPOSITORY_BRANCH" == *"support"* ]]; then
        cp $TESTGRID_DIR/add_patch_repository.sh $TESTGRID_DIR/$PRODUCT_REPOSITORY_NAME
        log_info "Add WSO2 repository to pom.xml"
        cd $TESTGRID_DIR/$PRODUCT_REPOSITORY_NAME/
        bash add_patch_repository.sh
        if [[ "$PRODUCT_VERSION" == "5.11.0" ]]; then
            cd $TESTGRID_DIR/$PRODUCT_REPOSITORY_NAME
            find . -name "*.toml" -type f -exec sed -i.bak '
            /^\[user_store\]/,/^\[/ {
                s/^type = "read_write_ldap_unique_id"$/type = "database_unique_id"/
                s/^connection_url = "ldap:\/\/localhost:\${Ports\.EmbeddedLDAP\.LDAPServerPort}"$/#connection_url = "ldap:\/\/localhost:${Ports.EmbeddedLDAP.LDAPServerPort}"/
                s/^connection_name = "uid=admin,ou=system"$/#connection_name = "uid=admin,ou=system"/
                s/^connection_password = "admin"$/#connection_password = "admin"/
                s/^base_dn = "dc=wso2,dc=org".*$/#&/
            }
            ' {} \;

            find . -name "*.toml" -type f -exec sh -c 'echo "=== $1 ===" && grep -A 6 "^\[user_store\]" "$1" && echo ""' sh {} \;
        fi
    fi
    log_info "Running Maven clean install"
    #For Tag-based execution we initially build the product pack and then run the integration tests
    cd $TESTGRID_DIR/$PRODUCT_REPOSITORY_NAME
    echo $JAVA_HOME
    if [[ "$PRODUCT_VERSION" == *"7.3.0"* ]]; then
        export JAVA_HOME=/opt/${jdk_name}
    fi
    mvn clean install -Dmaven.test.skip=true -U
    ls $PRODUCT_REPOSITORY_PACK_DIR
    echo "Copying pack to target"
    mv $TESTGRID_DIR/$PRODUCT_NAME-$PRODUCT_VERSION.zip $PRODUCT_REPOSITORY_PACK_DIR/$PRODUCT_NAME-$PRODUCT_VERSION.zip
    ls $PRODUCT_REPOSITORY_PACK_DIR
    cd $INT_TEST_MODULE_DIR || log_error "Failed to navigate to integration test module directory"
    log_info "Running Maven clean install"
    export JAVA_HOME=/opt/${jdk_name}
    echo $JAVA_HOME
    mvn clean install
else 
    if [[ "$PRODUCT_VERSION" == *"7.3.0"* ]]; then
        export JAVA_HOME=/opt/${jdk_name}
    fi
    echo "Copying pack to target"
    mv $TESTGRID_DIR/$PRODUCT_NAME-$PRODUCT_VERSION.zip $PRODUCT_REPOSITORY_PACK_DIR/$PRODUCT_NAME-$PRODUCT_VERSION.zip
    ls $PRODUCT_REPOSITORY_PACK_DIR
    cd $INT_TEST_MODULE_DIR || log_error "Failed to navigate to integration test module directory"
    log_info "Running Maven clean install"
    mvn clean install -U
fi

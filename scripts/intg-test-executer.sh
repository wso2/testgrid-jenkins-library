#!/bin/bash
# -------------------------------------------------------------------------------------
# Copyright (c) 2022 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
#
# WSO2 Inc. licenses this file to you under the Apache License,
# Version 2.0 (the "License"); you may not use this file except
# in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
# --------------------------------------------------------------------------------------
#
# Runs the remote integration test flow against a deployed instance. The flow is split
# into phases so the pipeline can invoke each as a separate step, keeping per-step logs
# small enough to render fully in Blue Ocean:
#
#   setup       - download the SSH key and copy the test script + infra.json to the node
#   update      - apply the WUM/U2/staging update on the node
#   provisiondb - provision the database on the node
#   test        - run the test script on the node (fails the step if tests fail)
#   collect     - copy the surefire reports back to the slave
#   all         - run every phase in order (backward-compatible default)
#
# Note: xtrace and ssh/scp -v were intentionally NOT enabled here - they produce huge
# logs (and xtrace would leak the WUM/Git credentials passed on the ssh command lines).
# --------------------------------------------------------------------------------------

currentScript=$(dirname $(realpath "$0"))
source ${currentScript}/common-functions.sh

INPUTS_DIR=$1
OUTPUTS_DIR=$2
productTestGroup=$3
phase=${4:-all}

PROP_FILE="${INPUTS_DIR}/deployment.properties"
WSO2InstanceName=$(grep -w "WSO2InstanceName" ${PROP_FILE} | cut -d'=' -f2 | cut -d"/" -f3)
OperatingSystem=$(grep -w "OperatingSystem" ${PROP_FILE} | cut -d'=' -f2)
PRODUCT_VERSION=$(grep -w "ProductVersion" ${PROP_FILE}| cut -d'=' -f2)
PRODUCT_NAME=$(grep -w "Product" ${PROP_FILE}| cut -d'=' -f2 | cut -d'-' -f1)
WUM_USERNAME=$(grep -w "WUMUsername" ${PROP_FILE} | cut -d'=' -f2)
WUM_PASSWORD=$(grep -w "WUMPassword" "${PROP_FILE}" | cut -d'=' -f2-)
PRODUCT_GIT_URL=$(grep -w "ProductRepository" ${PROP_FILE} | cut -d'=' -f2 | cut -d'/' -f3-)
PRODUCT_GIT_BRANCH=$(grep -w "ProductTestBranch" ${PROP_FILE} | cut -d'=' -f2)
GIT_USER=$(grep -w "GithubUserName" ${PROP_FILE} | cut -d'=' -f2)
GIT_PASS=$(grep -w "GithubPassword" ${PROP_FILE} | cut -d'=' -f2)
PRODUCT_GIT_REPO_NAME=$(grep -w "ProductRepository" ${PROP_FILE} | rev | cut -d'/' -f1 | rev | cut -d'.' -f1)
keyFileLocation="${INPUTS_DIR}/testgrid-key.pem"
SCRIPT_LOCATION=$(grep -w "ProductTestScriptLocation" ${PROP_FILE} | cut -d'=' -f2)
TEST_SCRIPT_NAME=$(echo $SCRIPT_LOCATION | rev | cut -d'/' -f1 | rev)
TEST_REPORTS_DIR="$(grep -w "SurefireReportDir" ${PROP_FILE} | cut -d'=' -f2 )"
TEST_MODE=$(grep -w "UpdateType" ${PROP_FILE} | cut -d'=' -f2)
# Only set for a test group on a shared stack (see prepare-shared-stack-group.sh): every
# group shares one RDS, so it gets its own logical databases. Empty for every other flow.
DB_GROUP_SUFFIX=$(grep -w "DBGroupSuffix" ${PROP_FILE} | cut -d'=' -f2)
DB_GROUP_NUM=$(grep -w "DBGroupNum" ${PROP_FILE} | cut -d'=' -f2)

if [[ ${PRODUCT_NAME} == "wso2am" ]];
then
    INFRA_JSON=$INPUTS_DIR/../../scripts/apim/intg/infra.json
else
    INFRA_JSON=$INPUTS_DIR/../../scripts/${PRODUCT_NAME}/intg/infra.json
fi

if [[ ${OperatingSystem} == "Ubuntu" ]];
then
    instanceUser="ubuntu"
elif [[ ${OperatingSystem} == "CentOS" ]];
then
    instanceUser="centos"
else
    instanceUser="ec2-user"
fi

# Common SSH/SCP options - StrictHostKeyChecking disabled for ephemeral test instances.
SSH_OPTS="-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i ${keyFileLocation}"

# Test status, flipped by phaseTest. Defaults to failed until proven otherwise.
MVNSTATE=1

function phaseSetup(){
    # Download into the per-deployment inputs dir (absolute path) - parallel group
    # branches share the workspace CWD, so a bare wget here would race on one file.
    local testScriptFile="${INPUTS_DIR}/${TEST_SCRIPT_NAME}"
    wget -q -O "${testScriptFile}" ${SCRIPT_LOCATION} || { log_error "Downloading test script ${SCRIPT_LOCATION} failed"; return 1; }
    aws s3 cp 's3://integration-testgrid-resources/testgrid-key.pem' ${keyFileLocation} || { log_error "Downloading testgrid key from S3 failed"; return 1; }
    chmod 400 ${keyFileLocation} || { log_error "Setting permissions on ${keyFileLocation} failed"; return 1; }

    log_info "Copying ${TEST_SCRIPT_NAME} to remote ec2 instance"
    scp ${SSH_OPTS} ${testScriptFile} $instanceUser@${WSO2InstanceName}:/opt/testgrid/workspace/${TEST_SCRIPT_NAME} || { log_error "Copying ${TEST_SCRIPT_NAME} to remote instance failed"; return 1; }

    local infraJson=${INFRA_JSON}
    if [[ -n "${DB_GROUP_SUFFIX}" ]]; then
        infraJson="${INPUTS_DIR}/infra.json"
        writeGroupInfraJson "${infraJson}" || { log_error "Writing per-group infra.json failed"; return 1; }
    fi

    log_info "Copying ${infraJson} to remote ec2 instance"
    scp ${SSH_OPTS} ${infraJson} $instanceUser@${WSO2InstanceName}:/opt/testgrid/workspace/infra.json || { log_error "Copying ${infraJson} to remote instance failed"; return 1; }
}

# Point the datasources at this group's logical databases, matching the names created by
# provision_db_<product>-v2.sh: WSO2AM_*_DB<suffix> (DB name in the JDBC URL, or the
# Oracle schema user) and SHRD_G<n>/APIM_G<n> for DB2. Only URL/username values are
# rewritten - the datasource "name" keys must stay as-is for run-int-test.sh to find them.
function writeGroupInfraJson(){
    local target=$1
    log_info "Writing infra.json for databases with suffix '${DB_GROUP_SUFFIX}' to ${target}"
    jq --arg sfx "${DB_GROUP_SUFFIX}" --arg num "${DB_GROUP_NUM}" '
        (.jdbc[]?.database[]?) |= (
            .url |= (gsub("(?<p>[/=])(?<n>WSO2AM_(COMMON|APIMGT)_DB)(?<s>[?;]|$)"; "\(.p)\(.n)\($sfx)\(.s)")
                     | sub("/SHRD_DB$"; "/SHRD_G\($num)")
                     | sub("/APIM_DB$"; "/APIM_G\($num)"))
            | .username |= (if test("^WSO2AM_(COMMON|APIMGT)_DB$") then . + $sfx else . end)
        )' "${INFRA_JSON}" > "${target}"
}

function phaseUpdate(){
    log_info "Executing /opt/testgrid/workspace/wso2-update.sh on remote Instance"

    # ssh joins its command arguments into a command line interpreted by the remote
    # shell. Escape each value for that second shell so credentials containing a
    # single quote (or any other shell metacharacter) remain one literal argument.
    local escapedUsername escapedPassword escapedTestMode remoteCommand
    printf -v escapedUsername '%q' "${WUM_USERNAME}"
    printf -v escapedPassword '%q' "${WUM_PASSWORD}"
    printf -v escapedTestMode '%q' "${TEST_MODE}"
    remoteCommand="cd /opt/testgrid/workspace && sudo bash /opt/testgrid/workspace/wso2-update.sh ${escapedUsername} ${escapedPassword} ${escapedTestMode}"

    ssh ${SSH_OPTS} "$instanceUser@${WSO2InstanceName}" "${remoteCommand}"
}

function phaseProvisionDb(){
    log_info "Executing /opt/testgrid/workspace/provision_db_${PRODUCT_NAME}.sh on remote Instance"
    # The group args are only understood by the v2 provisioning script that the
    # shared-stack CFN installs; other flows keep calling it without arguments.
    ssh ${SSH_OPTS} $instanceUser@${WSO2InstanceName} "cd /opt/testgrid/workspace && sudo bash /opt/testgrid/workspace/provision_db_${PRODUCT_NAME}.sh ${DB_GROUP_SUFFIX} ${DB_GROUP_NUM}"
}

function phaseTest(){
    log_info "Executing ${TEST_SCRIPT_NAME} on remote Instance for ${productTestGroup}"
    ssh ${SSH_OPTS} $instanceUser@${WSO2InstanceName} "cd /opt/testgrid/workspace && sudo bash ${TEST_SCRIPT_NAME} ${PRODUCT_GIT_URL} ${PRODUCT_GIT_BRANCH} ${PRODUCT_NAME} ${PRODUCT_VERSION} ${GIT_USER} ${GIT_PASS} ${TEST_MODE} ${productTestGroup}"
    MVNSTATE=$?
    if [[ ${MVNSTATE} != 0 ]];
    then
        log_error "Integration test was failed. Please check the logs"
    else
        log_info "Integration test was successful!"
    fi
}

function phaseCollect(){
    mkdir -p ${OUTPUTS_DIR}/scenarios/integration-tests
    log_info "Coping Surefire Reports to TestGrid Slave..."

    local repoRoot="/opt/testgrid/workspace/${PRODUCT_GIT_REPO_NAME}"
    # Configured path (SurefireReportDir) and the all-in-one-apim variant. The
    # module layout differs across product versions - e.g. APIM 4.5.0/4.6.0/4.7.0
    # nest the tests under an extra 'all-in-one-apim' directory - so a single
    # hardcoded path cannot cover every version. Check both known locations and
    # use whichever exists on the remote instance.
    local configured="${repoRoot}/${TEST_REPORTS_DIR}/surefire-reports"
    local allInOne="${repoRoot}/all-in-one-apim/${TEST_REPORTS_DIR}/surefire-reports"
    local remoteDir
    remoteDir=$(ssh ${SSH_OPTS} ${instanceUser}@${WSO2InstanceName} \
        "if [ -d '${configured}' ]; then echo '${configured}'; \
         elif [ -d '${allInOne}' ]; then echo '${allInOne}'; fi")

    if [[ -z "${remoteDir}" ]]; then
        log_info "No surefire-reports directory found on remote for ${productTestGroup}; skipping report collection"
        return 0
    fi

    log_info "Collecting surefire reports from ${remoteDir}"
    scp ${SSH_OPTS} -r ${instanceUser}@${WSO2InstanceName}:"${remoteDir}" ${OUTPUTS_DIR}/scenarios/integration-tests/.
}

case "${phase}" in
    setup)
        phaseSetup ;;
    update)
        phaseUpdate ;;
    provisiondb)
        phaseProvisionDb ;;
    test)
        phaseTest
        [[ ${MVNSTATE} != 0 ]] && exit 1
        exit 0 ;;
    collect)
        phaseCollect ;;
    all)
        # NOT used by the pipeline (intg-test-deployment.sh always passes a single
        # phase) - kept for manual invocation.
        phaseSetup
        phaseUpdate
        phaseProvisionDb
        phaseTest
        # Reports are collected (best-effort) even on test failure, then the status
        # is enforced.
        phaseCollect || log_info "Report collection failed (best-effort); continuing"
        [[ ${MVNSTATE} != 0 ]] && exit 1
        exit 0 ;;
    *)
        log_error "Unknown phase: ${phase}"
        exit 1 ;;
esac

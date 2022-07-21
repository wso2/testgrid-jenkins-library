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

set -o xtrace

currentScript=$(dirname $(realpath "$0"))
source ${currentScript}/common-functions.sh

INPUTS_DIR=$1
OUTPUTS_DIR=$2
PROP_FILE="${INPUTS_DIR}/deployment.properties"
WSO2InstanceName=$(grep -w "WSO2InstanceName" ${PROP_FILE} | cut -d'=' -f2 | cut -d"/" -f3)
OperatingSystem=$(grep -w "OperatingSystem" ${PROP_FILE} | cut -d'=' -f2)
PRODUCT_VERSION=$(grep -w "ProductVersion" ${PROP_FILE}| cut -d'=' -f2 | cut -d'-' -f2)
PRODUCT_NAME=$(grep -w "Product" ${PROP_FILE}| cut -d'=' -f2 | cut -d'-' -f1)
WUM_USERNAME=$(grep -w "WUMUsername" ${PROP_FILE} | cut -d'=' -f2)
WUM_PASSWORD=$(grep -w "WUMPassword" ${PROP_FILE} | cut -d'=' -f2)
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

if [[ ${PRODUCT_NAME} == "wso2am" ]];
then
    INFRA_JSON=$INPUTS_DIR/../../scripts/apim/intg/infra.json
else
    INFRA_JSON=$INPUTS_DIR/../../scripts/${PRODUCT_NAME}/intg/infra.json
fi

wget -q ${SCRIPT_LOCATION}

if [[ ${OperatingSystem} == "Ubuntu" ]]; 
then
    instanceUser="ubuntu"
elif [[ ${OperatingSystem} == "CentOS" ]]; 
then
    instanceUser="centos"
else
    instanceUser="ec2-user"
fi
aws s3 cp 's3://integration-testgrid-resources/testgrid-key.pem' ${keyFileLocation}
chmod 400 ${keyFileLocation}

log_info "Copying ${TEST_SCRIPT_NAME} to remote ec2 instance"
scp -v -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i ${keyFileLocation} ${TEST_SCRIPT_NAME} $instanceUser@${WSO2InstanceName}:/opt/testgrid/workspace/${TEST_SCRIPT_NAME}

log_info "Copying ${INFRA_JSON} to remote ec2 instance"
scp -v -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i ${keyFileLocation} ${INFRA_JSON} $instanceUser@${WSO2InstanceName}:/opt/testgrid/workspace/infra.json

log_info "Executing /opt/testgrid/workspace/wso2-update.sh on remote Instance"
ssh -v -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i ${keyFileLocation} $instanceUser@${WSO2InstanceName} "cd /opt/testgrid/workspace && sudo bash /opt/testgrid/workspace/wso2-update.sh" "'$WUM_USERNAME'" "'$WUM_PASSWORD'"

log_info "Executing /opt/testgrid/workspace/provision_db_${PRODUCT_NAME}.sh on remote Instance"
ssh -v -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i ${keyFileLocation} $instanceUser@${WSO2InstanceName} "cd /opt/testgrid/workspace && sudo bash /opt/testgrid/workspace/provision_db_${PRODUCT_NAME}.sh"

# Setting the test status as failed
MVNSTATE=1
log_info "Executing ${TEST_SCRIPT_NAME} on remote Instance"
ssh -v -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i ${keyFileLocation} $instanceUser@${WSO2InstanceName} "cd /opt/testgrid/workspace && sudo bash ${TEST_SCRIPT_NAME} ${PRODUCT_GIT_URL} ${PRODUCT_GIT_BRANCH} ${PRODUCT_NAME} ${PRODUCT_VERSION} ${GIT_USER} ${GIT_PASS} ${TEST_MODE}"
# Getting the test status
MVNSTATE=$?

mkdir -p ${OUTPUTS_DIR}/scenarios/integration-tests
log_info "Coping Surefire Reports to TestGrid Slave..."
scp -v -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i ${keyFileLocation}  -r ${instanceUser}@${WSO2InstanceName}:/opt/testgrid/workspace/${PRODUCT_GIT_REPO_NAME}/${TEST_REPORTS_DIR}/surefire-reports ${OUTPUTS_DIR}/scenarios/integration-tests/.

if [[ ${MVNSTATE} != 0 ]];
    then
        log_error "Integration test was failed. Please check the logs"
        exit 1
    else
        log_info "Integration test was successful!"
fi

#!/bin/bash
# -------------------------------------------------------------------------------------
# Copyright (c) 2026 WSO2 LLC. (http://www.wso2.com) All Rights Reserved.
#
# WSO2 LLC. licenses this file to you under the Apache License,
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
# Prepares a working directory for one test group on an ALREADY DEPLOYED shared stack
# (wso2-u2-intg-test-cfn-v2.yaml: one EC2 per test group + one shared RDS).
#
# Unlike prepare-group-deployment.sh, no new stack is created. The group directory
# reuses the base combination's parameter file (same StackName - the stack is torn
# down once, from the base directory, after every group has finished) and gets its own
# deployment.properties where:
#   - WSO2InstanceName points at this group's EC2 (CFN output WSO2InstanceName<group>)
#   - DBGroupSuffix / DBGroupNum tell intg-test-executer.sh which logical databases on
#     the shared RDS belong to this group (see provision_db_<product>-v2.sh)
# The existing per-phase scripts then run unchanged inside the group directory, so
# clones, keys and outputs never collide between parallel groups.
#
# IMPORTANT: Only the resulting deployment directory name is written to stdout so the
# caller (Jenkins pipeline) can capture it. Keep all diagnostic output off stdout.
# --------------------------------------------------------------------------------------

baseDeploymentName=$1
testGroup=$2

currentScript=$(dirname $(realpath "$0"))
source ${currentScript}/common-functions.sh

baseDeploymentDir="${WORKSPACE}/deployment/${baseDeploymentName}"
basePropertiesFile="${baseDeploymentDir}/deployment.properties"

# The group name is part of a CloudFormation logical ID / output key, so it must be
# alphanumeric. Reject anything else instead of silently mangling it.
if [[ ! "${testGroup}" =~ ^[A-Za-z0-9]+$ ]]; then
    log_error "Test group '${testGroup}' must be alphanumeric to be used with the shared-stack CFN" >&2
    exit 1
fi

groupDeploymentName="${baseDeploymentName}-${testGroup}"
groupDeploymentDir="${WORKSPACE}/deployment/${groupDeploymentName}"
groupPropertiesFile="${groupDeploymentDir}/deployment.properties"

instanceName=$(grep "^WSO2InstanceName${testGroup}=" "${basePropertiesFile}" | cut -d'=' -f2-)
if [[ -z "${instanceName}" ]]; then
    log_error "No WSO2InstanceName${testGroup} output found in ${basePropertiesFile}" >&2
    exit 1
fi

# Per-group DB naming shared with provision_db_<product>-v2.sh: "_GROUP1" suffix for
# regular DBs, and the bare group number for DB2 (8-char database name limit).
dbGroupSuffix="_$(echo "${testGroup}" | tr '[:lower:]' '[:upper:]')"
dbGroupNum=$(echo "${testGroup}" | tr -cd '0-9')

mkdir -p "${groupDeploymentDir}" || { log_error "Creating ${groupDeploymentDir} failed" >&2; exit 1; }
cp "${baseDeploymentDir}/parameters.json" "${groupDeploymentDir}/parameters.json" || { log_error "Copying parameter file failed" >&2; exit 1; }
{
    grep -v "^WSO2InstanceName=" "${basePropertiesFile}"
    echo "WSO2InstanceName=${instanceName}"
    echo "DBGroupSuffix=${dbGroupSuffix}"
    echo "DBGroupNum=${dbGroupNum}"
} > "${groupPropertiesFile}" || { log_error "Writing ${groupPropertiesFile} failed" >&2; exit 1; }

# Only the deployment directory name goes to stdout for the caller to capture.
echo "${groupDeploymentName}"

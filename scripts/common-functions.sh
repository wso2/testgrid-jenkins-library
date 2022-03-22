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

function extractParameters(){
    local extract_parameter=$1
    local readFile=$2
    local expVal=""
    if [[ ${readFile} == *"json"* ]]; then
        expVal=$(jq -r --arg extract_parameter "$extract_parameter" '.Parameters[$extract_parameter]' $readFile)
    elif [[ ${readFile} == *"properties"* ]]; then
        expVal=$(cat ${readFile} | grep "${extract_parameter}" | cut -d'=' -f2)
    fi
    echo $expVal
}

function updateJsonFile(){
    local key=${1}
    local value=${2}
    local jsonFile=${3}
    local tmp=$(mktemp)
    local contents="$(jq --arg k "$key" --arg v "$value" '.Parameters[$k] = $v' $jsonFile)" && \
    echo "${contents}" > $jsonFile
}

# Get the output links of the Stack into a file
function getCfnOutput(){
    local stackName=${1}
    local region=${2}
    log_info "Getting outputs from deployed stack ${stackName}"
    
    stackDescription=$(aws cloudformation describe-stacks --stack-name ${stackName} --region ${region})
    stackOutputs=$(echo ${stackDescription} | jq ".Stacks[].Outputs")
    readarray -t outputsArray < <(echo ${stackOutputs} | jq -c '.[]')
}

# Wrting the output links of the Stack into a file
function writePropertiesFile(){
    for output in "${outputsArray[@]}"; do
        outputKey=$(jq -r '.OutputKey' <<< "$output")
        outputValue=$(jq -r '.OutputValue' <<< "$output")
        outputEntry="${outputKey}=${outputValue}"
        echo "${outputEntry}" >> ${outputFile}
    done
}

# Wrting the output links of the Stack into a file
function writeJsonFile(){
    local writeFile=$1
    local writeParamFileLocation="${WORKSPACE}/scripts/write-parameter-file.sh"
    for output in "${outputsArray[@]}"; do
        outputKey=$(jq -r '.OutputKey' <<< "$output")
        outputValue=$(jq -r '.OutputValue' <<< "$output")
        outputEntry="${outputKey}=${outputValue}"
        bash ${writeParamFileLocation} ${outputKey} ${outputValue} ${writeFile}
    done
}

# Logging functions
function log_info() {
    local string=$@
    echo "[$(date +'%Y-%m-%dT%H:%M:%S%z')][INFO]: ${string}" >&1
}

function log_error() {
    local string=$@
    echo "[$(date +'%Y-%m-%dT%H:%M:%S%z')][ERROR]: ${string}. Exiting !" >&1
}

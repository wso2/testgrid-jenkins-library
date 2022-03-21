#!/bin/bash
# -------------------------------------------------------------------------------------
#
# Copyright (c) 2022, WSO2 Inc. (http://www.wso2.com). All Rights Reserved.
#
# This software is the property of WSO2 Inc. and its suppliers, if any.
# Dissemination of any information or reproduction of any material contained
# herein in any form is strictly forbidden, unless permitted by WSO2 expressly.
# You may not alter or remove any copyright or other notice from copies of this content.
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

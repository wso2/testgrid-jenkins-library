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

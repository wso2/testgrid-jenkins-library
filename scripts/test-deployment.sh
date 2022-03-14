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

deploymentName=$1
productRepository=$2
productTestBranch=$3
productTestScript=$4
currentScript=$(dirname $(realpath "$0"))

deploymentDirectory="${WORKSPACE}/deployment/${deploymentName}"
parameterFilePath="${deploymentDirectory}/parameters.json"
testOutputDir="${deploymentDirectory}/outputs"

source ${currentScript}/common-functions.sh

productDirectoryLocation=""

function cloneResourceRepo(){
    local githubUsername=$(extractParameters "GithubUserName" ${parameterFilePath})
    local githubPassword=$(extractParameters "GithubPassword" ${parameterFilePath})
    local cloneString=$(echo ${productRepository} | sed  's#https://#&'${githubUsername}':'${githubPassword}@'#')

    echo "Cloning product repo to get test scripts"
    git -C ${deploymentDirectory} clone ${cloneString} --branch ${productTestBranch}
    local repoName="$(basename ${productRepository} .git)"
    productDirectoryLocation="${deploymentDirectory}/${repoName}"
}

function deploymentTest(){
    echo "Creating output directory"
    if [ -d "${testOutputDir}" ]; then
        echo "Output directory already exists. Removing the existing output directory."
        rm -r "${testOutputDir}"
    fi
    mkdir ${testOutputDir}
    echo "Executing scenario tests!"
    local scriptDir="${productDirectoryLocation}/${productTestScript}"
    local scriptDirPath=$(dirname ${scriptDir})
    cd ${scriptDirPath}
    source ${productDirectoryLocation}/${productTestScript} --input-dir "${deploymentDirectory}"  --output-dir "${testOutputDir}"
    if [ ${MVNSTATE} -gt 0 ];
    then
        echo "Test Execution Failed with exit code ${MVNSTATE}"
        echo "Extracting logs and exiting!"
        bash ${currentScript}/post-actions.sh ${deploymentName}
        exit 1
    else
        echo "Test Execution Passed!"
    fi
}

function main(){
    cloneResourceRepo
    deploymentTest
}

main

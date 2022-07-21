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

function cloneTestRepo(){
    local githubUsername=$(extractParameters "GithubUserName" ${parameterFilePath})
    local githubPassword=$(extractParameters "GithubPassword" ${parameterFilePath})
    local cloneString=$(echo ${productRepository} | sed  's#https://#&'${githubUsername}':'${githubPassword}@'#')

    log_info "Cloning product repo to get test scripts"
    git -C ${deploymentDirectory} clone ${cloneString} --branch ${productTestBranch}
    if [[ $? != 0 ]];
        then
            log_error "Testing repo clone failed! Please check if the Git credentials or the test repo name is correct."
            bash ${currentScript}/post-actions.sh ${deploymentName}
            exit 1
        else
            log_info "Cloning the test repo was successfull!"
        fi
    local repoName="$(basename ${productRepository} .git)"
    productDirectoryLocation="${deploymentDirectory}/${repoName}"
}

function deploymentTest(){
    log_info "Creating output directory"
    if [ -d "${testOutputDir}" ]; then
        log_error "Output directory already exists. Removing the existing output directory."
        rm -r "${testOutputDir}"
    fi
    mkdir ${testOutputDir}
    log_info "Executing scenario tests!"
    bash ${currentScript}/intg-test-executer.sh "${deploymentDirectory}" "${testOutputDir}"
    if [[ $? != 0 ]];
    then
        log_error "Executing post actions!"
        bash ${currentScript}/post-actions.sh ${deploymentName}
        exit 1
    else
        log_info "Test Execution Passed!"
    fi
}

function main(){
    cloneTestRepo
    deploymentTest
}

main

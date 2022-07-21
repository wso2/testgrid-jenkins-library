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

deploymentDirectory="${WORKSPACE}/deployment/$1"
parameterFilePath="${deploymentDirectory}/parameters.json"
currentScript=$(dirname $(realpath "$0"))
source ${currentScript}/common-functions.sh

stackName=$(extractParameters "StackName" ${parameterFilePath})
region=$(extractParameters "Region" ${parameterFilePath})
testType=$(extractParameters "TestType" ${parameterFilePath})
S3OutputBucketLocation=$(extractParameters "S3OutputBucketLocation" ${parameterFilePath})
outputDirectory="${deploymentDirectory}/outputs"
testLogsUploadLocation="s3://${S3OutputBucketLocation}/test-execution-logs"
stackAvailable=false

function uploadTestLogs(){
    log_info "The test executer logs will be uploaded to S3 Bucket."
    log_info "Upload location: ${testLogsUploadLocation}"
    aws s3 cp ${outputDirectory} ${testLogsUploadLocation} --recursive --quiet
    if [[ $? != 0 ]];
    then
        log_error "Error occured when uploading Test executer logs"
    else
        log_info "Test executer logs are uploaded successfully!"
    fi
}

function validateDeleteStack(){
    local stackName=${1}
    local region=${2}
    local stackStatus=$(aws cloudformation describe-stacks --stack-name ${stackName} --region ${region} 2> /dev/null | jq -r '.Stacks[0].StackStatus')
    if [[ ${stackStatus} == "CREATE_COMPLETE" || ${stackStatus} == "CREATE_FAILED"  || ${stackStatus} == "ROLLBACK_IN_PROGRESS" || ${stackStatus} == "ROLLBACK_COMPLETE"  ]];
    then
        echo true
    else
        echo false
    fi
}

function deleteStack(){
    log_info "Deleting the stacks after testing and uploading the logs..."
    declare -A stackData=()
    while IFS= read -r -d '' file; do
        local stackName=$(extractParameters "StackName" ${file})
        local stackRegion=$(extractParameters "Region" ${file})
        stackData[$stackName]=$stackRegion
    done < <(find ${deploymentDirectory} -maxdepth 1 -type f -name '*.json'  -print0 )

    for stackName in "${!stackData[@]}"
    do
        log_info "Checking stack status of ${stackName} before deleting..."
        stackAvailable=$(validateDeleteStack ${stackName} ${stackData[$stackName]})
        if [ "${stackAvailable}" = true ];
        then
            log_info "Stack ${stackName} is available. Proceeding to delete!"
            log_info "Deleting the stack: ${stackName} that is in the ${stackData[$stackName]} region"
            aws cloudformation delete-stack \
            --stack-name ${stackName} \
            --region ${stackData[$stackName]}
            if [[ $? != 0 ]];
            then
                log_error "AWS Stack: ${stackName} deletion failed!"
            else
                local stackDeleted=false
                while [ ${stackDeleted} = false ]
                do
                    stackStatus=$(aws cloudformation describe-stacks --stack-name ${stackName} --region ${stackData[$stackName]} 2> /dev/null | jq -r '.Stacks[0].StackStatus')
                    if [[ "${stackStatus}" == "DELETE_IN_PROGRESS" ]];
                    then
                        log_info "Still deleting the stack ${stackName}"
                        # Test cannot end before deleting the entire stack becase when archiving the logs
                        # the instance logs should be available on the S3 bucket. Therefore sleeping till 
                        # the stack fully gets deleted.
                        sleep 60 #1min sleep
                    else
                        # If command fails that means the stack doesn't exist(already deleted)
                        log_info "Stack ${stackName} deletion completed!"
                        stackDeleted=true
                    fi
                done
            fi
        else
            log_error "Stack ${stackName} that is in ${stackData[$stackName]} region is not available" 
        fi
    done
}

function main(){
    # # Check if test logs are already uploaded
    local s3DirList=$(aws s3 ls ${testLogsUploadLocation}/)
    if [[ "${s3DirList}" == "" ]];
    then
        deleteStack
        if [[ "${testType}" != "intg" ]];
        then
            uploadTestLogs
        fi
    else
        log_info "Logs are already uploaded to S3"
    fi
}

main

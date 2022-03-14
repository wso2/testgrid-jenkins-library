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

deploymentDirectory="${WORKSPACE}/deployment/$1"
parameterFilePath="${deploymentDirectory}/parameters.json"
currentScript=$(dirname $(realpath "$0"))
source ${currentScript}/common-functions.sh

stackName=$(extractParameters "StackName" ${parameterFilePath})
region=$(extractParameters "Region" ${parameterFilePath})
S3OutputBucketLocation=$(extractParameters "S3OutputBucketLocation" ${parameterFilePath})
outputDirectory="${deploymentDirectory}/outputs"
testLogsUploadLocation="s3://${S3OutputBucketLocation}/test-execution-logs"
stackAvailable=false

function uploadTestLogs(){
    echo "The test executer logs will be uploaded to S3 Bucket."
    echo "Upload location: ${testLogsUploadLocation}"
    aws s3 cp ${outputDirectory} ${testLogsUploadLocation} --recursive --quiet
    if [[ $? != 0 ]];
    then
        echo "Error occured when uploading Test executer logs"
    else
        echo "Test executer logs are uploaded successfully!"
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
    echo "Deleting the stacks after testing and uploading the logs..."
    declare -A stackData=()
    while IFS= read -r -d '' file; do
        local stackName=$(extractParameters "StackName" ${file})
        local stackRegion=$(extractParameters "Region" ${file})
        stackData[$stackName]=$stackRegion
    done < <(find ${deploymentDirectory} -maxdepth 1 -type f -name '*.json'  -print0 )

    for stackName in "${!stackData[@]}"
    do
        echo "Checking stack status of ${stackName} before deleting..."
        stackAvailable=$(validateDeleteStack ${stackName} ${stackData[$stackName]})
        if [ "${stackAvailable}" = true ];
        then
            echo "Stack ${stackName} is available. Proceeding to delete!"
            echo "Deleting the stack: ${stackName} that is in the ${stackData[$stackName]} region"
            aws cloudformation delete-stack \
            --stack-name ${stackName} \
            --region ${stackData[$stackName]}
            local stackDeleted=false
            while [ ${stackDeleted} = false ]
            do
                stackStatus=$(aws cloudformation describe-stacks --stack-name ${stackName} --region ${stackData[$stackName]} 2> /dev/null | jq -r '.Stacks[0].StackStatus')
                if [[ "${stackStatus}" == "DELETE_IN_PROGRESS" ]];
                then
                    echo "Still deleting the stack ${stackName}"
                    # Test cannot end before deleting the entire stack becase when archiving the logs
                    # the instance logs should be available on the S3 bucket. Therefore sleeping till 
                    # the stack fully gets deleted.
                    sleep 60 #1min sleep
                else
                    # If command fails that means the stack doesn't exist(already deleted)
                    echo "Stack ${stackName} deletion completed!"
                    stackDeleted=true
                fi
            done
        else
            echo "Stack ${stackName} that is in ${stackData[$stackName]} region is not available" 
        fi
    done
}

function main(){
    # # Check if test logs are already uploaded
    local s3DirList=$(aws s3 ls ${testLogsUploadLocation}/)
    if [[ "${s3DirList}" == "" ]];
    then
        deleteStack
        uploadTestLogs
    else
        echo "Logs are already uploaded to S3"
    fi
}

main

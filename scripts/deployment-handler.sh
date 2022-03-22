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

deploymentName=$1; shift
cloudformationFileLocations=$@

cloudformationFileLocations=$(echo $cloudformationFileLocations | tr -d '[],')
cloudformationFileLocations=(`echo $cloudformationFileLocations | sed 's/,/\n/g'`)
currentScript=$(dirname $(realpath "$0"))

deploymentDirectory="${WORKSPACE}/deployment/${deploymentName}"
parameterFilePath="${deploymentDirectory}/parameters.json"
outputFile="${deploymentDirectory}/deployment.properties"

source ${currentScript}/common-functions.sh
product=$(extractParameters "Product" ${parameterFilePath})

echo "-----------"
echo "Deployment Directory:    "${deploymentDirectory}
echo "CloudFormation Locations: "${cloudformationFileLocations[*]}
echo "-----------"

function cloudformationValidation() {
    ## Validate the CFN file before deploying
    for cloudformationFileLocation in ${cloudformationFileLocations[@]}
    do
        log_info "Validating cloudformation script ${cloudformationFileLocation}!"
        cloudformationResult=$(aws cloudformation validate-template --template-body file://${cloudformationFileLocation})
        if [[ $? != 0 ]];
        then
            log_error "Cloudformation Template Validation failed!"
            bash ${currentScript}/post-actions.sh ${deploymentName}
            exit 1
        else
            log_info "Cloudformation template is valid!"
        fi
    done
}

# The output locations in S3 bucket will be created seperately for each deployment
# Therefore the output location which was written at the beginning will be changed  
function changeCommonLogPath(){
    local s3OutputBucketLocation=$(extractParameters "S3OutputBucketLocation" ${parameterFilePath})
    local stackName=$(extractParameters "StackName" ${parameterFilePath})
    local deployementLogPath="${s3OutputBucketLocation}/${stackName}/test-outputs"
    updateJsonFile "S3OutputBucketLocation" ${deployementLogPath} ${parameterFilePath}
}

function cloudformationDeployment(){
   log_info "Executing product specific deployment..."
   log_info "Running ${product} deployment.."
   bash ${currentScript}/${product}/deploy.sh ${deploymentName} ${cloudformationFileLocations[@]}
}

function writeCommonVariables(){
    extractRequired=$3

    if [[ ${extractRequired} = true ]];
    then
        getVariable=$1
        variableName=$2
        variableValue=$(extractParameters $getVariable ${parameterFilePath})
    else
        variableName=$1
        variableValue=$2
    fi
    outputEntry="${variableName}=${variableValue}"
    echo "${outputEntry}" >> ${outputFile}
}

function addCommonVariables(){
    writeCommonVariables "S3OutputBucketLocation" "S3OutputBucketLocation" true
    writeCommonVariables "ProductVersion" "ProductVersion" true
    writeCommonVariables "S3AccessKeyID" "s3accessKey" true
    writeCommonVariables "S3SecretAccessKey" "s3secretKey" true
}

function main(){
    changeCommonLogPath
    cloudformationValidation
    cloudformationDeployment
    addCommonVariables
}

main

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
testType=$(extractParameters "TestType" ${parameterFilePath})

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
    if [[ ${testType} == "intg"  ]];
    then
        if [[ ${product} == "wso2am" ]];
        then
            bash ${currentScript}/apim/intg/intg-deploy.sh ${deploymentName} ${cloudformationFileLocations[@]}
        else
            bash ${currentScript}/${product}/intg/intg-deploy.sh ${deploymentName} ${cloudformationFileLocations[@]}
        fi
    else
        bash ${currentScript}/${product}/deploy.sh ${deploymentName} ${cloudformationFileLocations[@]}
    fi
    if [[ $? != 0 ]];
    then
        # If deployment fails the handler should also fail
        exit 1
    fi
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
    writeCommonVariables "Product" "Product" true
    writeCommonVariables "ProductVersion" "ProductVersion" true
    writeCommonVariables "WUMUsername" "WUMUsername" true
    writeCommonVariables "WUMPassword" "WUMPassword" true
    writeCommonVariables "GithubUserName" "GithubUserName" true
    writeCommonVariables "GithubPassword" "GithubPassword" true
    writeCommonVariables "ProductRepository" "ProductRepository" true
    writeCommonVariables "ProductTestBranch" "ProductTestBranch" true
    writeCommonVariables "ProductTestScriptLocation" "ProductTestScriptLocation" true
    writeCommonVariables "S3AccessKeyID" "s3accessKey" true
    writeCommonVariables "S3SecretAccessKey" "s3secretKey" true
    writeCommonVariables "TESTGRID_EMAIL_PASSWORD" "testgridEmailPassword" true
    writeCommonVariables "CustomURL" "CustomURL" true
    writeCommonVariables "UpdateType" "UpdateType" true
    writeCommonVariables "TestType" "TestType" true
    writeCommonVariables "SurefireReportDir" "SurefireReportDir" true
}

function main(){
    changeCommonLogPath
    cloudformationValidation
    cloudformationDeployment
    addCommonVariables
}

main

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
cloudformationFileLocations=("$@")

deploymentDirectory="${WORKSPACE}/deployment/${deploymentName}"
parameterFilePath="${deploymentDirectory}/parameters.json"
outputFile="${deploymentDirectory}/deployment.properties"
currentScript=$(dirname $(realpath "$0"))

source ${currentScript}/../common-functions.sh

counter=0
stackName=$(extractParameters "StackName" ${parameterFilePath})
tempStackName=${stackName}
for cloudformationFileLocation in ${cloudformationFileLocations[@]}
do
    parameterFilePath="${deploymentDirectory}/parameters.json"
    # When one deployment depends on several CFNs then the stackname will be appened
    # with an itteration number
    if [[ ${#cloudformationFileLocations[@]} -gt 1 ]];
    then
        stackName="${tempStackName}-${counter}"
    fi
    
    if [[ ${product} == "is" ]];
    then
        if [[ ${cloudformationFileLocation} == *"sample"* ]];
        then
            isSampleVariableGenerator="${deploymentDirectory}/../../parameters/is/is-samples.json"
            isSampleVariableFile="${deploymentDirectory}/is-samples.json"
            cp ${parameterFilePath} ${isSampleVariableFile}
            declare -A replaceArray
            tmp=$(mktemp)
            while IFS="=" read -r key value
            do
                replaceArray[$key]="$value"
                updateJsonFile ${key} ${value} ${isSampleVariableFile}
            done < <(jq -c -r "to_entries|map(\"\(.key)=\(.value)\")|.[]" ${isSampleVariableGenerator})
            parameterFilePath=$isSampleVariableFile
        fi
    fi
    
    region=$(extractParameters "Region" ${parameterFilePath})
    updateJsonFile "StackName" ${stackName} ${parameterFilePath}
    
    log_info "Deploying ${stackName}"
    aws cloudformation deploy \
    --stack-name ${stackName} \
    --template-file $cloudformationFileLocation \
    --parameter-overrides file://${parameterFilePath} \
    --capabilities CAPABILITY_NAMED_IAM \
    --region ${region}

    # When the CFN YAML has issues this will terminate the flow.
    if [[ $? != 0 ]];
    then
        log_error "CloudFormation file errors identified!"
        aws cloudformation describe-stack-events --stack-name ${stackName} --region ${region}
        bash ${currentScript}/../post-actions.sh ${deploymentName}
        exit 1
    fi

    # Add a proper comment  why this if confition was used
    # add another comment to mention other products that needs if conditions
    if [[ ${cloudformationFileLocation} == *"sample"* ]];
    then
        parameterFilePath="${deploymentDirectory}/parameters.json"
    fi

    # When the Deployment has issues this will terminate the flow
    stackStatus=$(aws cloudformation describe-stacks --stack-name ${stackName} --region ${region} | jq -r '.Stacks[0].StackStatus')
    if [[ ${stackStatus} == "CREATE_COMPLETE" ]];
    then
        log_info "Stack:${stackName} creation was successfull!"
    else
        log_error "Stack:${stackName} creation failed! Error:${stackStatus}"
        bash ${currentScript}/../post-actions.sh ${deploymentName}
        exit 1
    fi

    # Get the deployment outputs to an array
    getCfnOutput ${stackName} ${region}

    # When there are multiple deployments then the CFNs are dependant to each other
    # Therefore adding the single outputs to the JSON parameter file as well
    if [[ ${#cloudformationFileLocations[@]} -gt 1 ]];
    then
        ## use the common file to execute the function
        writeJsonFile ${parameterFilePath}
    fi

    writePropertiesFile
    counter=$((counter+1))
done

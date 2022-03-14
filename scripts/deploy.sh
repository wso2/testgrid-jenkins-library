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
        echo "Validating cloudformation script ${cloudformationFileLocation}!"
        cloudformationResult=$(aws cloudformation validate-template --template-body file://${cloudformationFileLocation})
        if [[ $? != 0 ]];
        then
            echo "Cloudformation Template Validation failed!"
            bash ${currentScript}/post-actions.sh ${deploymentName}
            exit 1
        else
            echo "Cloudformation template is valid!"
        fi
    done
}

# The output locations in S3 bucket will be created seperately for each deployment
# Therefore the output location which was written at the beginning will be changed  
function changeCommonLogPath(){
    local S3OutputBucketLocation=$(extractParameters "S3OutputBucketLocation" ${parameterFilePath})
    local stackName=$(extractParameters "StackName" ${parameterFilePath})
    local deployementLogPath="${S3OutputBucketLocation}/${stackName}/test-outputs"
    updateJsonFile "S3OutputBucketLocation" ${deployementLogPath} ${parameterFilePath}
}

function cloudformationDeployment(){
    local counter=0
    local stackName=$(extractParameters "StackName" ${parameterFilePath})
    local tempStackName=${stackName}
    for cloudformationFileLocation in ${cloudformationFileLocations[@]}
    do
        local parameterFilePath="${deploymentDirectory}/parameters.json"
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
                local isSampleVariableGenerator="${deploymentDirectory}/../../parameters/is/is-samples.json"
                local isSampleVariableFile="${deploymentDirectory}/is-samples.json"
                cp ${parameterFilePath} ${isSampleVariableFile}
                declare -A replaceArray
                local tmp=$(mktemp)
                while IFS="=" read -r key value
                do
                    replaceArray[$key]="$value"
                    updateJsonFile ${key} ${value} ${isSampleVariableFile}
                done < <(jq -c -r "to_entries|map(\"\(.key)=\(.value)\")|.[]" ${isSampleVariableGenerator})
                parameterFilePath=$isSampleVariableFile
            fi
        fi
        
        local region=$(extractParameters "Region" ${parameterFilePath})
        updateJsonFile "StackName" ${stackName} ${parameterFilePath}

        echo "Deploying ${stackName}"
        aws cloudformation deploy \
        --stack-name ${stackName} \
        --template-file $cloudformationFileLocation \
        --parameter-overrides file://${parameterFilePath} \
        --capabilities CAPABILITY_NAMED_IAM \
        --region ${region}

        # When the CFN YAML has issues this will terminate the flow.
        if [[ $? != 0 ]];
        then
            echo "CloudFormation file errors identified!"
            bash ${currentScript}/post-actions.sh ${deploymentName}
            exit 1
        fi

        if [[ ${product} == "is" ]];
        then
            if [[ ${cloudformationFileLocation} == *"sample"* ]];
            then
                parameterFilePath="${deploymentDirectory}/parameters.json"
            fi
        fi

        # When the Deployment has issues this will terminate the flow.
        stackStatus=$(aws cloudformation describe-stacks --stack-name ${stackName} --region ${region} | jq -r '.Stacks[0].StackStatus')
        if [[ ${stackStatus} == "CREATE_COMPLETE" ]];
        then
            echo "Stack:${stackName} creation was successfull!"
        else
            echo "Stack:${stackName} creation failed! Error:${stackStatus}"
            bash ${currentScript}/post-actions.sh ${deploymentName}
            exit 1
        fi

        # Get the deployment outputs to an array
        getCfnOutput ${stackName} ${region}
        
        # When there are multiple deployments then the CFNs are dependant to each other
        # Therefore adding the single outputs to the JSON parameter file as well
        if [[ ${#cloudformationFileLocations[@]} -gt 1 ]];
        then
            writeJsonFile ${parameterFilePath}
        fi

        writePropertiesFile
        #bash ${currentScript}/write-parameter-file.sh ${outputKey} ${outputValue} ${writeFile}
        counter=$((counter+1))
    done
}

# Get the output links of the Stack into a file
function getCfnOutput(){
    local stackName=${1}
    local region=${2}
    echo "Getting outputs from deployed stack ${stackName}"
    
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
    for output in "${outputsArray[@]}"; do
        outputKey=$(jq -r '.OutputKey' <<< "$output")
        outputValue=$(jq -r '.OutputValue' <<< "$output")
        outputEntry="${outputKey}=${outputValue}"
        bash ${currentScript}/write-parameter-file.sh ${outputKey} ${outputValue} ${writeFile}
    done
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

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

product=$1
productVersion=$2
updateType=$3

originalParameteFilePath="${WORKSPACE}/parameters/parameters.json"

osArray=(`echo ${os_list} | sed 's/,/\n/g'`)
jdkArray=(`echo ${jdk_list} | sed 's/,/\n/g'`)
dbArray=(`echo ${database_list} | sed 's/,/\n/g'`)

function generateRandomString(){
    tr -dc A-Za-z0-9 </dev/urandom | head -c 8 ; echo ''
}

function removeSpecialCharacters(){
    stringValue=$1
    removedString=$(echo "${stringValue}" | sed 's|[_.,]||g' )
    echo ${removedString}
}

echo "Creating the deployment directories by using infrastructure combination!"
for os in ${osArray[@]}; do
    for jdk in ${jdkArray[@]}; do
        for db in ${dbArray[@]}; do
            deploymentDirName="${product}-${productVersion}-${updateType}-${os}-${jdk}-${db}"
            # Generating a random value to avoid resource duplication
            uniqueIdentifier=$(generateRandomString)

            deploymentDirPath="${WORKSPACE}/deployment/${deploymentDirName}"
            deploymentParameterFilePath="${deploymentDirPath}/parameters.json"
            simplifiedProductVersion=$(removeSpecialCharacters ${productVersion})
            
            stackNamePrefix="prod-${product}${simplifiedProductVersion}-${updateType}"
            stackNameSufix=$(removeSpecialCharacters "${os}-${jdk}-${db}")
            stackName="${stackNamePrefix}-${stackNameSufix}-${uniqueIdentifier}"

            # An individual directory will be created per deployment in each build
            log_info "Creating a directory for ${deploymentDirName} deployment"
            mkdir -p "${deploymentDirPath}"

            # Common paramter file will be copied to the deployment dir
            # Then the OS, JDK and DB will be added individually per deployment 
            cp ${originalParameteFilePath} ${deploymentDirPath}
            ./scripts/write-parameter-file.sh "OperatingSystem" ${os} ${deploymentParameterFilePath}
            ./scripts/write-parameter-file.sh "JDK" ${jdk} ${deploymentParameterFilePath}
            ./scripts/write-parameter-file.sh "DB" ${db} ${deploymentParameterFilePath}
            ./scripts/write-parameter-file.sh "UniqueIdentifier" ${uniqueIdentifier} ${deploymentParameterFilePath}
            ./scripts/write-parameter-file.sh "StackName" ${stackName} ${deploymentParameterFilePath}
        done
    done
done

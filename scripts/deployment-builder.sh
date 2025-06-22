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

product=$1
productVersion=$2
updateType=$3

currentScript=$(dirname $(realpath "$0"))
source ${currentScript}/common-functions.sh
originalParameteFilePath="${WORKSPACE}/parameters/parameters.json"
testType=$(extractParameters "TestType" ${originalParameteFilePath})

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
            echo ${testType}
            if [[ ${testType} == "intg"  ]]; 
            then
                stackNamePrefix="prod-intg-${product}${simplifiedProductVersion}-${updateType}"
            else
                stackNamePrefix="prod-${product}${simplifiedProductVersion}-${updateType}"
            fi
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

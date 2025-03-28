#!groovy
/*
* Copyright (c) 2025 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
* WSO2 Inc. licenses this file to you under the Apache License,
* Version 2.0 (the "License"); you may not use this file except
* in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*
*/

// Input parameters
String product = params.product
String productVersion = params.productVersion
String productDeploymentRegion = params.productDeploymentRegion
String[] osList = params.osList
Strin[]g jdkList = params.jdkList
def databaseList = params.databaseList ?// Each object in databaseList should have db_engine and db_engine_version properties
String albCertArn = params.albCertArn
String productRepository = params.productRepository
String productTestBranch = params.productTestBranch
String productTestScript = params.productTestScript
String surefireReportDir = params.surefireReportDir
String productInstanceType = params.productInstanceType
Boolean useStaging = params.useStaging
Boolean apimPreRelease = params.apimPreRelease
String testGroups = params.testGroups

// Default values
String[] deploymentDirectories = []
String updateType = "u2"
String tfRepoUrl = "https://github.com/kavindasr/iac-aws-wso2-products.git"
String tfRepoBranch = "apim-intg"
String tfDirectory = "iac-aws-wso2-products"



pipeline {
    agent {label 'pipeline-agent'}

    stages {
        stage('Clone Terraform repo') {
            steps {
                script {
                    dir(tfDirectory) {
                        git branch: "${tf_repo_branch}",
                        credentialsId: "WSO2_GITHUB_TOKEN",
                        url: "${tf_repo_url}"
                    }
                }
            }
        }
    }
}
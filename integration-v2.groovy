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
String[] jdkList = params.jdkList
def databaseList = params.databaseList // Each object in databaseList should have dbEngine and dbEngineVersion properties
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
String[] deploymentPatterns = []
String updateType = "u2"
String tfRepoUrl = "https://github.com/kavindasr/iac-aws-wso2-products.git"
String tfRepoBranch = "apim-intg"
String tfDirectory = "iac-aws-wso2-products"

String githubCredentialId = "WSO2_GITHUB_TOKEN"

// Create deployment patterns for all combinations of OS, JDK, and database
void createDeploymentPatterns(String product, String productVersion, 
                                String[] osArray, String[] jdkArray, def databaseList) {
    println "Creating the deployment patterns by using infrastructure combination!"
    
    for (String os : osArray) {
        for (String jdk : jdkArray) {
            for (def db : databaseList) {
                String dbEngine = db.dbEngine
                String dbEngineVersion = db.dbEngineVersion
                String deploymentDirName = "${product}-${productVersion}-${os}-${jdk}-${db_engine}-${db_engine_version}"
                
                def deploymentPattern = [
                    product: product,
                    version: productVersion,
                    os: os,
                    jdk: jdk,
                    dbEngine: dbEngine,
                    dbEngineVersion: dbEngineVersion,
                    directory: deploymentDirName,
                ]

                println "Deployment pattern created: ${deploymentPattern}"

                deploymentPatterns.add(deploymentPattern)
            }
        }
    }
}

pipeline {
    agent {label 'pipeline-agent'}

    stages {
        stage('Clone Terraform repo') {
            steps {
                script {
                    dir(tfDirectory) {
                        git branch: "${tfRepoBranch}",
                        credentialsId: githubCredentialId,
                        url: "${tfRepoUrl}"
                    }
                }
            }
        }

        stage('Preparation') {
            steps {
                createDeploymentPatterns(product, productVersion, osList, jdkList, databaseList)

                println "Deployment patterns created: ${deploymentPatterns}"

            }
        }
    }

    post {
            always {
                println "Job is completed... Deleting the workspace directories!"
                cleanWs deleteDirs: true, notFailBuild: true
            }
        }
}
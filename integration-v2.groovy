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

import groovy.json.JsonOutput

// Input parameters
String product = params.product
String productVersion = params.productVersion
String productDeploymentRegion = params.productDeploymentRegion
String[] osList = params.osList?.split(',') ?: []
String[] jdkList = params.jdkList?.split(',') ?: []
String[] databaseList = params.databaseList?.split(',') ?: []
String albCertArn = params.albCertArn
String productRepository = params.productRepository
String productTestBranch = params.productTestBranch
String productTestScript = params.productTestScript
String surefireReportDir = params.surefireReportDir
String productInstanceType = params.productInstanceType
Boolean useStaging = params.useStaging
Boolean apimPreRelease = params.apimPreRelease
String testGroups = params.testGroups
String tfS3Bucket = params.tfS3Bucket
String tfS3region = params.tfS3Region

// Default values
def deploymentPatterns = []
String updateType = "u2"
String tfRepoUrl = "https://github.com/kavindasr/iac-aws-wso2-products.git"
String tfRepoBranch = "apim-intg"
String tfDirectory = "iac-aws-wso2-products"

String githubCredentialId = "WSO2_GITHUB_TOKEN"
def dbEngineVersions = [
    "aurora-mysql": "8.0.mysql_aurora.3.02.2",
]

// Create deployment patterns for all combinations of OS, JDK, and database
def createDeploymentPatterns(String product, String productVersion, 
                                String[] osList, String[] jdkList, String[] databaseList, def dbEngineVersions, def deploymentPatterns) {
    println "Creating the deployment patterns by using infrastructure combination!"
    
    for (String os : osList) {
        for (String jdk : jdkList) {
            for (String db : databaseList) {
                String dbEngineVersion = dbEngineVersions[db]
                if (dbEngineVersion == null) {
                    println "DB engine version not found for ${db}. Skipping..."
                    continue
                }
                String deploymentDirName = "${product}-${productVersion}-${os}-${jdk}-${db}-${dbEngineVersion}"
                
                def deploymentPattern = [
                    product: product,
                    version: productVersion,
                    os: os,
                    jdk: jdk,
                    dbEngine: db,
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
                script {
                    println "JDK List: ${jdkList}"
                    println "OS List: ${osList}"
                    println "Database List: ${databaseList}"
                    createDeploymentPatterns(product, productVersion, osList, jdkList, databaseList,dbEngineVersions, deploymentPatterns)

                    println "Deployment patterns created: ${deploymentPatterns}"

                    // Create directories for each deployment pattern
                    for (def pattern : deploymentPatterns) {
                        def deploymentDirName = pattern.directory
                        println "Creating directory: ${deploymentDirName}"
                        sh "mkdir -p ${deploymentDirName}"
                        
                        // Copy the Terraform files to the respective directories
                        dir("${deploymentDirName}") {
                            sh "cp -r ../${tfDirectory}/* ."
                        }
                    }
                }
            }
        }

        stage('Terraform Plan') {
            steps {
                script {
                    withCredentials([[
                        $class: 'AmazonWebServicesCredentialsBinding',
                        credentialsId: params.awsCred,
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                    ]]) {
                        
                        for (def pattern : deploymentPatterns) {
                            def deploymentDirName = pattern.directory
                            dir("${deploymentDirName}") {
                                println "Running Terraform plan for ${deploymentDirName}..."
                                sh """
                                    terraform init -backend-config="bucket=${tfS3Bucket}" \
                                        -backend-config="region=${tfS3region}" \
                                        -backend-config="key=${deploymentDirName}.tfstate"
                                    
                                    terraform plan -var="product=${pattern.product}" \
                                        -var="dbEngine=${pattern.dbEngine}" \
                                        -var="dbEngineVersion=${pattern.dbEngineVersion}"
                                """
                            }
                        }
                    }
                }
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

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
String tfS3region = params.tfS3region
String dbPassword = params.dbPassword
String project = params.project?: "wso2"
Boolean onlyDestroyResources = params.onlyDestroyResources
Boolean destroyResources = params.destroyResources

// Default values
def deploymentPatterns = []
String updateType = "u2"
// Terraform repository details
String tfRepoUrl = "https://github.com/kavindasr/iac-aws-wso2-products.git"
String tfRepoBranch = "apim-intg"
String tfDirectory = "iac-aws-wso2-products"
String tfEnvironment = "dev"
// Helm repository details
String helmRepoUrl = "https://github.com/kavindasr/helm-apim.git"
String helmRepoBranch = "apim-intg"
String helmDirectory = "helm-apim"

String githubCredentialId = "WSO2_GITHUB_TOKEN"
def dbEngineVersions = [
    "aurora-mysql": "8.0.mysql_aurora.3.04.0",
    "aurora-postgresql": "16.6",
]

// Create deployment patterns for all combinations of OS, JDK, and database
def createDeploymentPatterns(String product, String productVersion, 
                                String[] osList, String[] jdkList, String[] databaseList, def dbEngineVersions, def deploymentPatterns) {
    println "Creating the deployment patterns by using infrastructure combination!"
    
    int count = 1
    for (String os : osList) {
        for (String jdk : jdkList) {
            def dbEngines = []
            for (String db : databaseList) {
                String dbEngineVersion = dbEngineVersions[db]
                if (dbEngines == null) {
                    println "DB engine version not found for ${db}. Skipping..."
                    continue
                }
                dbEngines.add([
                    engine: db,
                    version: dbEngineVersion
                ])
            }
            String deploymentDirName = "${product}-${productVersion}-${os}-${jdk}"
                
            def deploymentPattern = [
                id: count++,
                product: product,
                version: productVersion,
                os: os,
                jdk: jdk,
                dbEngines: dbEngines,
            ]
            deploymentPatterns.add(deploymentPattern)
        }
    }
}

pipeline {
    agent {label 'pipeline-kubernetes-agent'}

    stages {
        stage('Clone repos') {
            steps {
                script {
                    dir(tfDirectory) {
                        git branch: "${tfRepoBranch}",
                        credentialsId: githubCredentialId,
                        url: "${tfRepoUrl}"
                    }

                    dir(helmDirectory) {
                        git branch: "${helmRepoBranch}",
                        credentialsId: githubCredentialId,
                        url: "${helmRepoUrl}"
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

                    // Install Terraform if not already installed
                    if (!fileExists('/usr/local/bin/terraform')) {
                        println "Terraform not found. Installing..."
                        sh """
                            curl -LO https://releases.hashicorp.com/terraform/1.11.3/terraform_1.11.3_linux_amd64.zip
                            unzip terraform_1.11.3_linux_amd64.zip
                            sudo mv terraform /usr/local/bin/
                            terraform version
                        """
                    } else {
                        println "Terraform is already installed."
                    }
                }
            }
        }

        stage('Terraform Plan') {
            when {
                expression { !onlyDestroyResources }
            }
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
                                    
                                    terraform plan \
                                        -var="project=${project}" \
                                        -var="client_name=dev-${pattern.id}" \
                                        -var="region=${productDeploymentRegion}" \
                                        -var="db_password=${dbPassword}" \
                                        -var="db_engine_options=${pattern.dbEngines}"
                                """
                            }
                        }
                    }
                }
            }
        }

        stage('Terraform Apply') {
            when {
                expression { !onlyDestroyResources }
            }
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
                                println "Running Terraform apply for ${deploymentDirName}..."
                                sh """
                                    terraform apply -auto-approve \
                                        -var="project=${project}" \
                                        -var="client_name=${pattern.id}" \
                                        -var="region=${productDeploymentRegion}" \
                                        -var="db_password=${dbPassword}" \
                                        -var="db_engine_options=${pattern.dbEngines}"
                                """
                                
                                // Capture all outputs as JSON
                                def terraformOutput = sh(script: "terraform output -json", returnStdout: true).trim()
                                // Parse the JSON
                                def jsonSlurper = new groovy.json.JsonSlurper()
                                def terraformOutputJson = jsonSlurper.parseText(terraformOutput)
                                
                                // Extract database writer endpoint
                                def dbWriterEndpoint = terraformOutputJson
                                println "Database Writer Endpoint: ${dbWriterEndpoint}"
                                
                                // Store the outputs in the pattern object for later use
                                pattern.dbEndpoint = dbWriterEndpoint

                            }
                        }
                    }
                }
            }
        }
        stage('Configure EKS cluster') {
            when {
                expression { !onlyDestroyResources }
            }
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
                                println "Configuring EKS for ${deploymentDirName}..."
                                // EKS cluster name follows this pattern defined in the AWS Terraform modules:
                                // https://github.com/wso2/aws-terraform-modules/blob/c9820b842ff2227c10bd22f4ff076461d972d520/modules/aws/EKS-Cluster/eks.tf#L21
                                sh """
                                    aws eks --region ${productDeploymentRegion} \
                                    update-kubeconfig --name ${project}-${pattern.id}-${tfEnvironment}-${productDeploymentRegion}-eks \
                                    --alias ${pattern.directory}
                                """
                            }
                        }
                    }
                }
            }
                                    
        }
        stage('Destroy Cloud Resources') {
            when {
                expression { destroyResources || onlyDestroyResources }
            }
            steps {
                script {
                     withCredentials([[
                        $class: 'AmazonWebServicesCredentialsBinding',
                        credentialsId: params.awsCred,
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                    ]]) { 
                        println "Job is completed... Deleting the workspace directories!"
                        // Destroy the created resources
                        for (def pattern : deploymentPatterns) {
                            def deploymentDirName = pattern.directory
                            dir("${deploymentDirName}") {
                                println "Destroying resources for ${deploymentDirName}..."
                                sh """
                                    terraform destroy -auto-approve \
                                        -var="project=${project}" \
                                        -var="client_name=${pattern.id}" \
                                        -var="region=${productDeploymentRegion}" \
                                        -var="db_password=${dbPassword}" \
                                        -var="db_engine_options=${pattern.dbEngines}"
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
                cleanWs deleteDirs: true, notFailBuild: true
            }
        }
}

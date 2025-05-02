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

import groovy.json.JsonSlurperClassic 

// Input parameters
String product = params.product
String productVersion = params.productVersion
String productDeploymentRegion = params.productDeploymentRegion
String[] osList = params.osList?.split(',')?.collect { it.trim() } ?: []
String[] databaseList = params.databaseList?.split(',')?.collect { it.trim() } ?: []
String albCertArn = params.albCertArn
String acpUpdateLevel = params.acpUpdateLevel?: "-1"
String tmUpdateLevel = params.tmUpdateLevel?: "-1"
String gwUpdateLevel = params.gwUpdateLevel?: "-1"
Boolean useStaging = params.useStaging
String tfS3Bucket = params.tfS3Bucket
String tfS3region = params.tfS3region
String awsCred = params.awsCred
String dbPassword = params.dbPassword
String project = params.project?: "wso2"
Boolean onlyDestroyResources = params.onlyDestroyResources
Boolean destroyResources = params.destroyResources
Boolean skipTfApply = params.skipTfApply
Boolean skipDockerBuild = params.skipDockerBuild
Boolean skipTests = params.skipTests

// Default values
def deploymentPatterns = []
String updateType = "u2"
String hostName = ""
String dbUser = "wso2carbon"
// Helm repository details
String helmRepoUrl = "https://github.com/wso2/helm-apim.git"
String helmRepoBranch = "main"
String helmDirectory = "helm-apim"
// APIM Test Integration repository details
String apimIntgRepoUrl = "https://github.com/kavindasr/apim-test-integration.git"
String apimIntgRepoBranch = "4.5.0-profile-automation"
String apimIntgDirectory = "apim-test-integration"
String tfDirectory = "terraform"
String tfEnvironment = "dev"
String logsDirectory = "logs"

String githubCredentialId = "WSO2_GITHUB_TOKEN"
def dbEngineList = [
    "mysql": [
        version: "5.7",
        dbDriver: "com.mysql.cj.jdbc.Driver",
        driverUrl: "https://repo1.maven.org/maven2/mysql/mysql-connector-java/8.0.29/mysql-connector-java-8.0.29.jar",
        dbType: "mysql",
        port: 3306
        ],
    "postgres": [
        version: "16.6",
        dbDriver: "org.postgresql.Driver",
        driverUrl: "https://repo1.maven.org/maven2/org/postgresql/postgresql/42.3.6/postgresql-42.3.6.jar",
        dbType: "postgresql",
        port: 5432
        ],
]

// Create deployment patterns for all combinations of OS and database
@NonCPS
def createDeploymentPatterns(String project, String product, String productVersion, 
                                String[] osList, String[] databaseList, def dbEngineList, def deploymentPatterns) {
    println "Creating the deployment patterns by using infrastructure combination!"
    
    int count = 1
    for (String os : osList) {
        def dbEngines = []
        for (String db : databaseList) {
            def dbDetails = dbEngineList[db]
            if (dbDetails == null) {
                println "DB engine version not found for ${db}. Skipping..."
                continue
            }
            dbEngines.add([
                engine: db,
                version: dbDetails.version,
                port: dbDetails.port,
            ])
        }
        String deploymentDirName = "${project}-${product}-${productVersion}-${os}"
        
        def dbEnginesJson = new groovy.json.JsonBuilder(dbEngines).toString()
        def deploymentPattern = [
            id: count++,
            product: product,
            version: productVersion,
            os: os,
            dbEngines: dbEngines,
            dbEnginesJson: dbEnginesJson,
            directory: deploymentDirName,
            eksDesiredSize: 5*dbEngines.size(),
        ]
        deploymentPatterns.add(deploymentPattern)
    }
}

def executeDBScripts(String dbEngine, String dbEndpoint, String dbUser, String dbPassword, String scriptPath) {
    println "Executing DB scripts for ${dbEngine} at ${dbEndpoint}..."

    try {
        timeout(time: 5, unit: 'MINUTES') {
            if (dbEngine == "mysql") {
                // Execute MySQL scripts
                println "Executing MySQL scripts..."
                sh """
                    mysql -h ${dbEndpoint} -u ${dbUser} -p$dbPassword -e "DROP DATABASE IF EXISTS shared_db;"
                    mysql -h ${dbEndpoint} -u ${dbUser} -p$dbPassword -e "DROP DATABASE IF EXISTS apim_db;"
                    mysql -h ${dbEndpoint} -u ${dbUser} -p$dbPassword -e "CREATE DATABASE IF NOT EXISTS shared_db CHARACTER SET latin1;"
                    mysql -h ${dbEndpoint} -u ${dbUser} -p$dbPassword -e "CREATE DATABASE IF NOT EXISTS apim_db CHARACTER SET latin1;"
                    mysql -h ${dbEndpoint} -u ${dbUser} -p$dbPassword -Dshared_db < ${scriptPath}/dbscripts/mysql.sql
                    mysql -h ${dbEndpoint} -u ${dbUser} -p$dbPassword -Dapim_db < ${scriptPath}/dbscripts/apimgt/mysql.sql
                """
            } else if (dbEngine == "postgres") {
                // Execute PostgreSQL scripts
                println "Executing PostgreSQL scripts..."
                sh """
                    PGPASSWORD=$dbPassword psql -h ${dbEndpoint} -U ${dbUser} -d postgres -c "DROP DATABASE IF EXISTS shared_db;"
                    PGPASSWORD=$dbPassword psql -h ${dbEndpoint} -U ${dbUser} -d postgres -c "DROP DATABASE IF EXISTS apim_db;"
                    PGPASSWORD=$dbPassword psql -h ${dbEndpoint} -U ${dbUser} -d postgres -c "CREATE DATABASE shared_db;"
                    PGPASSWORD=$dbPassword psql -h ${dbEndpoint} -U ${dbUser} -d postgres -c "CREATE DATABASE apim_db;"
                    PGPASSWORD=$dbPassword psql -h ${dbEndpoint} -U ${dbUser} -d shared_db -f ${scriptPath}/dbscripts/postgresql.sql
                    PGPASSWORD=$dbPassword psql -h ${dbEndpoint} -U ${dbUser} -d apim_db -f ${scriptPath}/dbscripts/apimgt/postgresql.sql
                """
            } else {
                error "Unsupported DB engine: ${dbEngine}"
            }
        }
    } catch (Exception e) {
        error "Database operation timed out or failed: ${e.message}"
    }
}

def buildDockerImage(String project, String product, String productVersion, String os, String updateLevel, String tag, String dbDriverUrl, 
    String dockerRegistry, String dockerRegistryUsername, String dockerRegistryPassword, Boolean useStaging) {
    
    println "Building Docker image for ${product} ${productVersion} on ${os} with update level ${updateLevel} and tag ${tag}..."
    try {
        // Define parameters for the downstream job
        def dockerBuildParameters = [
            [$class: 'StringParameterValue', name: 'project', value: project],
            [$class: 'StringParameterValue', name: 'wso2_product', value: product],
            [$class: 'StringParameterValue', name: 'wso2_product_version', value: productVersion],
            [$class: 'StringParameterValue', name: 'os', value: os],
            [$class: 'StringParameterValue', name: 'update_level', value: updateLevel],
            [$class: 'StringParameterValue', name: 'tag', value: tag],
            [$class: 'StringParameterValue', name: 'docker_registry', value: dockerRegistry],
            [$class: 'StringParameterValue', name: 'docker_registry_username', value: dockerRegistryUsername],
            [$class: 'PasswordParameterValue', name: 'docker_registry_password', value: hudson.util.Secret.fromString(dockerRegistryPassword)],
            [$class: 'StringParameterValue', name: 'db_driver_url', value: dbDriverUrl],
            [$class: 'BooleanParameterValue', name: 'use_staging', value: useStaging],
        ]
        
        // Invoke the downstream build job
        def buildJob = build job: 'U2/Integration-Tests/product-apim/utils/apim-docker-builder', 
            parameters: dockerBuildParameters,
            propagate: true,
            wait: true
            
        println "Docker image build job completed with status: ${buildJob.result}"
        
        if (buildJob.result != 'SUCCESS') {
            error "Docker image build job failed with status: ${buildJob.result}"
        }
        
        return true
    } catch (Exception e) {
        println "Docker image build job failed for OS ${os}: ${e}"
        error "Failed to build Docker image for OS ${os}. Please check the logs for more details."
        return false
    }
}

def installTerraform() {
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

def installKubectl() {
    if (!fileExists('/usr/local/bin/kubectl')) {
        println "kubectl not found. Installing..."
        sh """
            curl -LO https://dl.k8s.io/release/v1.32.0/bin/linux/amd64/kubectl
            sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
            kubectl version --client
        """
    } else {
        println "kubectl is already installed."
    }
}

def installHelm() {
    if (!fileExists('/usr/local/bin/helm')) {
        println "Helm not found. Installing..."
        sh """
            curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
            chmod 700 get_helm.sh
            ./get_helm.sh
            helm version
        """
    } else {
        println "Helm is already installed."
    }
}

def installDocker() {
if (!fileExists('/usr/bin/docker')) {
        println "Docker not found. Installing..."
        sh """
            sudo apt update
            sudo apt install apt-transport-https ca-certificates curl software-properties-common -y
            curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
            sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu focal stable"
            
            sudo apt install docker-ce -y
            
            sudo usermod -aG docker ${USER}
            su - ${USER}
        """
    } else {
        println "Docker is already installed."
    }
}

def installDBClients() {
    println "Checking and installing database client tools if not already installed..."
    if (!fileExists('/usr/bin/mysql')) {
        println "MySQL client not found. Installing..."
        sh """
            sudo apt-get update || echo "Failed to update package list, continuing..."
            sudo apt-get install -y mysql-client
        """
    } else {
        println "MySQL client is already installed."
    }

    if (!fileExists('/usr/bin/psql')) {
        println "PostgreSQL client not found. Installing..."
        sh """
            sudo apt-get update || echo "Failed to update package list, continuing..."
            sudo apt-get install -y postgresql-client
        """
    } else {
        println "PostgreSQL client is already installed."
    }
}

def installNewman() {
    def version = sh(script: 'newman --version || echo "NOT_INSTALLED"', returnStdout: true).trim()
    if (version == 'NOT_INSTALLED') {
        println "Newman not found. Installing..."
        sh """
            # Install newman globally using npm
            npm install -g newman
            
            # Verify newman installation
            newman --version
        """
    } else {
        println "Newman is already installed. Version: ${version}"
    }
}

@NonCPS
def parseJson(String jsonString) {
    return new groovy.json.JsonSlurper().parseText(jsonString)
}

@NonCPS
def stringifyJson(Map jsonMap) {
    return new groovy.json.JsonBuilder(jsonMap).toPrettyString()
}

pipeline {
    agent {label 'pipeline-kubernetes-agent'}

    stages {
        stage('Clone repos') {
            steps {
                script {
                    dir(helmDirectory) {
                        git branch: "${helmRepoBranch}",
                        credentialsId: githubCredentialId,
                        url: "${helmRepoUrl}"
                    }
                    dir(apimIntgDirectory) {
                        git branch: "${apimIntgRepoBranch}",
                        credentialsId: githubCredentialId,
                        url: "${apimIntgRepoUrl}"
                    }
                }
            }
        }

        stage('Preparation') {
            steps {
                script {
                    println "OS List: ${osList}"
                    println "Database List: ${databaseList}"
                    createDeploymentPatterns(project, product, productVersion, osList, databaseList, dbEngineList, deploymentPatterns)

                    println "Deployment patterns created: ${deploymentPatterns}"

                    // Create directories for each deployment pattern
                    for (def pattern : deploymentPatterns) {
                        def deploymentDirName = pattern.directory
                        println "Creating directory: ${deploymentDirName}"
                        sh "mkdir -p ${deploymentDirName}"
                        
                        // Copy the Terraform files to the respective directories
                        dir("${deploymentDirName}") {
                            sh "cp -r ../${apimIntgDirectory}/${tfDirectory}/* ."
                        }
                    }

                    // Install Terraform if not already installed
                    installTerraform()
                    // Install Docker if not already installed
                    installDocker()
                    // Install kubectl if not already installed
                    installKubectl()
                    // Install Helm if not already installed
                    installHelm()
                    // Install database client tools
                    installDBClients()
                    // Install Newman if not already installed
                    // installNewman()
                }
            }
        }

        stage('Terraform Init') {
            steps {
                script {
                    withCredentials([[
                        $class: 'AmazonWebServicesCredentialsBinding',
                        credentialsId: awsCred,
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                    ]]) { 
                        for (def pattern : deploymentPatterns) {
                            def deploymentDirName = pattern.directory
                            dir("${deploymentDirName}") {
                                println "Running Terraform init for ${deploymentDirName}..."
                                sh """
                                    terraform init -backend-config="bucket=${tfS3Bucket}" \
                                        -backend-config="region=${tfS3region}" \
                                        -backend-config="key=${deploymentDirName}.tfstate"
                                """
                            }
                        }
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
                        credentialsId: awsCred,
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                    ]]) { 
                        for (def pattern : deploymentPatterns) {
                            def deploymentDirName = pattern.directory
                            dir("${deploymentDirName}") {
                                println "Running Terraform plan for ${deploymentDirName}..."
                                sh """
                                    terraform plan \
                                        -var="project=${project}" \
                                        -var="client_name=${pattern.id}" \
                                        -var="region=${productDeploymentRegion}" \
                                        -var='db_engine_options=${pattern.dbEnginesJson}' \
                                        -var="db_password=$dbPassword" \
                                        -var="eks_default_nodepool_desired_size=${pattern.eksDesiredSize}" \
                                        -no-color
                                """
                            }
                        }
                    }
                }
            }
        }

        stage('Terraform Apply') {
            when {
                expression { !onlyDestroyResources && !skipTfApply }
            }
            steps {
                script {
                    try {
                        withCredentials([[
                            $class: 'AmazonWebServicesCredentialsBinding',
                            credentialsId: awsCred,
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
                                            -var='db_engine_options=${pattern.dbEnginesJson}' \
                                            -var="db_password=$dbPassword" \
                                            -var="eks_default_nodepool_desired_size=${pattern.eksDesiredSize}" \
                                            -no-color
                                    """
                                }
                            }
                        }
                    } catch (Exception e) {
                        println "Terraform apply failed: ${e}"
                        error "Terraform apply failed. Please check the logs for more details."
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
                    try {
                        withCredentials([[
                            $class: 'AmazonWebServicesCredentialsBinding',
                            credentialsId: awsCred,
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

                                        # Install nginx ingress controller
                                        kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.0.4/deploy/static/provider/aws/deploy.yaml || { echo "failed to install nginx ingress controller." ; exit 1 ; }

                                        # Delete Nginx admission if it exists.
                                        kubectl delete -A ValidatingWebhookConfiguration ingress-nginx-admission || echo "WARNING : Failed to delete nginx admission."

                                        # Wait for nginx to come alive.
                                        kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=480s ||  { echo 'Nginx service is not ready within the expected time limit.';  exit 1; }
                                    """

                                    hostName = sh(script: "kubectl -n ingress-nginx get svc ingress-nginx-controller -o json | jq -r '.status.loadBalancer.ingress[0].hostname'", returnStdout: true).trim()
                                    println "Ingress Host Name: ${hostName}"
                                    pattern.hostName = hostName

                                    def ecrWso2AcpURL = sh(script: "terraform output -json | jq -r '.ecr_wso2am_acp_url.value'", returnStdout: true).trim()
                                    def ecrCommonURL = ecrWso2AcpURL.split('/')[0]
                                    println "ECR Common URL: ${ecrCommonURL}"

                                    def password = sh(
                                        script: "aws ecr get-login-password --region ${productDeploymentRegion}",
                                        returnStdout: true
                                    ).trim()
                                    pattern.dockerRegistry = [
                                        registry: ecrCommonURL,
                                        username: "AWS",
                                        password: password
                                    ]
                                }
                            }
                        }
                    } catch (Exception e) {
                        println "EKS configuration failed: ${e}"
                        error "EKS configuration failed. Please check the logs for more details."
                    }
                }
            }                        
        }

        stage('Build docker images') {
            when {
                expression { !onlyDestroyResources && !skipDockerBuild }
            }
            steps {
                script {
                    // Create a map of parallel builds - one for each OS
                    def parallelBuilds = [:]
                    
                    for (def pattern : deploymentPatterns) {
                        for (def dbEngine : pattern.dbEngines) {
                            // Need to bind the os variable within the closure
                            def currentOs = pattern.os
                            def db = dbEngine.engine
                            def dbDriverUrl = dbEngineList[db].driverUrl
                            def dockerRegistry = pattern.dockerRegistry.registry
                            def dockerRegistryUsername = pattern.dockerRegistry.username
                            def dockerRegistryPassword = pattern.dockerRegistry.password
                            
                            parallelBuilds["Build ${currentOs}-${db} wso2am-acp image"] = {
                                buildDockerImage(project, "wso2am-acp", '4.5.0', currentOs, acpUpdateLevel, "${db}-latest", dbDriverUrl, dockerRegistry, dockerRegistryUsername, dockerRegistryPassword, useStaging)
                            }
                            parallelBuilds["Build ${currentOs}-${db} wso2am-tm image"] = {
                                buildDockerImage(project, "wso2am-tm", '4.5.0', currentOs, tmUpdateLevel, "${db}-latest", dbDriverUrl, dockerRegistry, dockerRegistryUsername, dockerRegistryPassword, useStaging)
                            }
                            parallelBuilds["Build ${currentOs}-${db} wso2am-universal-gw image"] = {
                                buildDockerImage(project, "wso2am-universal-gw", '4.5.0', currentOs, gwUpdateLevel, "${db}-latest", dbDriverUrl, dockerRegistry, dockerRegistryUsername, dockerRegistryPassword, useStaging)
                            }
                        }
                    }
                    
                    // Execute all builds in parallel
                    parallel parallelBuilds
                }
            }
        }

        stage('Prepare Deployment and Test') {
            steps {
                script {
                    if (onlyDestroyResources) {
                        echo "Skipping deployment because onlyDestroyResources is set to true"
                        return
                    }
                    
                    // Create a map of parallel deployment tasks
                    def parallelDeployments = [:]
                    
                    // Add each deployment as a parallel task
                    for (def pattern : deploymentPatterns) {
                        def patternDir = pattern.directory
                        for (def dbEngine : pattern.dbEngines) {
                            def dbEngineName = dbEngine.engine
                            
                            // We need to use variables that are safe for the closure
                            def patternDirSafe = patternDir
                            def dbEngineNameSafe = dbEngineName
                            def patternSafe = pattern
                            def dockerRegistrySafe = pattern.dockerRegistry.registry
                            def dockerRegistryUsernameSafe = pattern.dockerRegistry.username
                            def dockerRegistryPasswordSafe = pattern.dockerRegistry.password
                            def stageId = "${patternDirSafe}-${dbEngineNameSafe}"
                            
                            // Add deployment task to parallel map
                            parallelDeployments["Deploy ${stageId}"] = {
                                stage("Deploy ${stageId}") {
                                    try {
                                        withCredentials([
                                            [
                                                $class: 'AmazonWebServicesCredentialsBinding',
                                                credentialsId: awsCred,
                                                accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                                                secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                                            ]
                                        ]) {
                                            String pwd = sh(script: "pwd", returnStdout: true).trim()
                                            // Login to Docker registry
                                            sh """
                                                echo ${dockerRegistryPasswordSafe} | sudo docker login ${dockerRegistrySafe} --username ${dockerRegistryUsernameSafe} --password-stdin
                                            """

                                            dir("${patternDirSafe}") {
                                                def dbWriterEndpointsJson = sh(script: "terraform output -json | jq -r '.database_writer_endpoints.value'", returnStdout: true).trim()
                                                def dbWriterEndpoints = new groovy.json.JsonSlurperClassic().parseText(dbWriterEndpointsJson)
                                                if (!dbWriterEndpoints) {
                                                    error "DB Writer Endpoints are null or empty for ${patternDirSafe}. Please check the Terraform output."
                                                }
                                                println "DB Writer Endpoints: ${dbWriterEndpoints}"
                                                // Convert LazyMap to HashMap
                                                patternSafe.dbEndpoints = new HashMap<>(dbWriterEndpoints)

                                                def (endpoint, dbPort) = patternSafe.dbEndpoints["${dbEngineNameSafe}-${dbEngineList[dbEngineNameSafe].version}"]?.tokenize(':')
                                                def namespace = "${patternSafe.id}-${dbEngineNameSafe}"
                                                sh """
                                                    # Change context
                                                    kubectl config use-context ${patternDirSafe}

                                                    # Create a namespace for the deployment
                                                    kubectl create namespace ${namespace} || echo "Namespace ${namespace} already exists."

                                                    aws s3 cp --quiet s3://${tfS3Bucket}/tools/client-truststore.jks .
                                                    aws s3 cp --quiet s3://${tfS3Bucket}/tools/wso2carbon.jks .

                                                    # Create apim-keystore-secret
                                                    kubectl create secret generic apim-keystore-secret --from-file=wso2carbon.jks --from-file=client-truststore.jks -n ${namespace} || echo "Failed to create apim-keystore-secret."
                                                """
                                                println "Namespace created: ${namespace}"

                                                sh """
                                                # Delete existing release if it exists
                                                helm list -n ${namespace} -q | xargs -n1 -I{} helm uninstall {} -n ${namespace} || echo "Failed to delete existing release."
                                                """

                                                String wso2amAcpImageDigest = sh(script: "aws ecr describe-images --repository-name ${project}-wso2am-acp --query 'imageDetails[?contains(imageTags, `${dbEngineNameSafe}-latest`)].imageDigest' --region ${productDeploymentRegion} --output text", returnStdout: true).trim()
                                                String wso2amTmImageDigest = sh(script: "aws ecr describe-images --repository-name ${project}-wso2am-tm --query 'imageDetails[?contains(imageTags, `${dbEngineNameSafe}-latest`)].imageDigest' --region ${productDeploymentRegion} --output text", returnStdout: true).trim()
                                                String wso2amGwImageDigest = sh(script: "aws ecr describe-images --repository-name ${project}-wso2am-universal-gw --query 'imageDetails[?contains(imageTags, `${dbEngineNameSafe}-latest`)].imageDigest' --region ${productDeploymentRegion} --output text", returnStdout: true).trim()

                                                sleep 60

                                                // Execute DB scripts
                                                executeDBScripts(dbEngineNameSafe, endpoint, dbUser, dbPassword, "${pwd}/${apimIntgDirectory}")

                                                String helmChartPath = "${pwd}/${helmDirectory}"
                                                // Install the product using Helm
                                                sh """
                                                    # Helm-apim does not have a ingress to expose gateway REST API. So we need to create a ingress resource to expose the REST API.
                                                    helm install apim-ing ${pwd}/${apimIntgDirectory}/kubernetes/gw-ingress \
                                                        --set hostname=gw-${dbEngineNameSafe}.wso2.com \
                                                        --namespace ${namespace}
                                                    
                                                    # Deploy wso2am-acp
                                                    echo "Deploying WSO2 API Manager - API Control Plane in ${namespace} namespace..."
                                                    helm install apim-acp ${helmChartPath}/distributed/control-plane \
                                                        --namespace ${namespace} \
                                                        --set aws.enabled=false \
                                                        --set wso2.apim.configurations.adminUsername="admin" \
                                                        --set wso2.apim.configurations.adminPassword="admin" \
                                                        --set wso2.apim.configurations.security.keystores.primary.password="wso2carbon" \
                                                        --set wso2.apim.configurations.security.keystores.primary.keyPassword="wso2carbon" \
                                                        --set wso2.apim.configurations.security.keystores.tls.password="wso2carbon" \
                                                        --set wso2.apim.configurations.security.keystores.tls.keyPassword="wso2carbon" \
                                                        --set wso2.apim.configurations.security.keystores.internal.password="wso2carbon" \
                                                        --set wso2.apim.configurations.security.keystores.internal.keyPassword="wso2carbon" \
                                                        --set wso2.apim.configurations.security.truststore.password="wso2carbon" \
                                                        --set wso2.deployment.resources.requests.cpu="1000m" \
                                                        --set wso2.apim.configurations.userStore.properties.ReadGroups=true \
                                                        --set kubernetes.ingress.controlPlane.hostname="am-${dbEngineNameSafe}.wso2.com" \
                                                        --set wso2.apim.configurations.gateway.environments[0].name="Default" \
                                                        --set wso2.apim.configurations.gateway.environments[0].type="hybrid" \
                                                        --set wso2.apim.configurations.gateway.environments[0].gatewayType="Regular" \
                                                        --set wso2.apim.configurations.gateway.environments[0].provider="wso2" \
                                                        --set wso2.apim.configurations.gateway.environments[0].displayInApiConsole=true \
                                                        --set wso2.apim.configurations.gateway.environments[0].description="This is a hybrid gateway that handles both production and sandbox token traffic." \
                                                        --set wso2.apim.configurations.gateway.environments[0].showAsTokenEndpointUrl=true \
                                                        --set wso2.apim.configurations.gateway.environments[0].serviceName="apim-universal-gw-wso2am-universal-gw-service" \
                                                        --set wso2.apim.configurations.gateway.environments[0].servicePort=9443 \
                                                        --set wso2.apim.configurations.gateway.environments[0].wsHostname="websocket-${dbEngineNameSafe}.wso2.com" \
                                                        --set wso2.apim.configurations.gateway.environments[0].httpHostname="gw-${dbEngineNameSafe}.wso2.com" \
                                                        --set wso2.apim.configurations.gateway.environments[0].websubHostname="websub-${dbEngineNameSafe}.wso2.com" \
                                                        --set wso2.apim.configurations.devportal.enableApplicationSharing=true \
                                                        --set wso2.apim.configurations.devportal.applicationSharingType="default" \
                                                        --set wso2.apim.configurations.oauth_config.oauth2JWKSUrl="https://apim-acp-wso2am-acp-service:9443/oauth2/jwks" \
                                                        --set wso2.deployment.image.registry="${dockerRegistrySafe}" \
                                                        --set wso2.deployment.image.repository="${project}-wso2am-acp:${dbEngineNameSafe}-latest" \
                                                        --set wso2.deployment.image.digest="${wso2amAcpImageDigest}" \
                                                        --set wso2.deployment.image.imagePullSecrets.enabled=true \
                                                        --set wso2.deployment.image.imagePullSecrets.username="${dockerRegistryUsernameSafe}" \
                                                        --set wso2.deployment.image.imagePullSecrets.password="${dockerRegistryPasswordSafe}" \
                                                        --set wso2.apim.configurations.databases.type="${dbEngineList[dbEngineNameSafe].dbType}" \
                                                        --set wso2.apim.configurations.databases.jdbc.driver="${dbEngineList[dbEngineNameSafe].dbDriver}" \
                                                        --set wso2.apim.configurations.databases.apim_db.url="jdbc:${dbEngineList[dbEngineNameSafe].dbType}://${endpoint}:${dbPort}/apim_db?useSSL=false" \
                                                        --set wso2.apim.configurations.databases.apim_db.username="${dbUser}" \
                                                        --set wso2.apim.configurations.databases.apim_db.password="${dbPassword}" \
                                                        --set wso2.apim.configurations.databases.shared_db.url="jdbc:${dbEngineList[dbEngineNameSafe].dbType}://${endpoint}:${dbPort}/shared_db?useSSL=false" \
                                                        --set wso2.apim.configurations.databases.shared_db.username="${dbUser}" \
                                                        --set wso2.apim.configurations.databases.shared_db.password="${dbPassword}"
                                                    
                                                    # Wait for the deployment to be ready
                                                    kubectl wait --for=condition=available --timeout=400s deployment/apim-acp-wso2am-acp-deployment-1 -n ${namespace}

                                                    # Deploy wso2am-tm
                                                    echo "Deploying WSO2 API Manager - Traffic Manager in ${namespace} namespace..."
                                                    helm install apim-tm ${helmChartPath}/distributed/traffic-manager \
                                                        --namespace ${namespace} \
                                                        --set aws.enabled=false \
                                                        --set wso2.apim.configurations.adminUsername="admin" \
                                                        --set wso2.apim.configurations.adminPassword="admin" \
                                                        --set wso2.apim.configurations.security.keystores.primary.password="wso2carbon" \
                                                        --set wso2.apim.configurations.security.keystores.primary.keyPassword="wso2carbon" \
                                                        --set wso2.apim.configurations.security.keystores.tls.password="wso2carbon" \
                                                        --set wso2.apim.configurations.security.keystores.tls.keyPassword="wso2carbon" \
                                                        --set wso2.apim.configurations.security.keystores.internal.password="wso2carbon" \
                                                        --set wso2.apim.configurations.security.keystores.internal.keyPassword="wso2carbon" \
                                                        --set wso2.apim.configurations.security.truststore.password="wso2carbon" \
                                                        --set wso2.deployment.resources.requests.cpu="1000m" \
                                                        --set wso2.apim.configurations.km.serviceUrl="apim-acp-wso2am-acp-service" \
                                                        --set wso2.apim.configurations.throttling.serviceUrl="apim-tm-wso2am-tm-service" \
                                                        --set wso2.apim.configurations.throttling.urls="{apim-tm-wso2am-tm-1-service,apim-tm-wso2am-tm-2-service}" \
                                                        --set wso2.apim.configurations.eventhub.enabled=true \
                                                        --set wso2.apim.configurations.eventhub.serviceUrl="apim-acp-wso2am-acp-service" \
                                                        --set wso2.apim.configurations.eventhub.urls="{apim-acp-wso2am-acp-1-service,apim-acp-wso2am-acp-2-service}" \
                                                        --set wso2.deployment.replicas=1 \
                                                        --set wso2.deployment.minReplicas=1 \
                                                        --set wso2.deployment.image.registry="${dockerRegistrySafe}" \
                                                        --set wso2.deployment.image.repository="${project}-wso2am-tm:${dbEngineNameSafe}-latest" \
                                                        --set wso2.deployment.image.digest=${wso2amTmImageDigest} \
                                                        --set wso2.deployment.image.imagePullSecrets.enabled=true \
                                                        --set wso2.deployment.image.imagePullSecrets.username="${dockerRegistryUsernameSafe}" \
                                                        --set wso2.deployment.image.imagePullSecrets.password="${dockerRegistryPasswordSafe}" \
                                                        --set wso2.apim.configurations.databases.type="${dbEngineList[dbEngineNameSafe].dbType}" \
                                                        --set wso2.apim.configurations.databases.jdbc.driver="${dbEngineList[dbEngineNameSafe].dbDriver}" \
                                                        --set wso2.apim.configurations.databases.apim_db.url="jdbc:${dbEngineList[dbEngineNameSafe].dbType}://${endpoint}:${dbPort}/apim_db?useSSL=false" \
                                                        --set wso2.apim.configurations.databases.apim_db.username="${dbUser}" \
                                                        --set wso2.apim.configurations.databases.apim_db.password="${dbPassword}" \
                                                        --set wso2.apim.configurations.databases.shared_db.url="jdbc:${dbEngineList[dbEngineNameSafe].dbType}://${endpoint}:${dbPort}/shared_db?useSSL=false" \
                                                        --set wso2.apim.configurations.databases.shared_db.username="${dbUser}" \
                                                        --set wso2.apim.configurations.databases.shared_db.password="${dbPassword}"

                                                    # Wait for the deployment to be ready
                                                    kubectl wait --for=condition=available --timeout=400s deployment/apim-tm-wso2am-tm-deployment-1 -n ${namespace}

                                                    # Deploy wso2am-gw
                                                    echo "Deploying WSO2 API Manager - Gateway in ${namespace} namespace..."
                                                    helm install apim-universal-gw ${helmChartPath}/distributed/gateway \
                                                        --namespace ${namespace} \
                                                        --set aws.enabled=false \
                                                        --set wso2.apim.configurations.adminUsername="admin" \
                                                        --set wso2.apim.configurations.adminPassword="admin" \
                                                        --set wso2.apim.configurations.security.keystores.primary.password="wso2carbon" \
                                                        --set wso2.apim.configurations.security.keystores.primary.keyPassword="wso2carbon" \
                                                        --set wso2.apim.configurations.security.keystores.tls.password="wso2carbon" \
                                                        --set wso2.apim.configurations.security.keystores.tls.keyPassword="wso2carbon" \
                                                        --set wso2.apim.configurations.security.keystores.internal.password="wso2carbon" \
                                                        --set wso2.apim.configurations.security.keystores.internal.keyPassword="wso2carbon" \
                                                        --set wso2.apim.configurations.security.truststore.password="wso2carbon" \
                                                        --set wso2.deployment.resources.requests.cpu="1000m" \
                                                        --set kubernetes.ingress.gateway.hostname="gw-${dbEngineNameSafe}.wso2.com" \
                                                        --set kubernetes.ingress.websocket.hostname="websocket-${dbEngineNameSafe}.wso2.com" \
                                                        --set kubernetes.ingress.websub.hostname="websub-${dbEngineNameSafe}.wso2.com" \
                                                        --set wso2.apim.configurations.km.serviceUrl="apim-acp-wso2am-acp-service" \
                                                        --set wso2.apim.configurations.throttling.serviceUrl="apim-tm-wso2am-tm-service" \
                                                        --set wso2.apim.configurations.throttling.urls="{apim-tm-wso2am-tm-1-service,apim-tm-wso2am-tm-2-service}" \
                                                        --set wso2.apim.configurations.eventhub.enabled=true \
                                                        --set wso2.apim.configurations.eventhub.serviceUrl="apim-acp-wso2am-acp-service" \
                                                        --set wso2.apim.configurations.eventhub.urls="{apim-acp-wso2am-acp-1-service,apim-acp-wso2am-acp-2-service}" \
                                                        --set wso2.deployment.image.registry="${dockerRegistrySafe}" \
                                                        --set wso2.deployment.image.repository="${project}-wso2am-universal-gw:${dbEngineNameSafe}-latest" \
                                                        --set wso2.deployment.image.digest=${wso2amGwImageDigest} \
                                                        --set wso2.deployment.image.imagePullSecrets.enabled=true \
                                                        --set wso2.deployment.image.imagePullSecrets.username="${dockerRegistryUsernameSafe}" \
                                                        --set wso2.deployment.image.imagePullSecrets.password="${dockerRegistryPasswordSafe}" \
                                                        --set wso2.apim.configurations.databases.type="${dbEngineList[dbEngineNameSafe].dbType}" \
                                                        --set wso2.apim.configurations.databases.jdbc.driver="${dbEngineList[dbEngineNameSafe].dbDriver}" \
                                                        --set wso2.apim.configurations.databases.shared_db.url="jdbc:${dbEngineList[dbEngineNameSafe].dbType}://${endpoint}:${dbPort}/shared_db?useSSL=false" \
                                                        --set wso2.apim.configurations.databases.shared_db.username="${dbUser}" \
                                                        --set wso2.apim.configurations.databases.shared_db.password="${dbPassword}" \
                                                        --set wso2.deployment.replicas=1 \
                                                        --set wso2.deployment.minReplicas=1
                                                    
                                                    # Wait for the deployment to be ready
                                                    kubectl wait --for=condition=ready --timeout=300s pod -l deployment=apim-universal-gw-wso2am-universal-gw -n ${namespace}
                                                """
                                            }
                                        }
                                    } catch (Exception e) {
                                        println "Deployment failed for ${patternDirSafe}-${dbEngineNameSafe}: ${e}"
                                        error "Deployment failed for ${patternDirSafe}-${dbEngineNameSafe}. Please check the logs for more details."
                                    }
                                }

                                stage("Test ${stageId}") {
                                    if (skipTests) {
                                        echo "Skipping tests for ${stageId} as skipTests is set to true."
                                        return
                                    }
                                    try {
                                        withCredentials([
                                            [
                                                $class: 'AmazonWebServicesCredentialsBinding',
                                                credentialsId: awsCred,
                                                accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                                                secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                                            ]
                                        ]) {
                                            String namespace = "${patternSafe.id}-${dbEngineNameSafe}"
                                            dir("${apimIntgDirectory}") {
                                                sh """
                                                    # Change context
                                                    kubectl config use-context ${patternDirSafe}
                                                """

                                                echo "Waiting 60 seconds before proceeding with tests for ${patternDirSafe} with ${dbEngineNameSafe}..."
                                                sleep 60

                                                sh """
                                                    ./main.sh --HOSTNAME="${patternSafe.hostName}" \
                                                        --PORTAL_HOST="am-${dbEngineNameSafe}.wso2.com" \
                                                        --GATEWAY_HOST="gw-${dbEngineNameSafe}.wso2.com" \
                                                        --kubernetes_namespace="${namespace}"
                                                """
                                            }

                                            dir("${logsDirectory}") {
                                                def podNames = sh(
                                                    script: "kubectl get pods -l product=apim -n=${namespace} -o custom-columns=:metadata.name",
                                                    returnStdout: true
                                                ).trim().split('\n')
                                                println "APIM pods in namespace ${namespace}: ${podNames}"
                                                for (def podName : podNames) {
                                                    if (podName?.trim()) {
                                                        def logFile = "${dbEngineNameSafe}-${podName}.log"
                                                        sh """
                                                            kubectl logs ${podName} -n=${namespace} > ${logFile} || echo "Failed to get logs for pod ${podName}"
                                                        """
                                                    }
                                                }
                                                sh "ls -la"
                                            }
                                        }
                                    } catch (Exception e) {
                                        println "Test execution failed for ${patternDirSafe}-${dbEngineNameSafe}: ${e}"
                                        error "Test execution failed for ${patternDirSafe}-${dbEngineNameSafe}. Please check the logs for more details."
                                    }
                                }
                            }
                        }
                    }
                    
                    // Run all stages in parallel
                    parallel parallelDeployments
                }
            }
        }
    }

    post {
        always {
            script {
                try {
                    println "Cleaning up the workspace..."
                    if (destroyResources || onlyDestroyResources) {
                        withCredentials([[
                            $class: 'AmazonWebServicesCredentialsBinding',
                            credentialsId: awsCred,
                            accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                            secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                        ]]) { 
                            println "Destroying cloud resources!"
                            // Destroy the created resources
                            for (def pattern : deploymentPatterns) {
                                def deploymentDirName = pattern.directory
                                dir("${deploymentDirName}") {
                                    println "Destroying resources for ${deploymentDirName}..."
                                    sh """
                                        # Configure EKS cluster
                                        aws eks --region ${productDeploymentRegion} \
                                        update-kubeconfig --name ${project}-${pattern.id}-${tfEnvironment}-${productDeploymentRegion}-eks \
                                        --alias ${pattern.directory} || echo "Failed to update kubeconfig."

                                        kubectl delete -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.0.4/deploy/static/provider/aws/deploy.yaml || echo "Failed to delete ingress controller."

                                        kubectl wait --namespace ingress-nginx --for=delete pod --selector=app.kubernetes.io/component=controller --timeout=480s || echo "Ingress controller pods were not deleted within the expected time limit."

                                        terraform destroy -auto-approve \
                                            -var="project=${project}" \
                                            -var="client_name=${pattern.id}" \
                                            -var="region=${productDeploymentRegion}" \
                                            -var='db_engine_options=${pattern.dbEnginesJson}' \
                                            -var="db_password=$dbPassword" \
                                            -var="eks_default_nodepool_desired_size=${pattern.eksDesiredSize}" \
                                            -no-color
                                    """
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    echo "Workspace cleanup failed: ${e.message}"
                    currentBuild.result = 'FAILURE'
                } finally {
                    if (!onlyDestroyResources && !skipTests) {
                        archiveArtifacts artifacts: "${logsDirectory}/**/*.*", fingerprint: true
                    }
                    // Clean up the workspace
                    cleanWs()
                }
            }
        }
    }
}

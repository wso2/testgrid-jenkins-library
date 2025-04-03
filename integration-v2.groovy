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
String dockerRegistry = params.dockerRegistry?: "docker.io"
Boolean onlyDestroyResources = params.onlyDestroyResources
Boolean destroyResources = params.destroyResources
Boolean skipTfApply = params.skipTfApply

// Default values
def deploymentPatterns = []
String updateType = "u2"
String hostName = ""
String dbUser = "wso2carbon"
// Terraform repository details
String tfRepoUrl = "https://github.com/kavindasr/iac-aws-wso2-products.git"
String tfRepoBranch = "apim-intg"
String tfDirectory = "iac-aws-wso2-products"
String tfEnvironment = "dev"
// Helm repository details
String helmRepoUrl = "https://github.com/kavindasr/helm-apim.git"
String helmRepoBranch = "apim-intg"
String helmDirectory = "helm-apim"
// APIM Test Integration repository details
String apimIntgRepoUrl = "https://github.com/kavindasr/apim-test-integration.git"
String apimIntgRepoBranch = "4.5.0-profile-automation"
String apimIntgDirectory = "apim-test-integration"

String githubCredentialId = "WSO2_GITHUB_TOKEN"
def dbEngineList = [
    "aurora-mysql": [
        version: "8.0.mysql_aurora.3.04.0",
        dbDriver: "com.mysql.cj.jdbc.Driver",
        driverUrl: "https://repo1.maven.org/maven2/mysql/mysql-connector-java/8.0.29/mysql-connector-java-8.0.29.jar",
        dbType: "mysql"
        ],
    "aurora-postgresql": [
        version: "16.6",
        dbDriver: "org.postgresql.Driver",
        driverUrl: "https://repo1.maven.org/maven2/org/postgresql/postgresql/42.3.6/postgresql-42.3.6.jar",
        dbType: "postgresql"
    ],
]

// Create deployment patterns for all combinations of OS, JDK, and database
@NonCPS
def createDeploymentPatterns(String product, String productVersion, 
                                String[] osList, String[] jdkList, String[] databaseList, def dbEngineList, def deploymentPatterns) {
    println "Creating the deployment patterns by using infrastructure combination!"
    
    int count = 1
    for (String os : osList) {
        for (String jdk : jdkList) {
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
                ])
            }
            String deploymentDirName = "${product}-${productVersion}-${os}-${jdk}"
            // String dbEnginesJson = dbEngines.collect { "{ \"engine\": \"${it.engine}\", \"version\": \"${it.version}\" }" }.join(", ")
            // dbEnginesJson = "[${dbEnginesJson}]"
            def deploymentPattern = [
                id: count++,
                product: product,
                version: productVersion,
                os: os,
                jdk: jdk,
                dbEngines: dbEngines,
                directory: deploymentDirName,
            ]
            deploymentPatterns.add(deploymentPattern)
        }
    }
}

def executeDBScripts(String dbEngine, String dbEndpoint, String dbUser, String dbPassword, String scriptPath) {
    println "Executing DB scripts for ${dbEngine} at ${dbEndpoint}..."

    if (dbEngine == "aurora-mysql") {
        // Execute MySQL scripts
        println "Executing MySQL scripts..."
        sh """
            mysql -h ${dbEndpoint} -u ${dbUser} -p$dbPassword -e "CREATE DATABASE IF NOT EXISTS shared_db CHARACTER SET latin1;"
            mysql -h ${dbEndpoint} -u ${dbUser} -p$dbPassword -e "CREATE DATABASE IF NOT EXISTS apim_db CHARACTER SET latin1;"
            mysql -h ${dbEndpoint} -u ${dbUser} -p$dbPassword -Dshared_db < ${scriptPath}/dbscripts/mysql.sql
            mysql -h ${dbEndpoint} -u ${dbUser} -p$dbPassword -Dapim_db < ${scriptPath}/dbscripts/apimgt/mysql.sql
        """
    } else if (dbEngine == "aurora-postgresql") {
        // Execute PostgreSQL scripts
        println "Executing PostgreSQL scripts..."
        sh """
            PGPASSWORD=$dbPassword psql -h ${dbEndpoint} -U ${dbUser} -d postgres -c "CREATE DATABASE shared_db ENCODING 'LATIN1';"
            PGPASSWORD=$dbPassword psql -h ${dbEndpoint} -U ${dbUser} -d postgres -c "CREATE DATABASE apim_db ENCODING 'LATIN1';"
            PGPASSWORD=$dbPassword psql -h ${dbEndpoint} -U ${dbUser} -d shared_db -f ${scriptPath}/dbscripts/postgresql.sql
            PGPASSWORD=$dbPassword psql -h ${dbEndpoint} -U ${dbUser} -d apim_db -f ${scriptPath}/dbscripts/apimgt/postgresql.sql
        """
    } else {
        error "Unsupported DB engine: ${dbEngine}"
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

pipeline {
    agent {label 'pipeline-agent'}

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
                    println "JDK List: ${jdkList}"
                    println "OS List: ${osList}"
                    println "Database List: ${databaseList}"
                    createDeploymentPatterns(product, productVersion, osList, jdkList, databaseList, dbEngineList, deploymentPatterns)

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
                    installTerraform()
                    // Install Docker if not already installed
                    installDocker()
                    // Install kubectl if not already installed
                    installKubectl()
                    // Install Helm if not already installed
                    installHelm()
                    // Install database client tools
                    installDBClients()
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
                                    
                                    terraform plan \
                                        -var="project=${project}" \
                                        -var="client_name=${pattern.id}" \
                                        -var="region=${productDeploymentRegion}" \
                                        -var="db_password=$dbPassword"
                                """
                            }
                        }
                    }
                }
            }
        }

        stage('Terraform Apply') {
            when {
                allOf {
                    expression { !onlyDestroyResources }
                    expression { !skipTfApply }
                }
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
                                        -var="db_password=$dbPassword"
                                """
                                
                                def dbWriterEndpointsJson = sh(script: "terraform output -json | jq -r '.database_writer_endpoints.value'", returnStdout: true).trim()
                                def dbWriterEndpoints = new groovy.json.JsonSlurper().parseText(dbWriterEndpointsJson)
                                if (!dbWriterEndpoints) {
                                    error "DB Writer Endpoints are null or empty for ${deploymentDirName}. Please check the Terraform output."
                                }
                                println "DB Writer Endpoints: ${dbWriterEndpoints}"
                                // Convert LazyMap to HashMap
                                pattern.dbEndpoints = new HashMap<>(dbWriterEndpoints)

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
                            }
                        }
                    }
                }
            }                        
        }
        stage('Deploy the cluster') {
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
                        String pwd = sh(script: "pwd", returnStdout: true).trim()
                        for (def pattern : deploymentPatterns) {
                                def deploymentDirName = pattern.directory
                                dir("${deploymentDirName}") {
                                    pattern.dbEngines.eachWithIndex { dbEngine, index ->
                                        String dbEngineName = dbEngine.engine
                                        String endpoint = pattern.dbEndpoints["${dbEngineName}-${dbEngineList[dbEngineName].version}"]
                                        def namespace = "${pattern.id}-${dbEngineName}"
                                        sh """
                                            # Change context
                                            kubectl config use-context ${pattern.directory}

                                            # Create a namespace for the deployment
                                            kubectl create namespace ${namespace} || echo "Namespace ${namespace} already exists."
                                        """
                                        println "Namespace created: ${namespace}"

                                        // Execute DB scripts
                                        try {
                                            println "Listing files in the current directory..."
                                            sh "ls -al"
                                            executeDBScripts(dbEngineName, endpoint, dbUser, dbPassword, "${pwd}/${apimIntgDirectory}")
                                        } catch (Exception e) {
                                            // Improvement: Handle each DB engine in seperate stages
                                            println "Error executing DB scripts: ${e.message}"
                                            continue
                                        }

                                        dir("${helmDirectory}") {
                                            // Install the product using Helm
                                            String currentPath = sh(script: "pwd", returnStdout: true).trim()
                                            sh """
                                                # Deploy wso2am-acp
                                                echo "Deploying WSO2 API Manager - API Control Plane in ${namespace} namespace..."
                                                helm install apim-acp ${currentPath}/distributed/control-plane \
                                                    --namespace ${namespace} \
                                                    --set wso2.deployment.image.registry=${dockerRegistry} \
                                                    --set wso2.deployment.image.repository=kavindasr/wso2am-gw:rc2 \
                                                    --set wso2.apim.configurations.databases.type=${dbEngineList[dbEngineName].dbType} \
                                                    --set wso2.apim.configurations.databases.jdbc.driver=${dbEngineList[dbEngineName].dbDriver} \
                                                    --set wso2.apim.configurations.databases.apim_db.url=jdbc:${dbEngineList[dbEngineName].dbType}://${endpoint}:3306/apim_db \
                                                    --set wso2.apim.configurations.databases.apim_db.username=${dbUser} \
                                                    --set wso2.apim.configurations.databases.apim_db.password=${dbPassword} \
                                                    --set wso2.apim.configurations.databases.shared_db.url=jdbc:${dbEngineList[dbEngineName].dbType}://${endpoint}:3306/shared_db \
                                                    --set wso2.apim.configurations.databases.shared_db.username=${dbUser} \
                                                    --set wso2.apim.configurations.databases.shared_db.password=${dbPassword}
                                            """
                                            
                                    }
                                }
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
                                        -var="db_password=$dbPassword"
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

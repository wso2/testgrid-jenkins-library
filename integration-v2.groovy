#!groovy
/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
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
String apimPackS3Bucket = params.apimPackS3Bucket
String dockerRepoBranch = params.dockerRepoBranch ?: "4.7.x"
String helmRepoBranch = params.helmRepoBranch ?: "4.7.x"
Boolean onlyDestroyResources = params.onlyDestroyResources
Boolean destroyResources = params.destroyResources
Boolean skipTfApply = params.skipTfApply
Boolean skipDockerBuild = params.skipDockerBuild
Boolean skipTests = params.skipTests
Boolean skipUpdate = params.skipUpdate ?: false
Boolean skipPeerTest = params.skipPeerTest
String encryptionKey = params.encryptionKey ?: ""

// Default values
def deploymentPatterns = []
// ============================================================================
// PEER TEST DEPLOYMENT PATTERNS
// ============================================================================
// skipPeerTest=false (default): All 4 peer test patterns run in parallel:
//   Pattern 1: All components on staging (latest update)
//   Pattern 2: Only ACP on staging, TM and GW on GA
//   Pattern 3: Only TM on staging, ACP and GW on GA
//   Pattern 4: Only GW on staging, ACP and TM on GA
// skipPeerTest=true: A single custom pattern is used.
// ============================================================================
def peerTestPatterns = []
int nodesPerNamespace = 5

String dbUser = "wso2carbon"
// Helm repository details
String helmRepoUrl = "https://github.com/wso2/helm-apim.git"
String helmDirectory = "helm-apim"
// APIM Test Integration repository details
String apimIntgRepoUrl = "https://github.com/IsuruGunarathne/apim-test-integration.git"
String apimIntgRepoBranch = "${productVersion}-profile-automation"
String apimIntgDirectory = "apim-test-integration"
String tfDirectory = "terraform"
String tfEnvironment = "dev"
String collectedLogsDirectory = "collected-logs"
String logsDirectory = "logs"
String diagnosticsDirectory = "diagnostics"
String apimPackDirectory = "wso2am"

String githubCredentialId = "WSO2_GITHUB_TOKEN"
def dbEngineList = [
    "mysql": [
        version: "8.0.43",
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
                                String[] osList, String[] databaseList, def dbEngineList, def deploymentPatterns,
                                int nodesPerNamespace, int peerTestPatternCount) {
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
            // Scale EKS nodes to handle all peer test patterns running in parallel
            eksDesiredSize: nodesPerNamespace * dbEngines.size() * peerTestPatternCount,
        ]
        deploymentPatterns.add(deploymentPattern)
    }
}

def getDbNames(String dbSuffix) {
    String sharedDbName = dbSuffix ? "shared_db_${dbSuffix}" : "shared_db"
    String apimDbName   = dbSuffix ? "apim_db_${dbSuffix}"   : "apim_db"
    return [sharedDbName, apimDbName]
}

def validateShellSafeValue(String fieldName, String fieldValue) {
    if (!fieldValue || !(fieldValue ==~ /^[A-Za-z0-9][A-Za-z0-9._-]*$/)) {
        error "Invalid value for ${fieldName}: '${fieldValue}'. Allowed characters: letters, digits, dot, dash, underscore."
    }
}

def validatePipelineInputs(String project, String product, String productVersion, String productDeploymentRegion,
                           String[] osList, String[] databaseList) {
    validateShellSafeValue("project", project)
    validateShellSafeValue("product", product)
    validateShellSafeValue("productVersion", productVersion)
    validateShellSafeValue("productDeploymentRegion", productDeploymentRegion)

    if (!osList || osList.length == 0) {
        error "osList cannot be empty."
    }
    for (String os : osList) {
        validateShellSafeValue("osList entry", os)
    }

    if (!databaseList || databaseList.length == 0) {
        error "databaseList cannot be empty."
    }
    for (String db : databaseList) {
        validateShellSafeValue("databaseList entry", db)
    }
}

@NonCPS
def resolvePeerTestPatterns(Boolean skipPeerTest) {
    if (!skipPeerTest) {
        return [
            [name: "all-staging", acpVariant: "latest", tmVariant: "latest", gwVariant: "latest"],
            [name: "acp-staging", acpVariant: "latest", tmVariant: "ga",     gwVariant: "ga"],
            [name: "tm-staging",  acpVariant: "ga",     tmVariant: "latest", gwVariant: "ga"],
            [name: "gw-staging",  acpVariant: "ga",     tmVariant: "ga",     gwVariant: "latest"],
        ]
    }
    return [
        [name: "custom", acpVariant: "latest", tmVariant: "latest", gwVariant: "latest"],
    ]
}

/**
 * Wait for the DCR (Dynamic Client Registration) endpoint to become ready.
 * Sends a real POST with Basic auth (admin:admin) and an empty JSON body.
 * A fully initialized DCR returns 400 (bad request — missing required fields).
 * A still-initializing DCR returns 500; a connection failure returns 000.
 * HTTP 400 and 201 are treated as "ready" (both prove the webapp is
 * initialized). Other 4xx (404, 405) indicate a misconfigured route or
 * method and are not accepted.
 *
 * @param hostName   The ingress/service hostname to connect to.
 * @param portalHost The Host header value for the request.
 * @param maxAttempts Maximum number of retry attempts (default 45).
 * @param waitSeconds Seconds to wait between attempts (default 10).
 * @param consecutiveSuccesses Number of consecutive successful checks required to mark as ready (default 3).
 */
def waitForDcrEndpoint(String hostName, String portalHost, int maxAttempts = 45, int waitSeconds = 10, int consecutiveSuccesses = 3) {
    sh """#!/bin/bash
        success_streak=0
        STATUS=000
        for i in \$(seq 1 ${maxAttempts}); do
            STATUS=\$(curl -s -o /dev/null -w "%{http_code}" -k --connect-timeout 10 --max-time 30 \\
                -X POST \\
                -H "Host: ${portalHost}" \\
                -H "Content-Type: application/json" \\
                -H "Authorization: Basic YWRtaW46YWRtaW4=" \\
                -d '{}' \\
                https://${hostName}/client-registration/v0.17/register)
            echo "Readiness Check \$i: DCR endpoint returned HTTP \$STATUS"
            if [[ "\$STATUS" == "400" || "\$STATUS" == "201" ]]; then
                success_streak=\$((success_streak + 1))
                echo "DCR readiness streak: \$success_streak/${consecutiveSuccesses}"
                if [[ \$success_streak -ge ${consecutiveSuccesses} ]]; then
                    echo "DCR endpoint is stable and ready (HTTP \$STATUS)! Proceeding..."
                    break
                fi
            else
                success_streak=0
                echo "DCR endpoint not ready yet (HTTP \$STATUS). Waiting ${waitSeconds}s..."
            fi
            sleep ${waitSeconds}
        done

        if [[ \$success_streak -lt ${consecutiveSuccesses} ]]; then
            echo "ERROR: DCR endpoint did not reach ${consecutiveSuccesses} consecutive ready checks after ${maxAttempts} attempts. Last HTTP status: \$STATUS"
            exit 1
        fi
    """
}

/**
 * Wait for the Publisher REST API to become ready.
 * Sends an unauthenticated GET to the Publisher APIs listing endpoint.
 * A ready Publisher returns 401 (Unauthorized). A still-initializing
 * webapp returns 500 or 000. Only 200, 401, and 403 are accepted as
 * "ready"; 404 (route not registered) and 302 (redirect) are not.
 *
 * @param hostName   The ingress/service hostname to connect to.
 * @param portalHost The Host header value for the request.
 * @param maxAttempts Maximum number of retry attempts (default 45).
 * @param waitSeconds Seconds to wait between attempts (default 10).
 * @param consecutiveSuccesses Number of consecutive successful checks required to mark as ready (default 3).
 */
def waitForPublisherApi(String hostName, String portalHost, int maxAttempts = 45, int waitSeconds = 10, int consecutiveSuccesses = 3) {
    sh """#!/bin/bash
        success_streak=0
        STATUS=000
        for i in \$(seq 1 ${maxAttempts}); do
            STATUS=\$(curl -s -o /dev/null -w "%{http_code}" -k --connect-timeout 10 --max-time 30 -H "Host: ${portalHost}" https://${hostName}/api/am/publisher/v4/apis)
            echo "Readiness Check \$i: Publisher API returned HTTP \$STATUS"
            if [[ "\$STATUS" =~ ^(200|401|403)\$ ]]; then
                success_streak=\$((success_streak + 1))
                echo "Publisher readiness streak: \$success_streak/${consecutiveSuccesses}"
                if [[ \$success_streak -ge ${consecutiveSuccesses} ]]; then
                    echo "Publisher API is stable and ready (HTTP \$STATUS)! Proceeding..."
                    break
                fi
            else
                success_streak=0
                echo "Publisher API not ready yet (HTTP \$STATUS). Waiting ${waitSeconds}s..."
            fi
            sleep ${waitSeconds}
        done

        if [[ \$success_streak -lt ${consecutiveSuccesses} ]]; then
            echo "ERROR: Publisher API did not reach ${consecutiveSuccesses} consecutive ready checks after ${maxAttempts} attempts. Last HTTP status: \$STATUS"
            exit 1
        fi
    """
}

/**
 * Wait for the Gateway REST API to become ready.
 * Sends an unauthenticated GET to the Gateway APIs listing endpoint
 * through the gateway ingress hostname. A ready Gateway returns 401
 * (Unauthorized) or 200. A still-initializing Gateway returns 500 or
 * 000 (connection refused). Only 200, 401, and 403 are accepted as
 * "ready"; 404 (route not registered) and 302 (redirect) are not.
 *
 * This check is critical for parallel peer-test runs where resource
 * contention can delay Gateway pod startup compared to ACP pods.
 * Without it, Newman tests may hit a Gateway that is not yet serving
 * traffic, causing spurious 404 failures on API invocations.
 *
 * @param hostName   The ingress/service hostname (ELB) to connect to.
 * @param gwHost     The Gateway Host header value for the request.
 * @param maxAttempts Maximum number of retry attempts (default 45).
 * @param waitSeconds Seconds to wait between attempts (default 10).
 * @param consecutiveSuccesses Number of consecutive successful checks required to mark as ready (default 3).
 */
def waitForGatewayApi(String hostName, String gwHost, int maxAttempts = 45, int waitSeconds = 10, int consecutiveSuccesses = 3) {
    sh """#!/bin/bash
        success_streak=0
        STATUS=000
        for i in \$(seq 1 ${maxAttempts}); do
            STATUS=\$(curl -s -o /dev/null -w "%{http_code}" -k --connect-timeout 10 --max-time 30 -H "Host: ${gwHost}" https://${hostName}/api/am/gateway/v2/apis)
            echo "Readiness Check \$i: Gateway API returned HTTP \$STATUS"
            if [[ "\$STATUS" =~ ^(200|401|403)\$ ]]; then
                success_streak=\$((success_streak + 1))
                echo "Gateway readiness streak: \$success_streak/${consecutiveSuccesses}"
                if [[ \$success_streak -ge ${consecutiveSuccesses} ]]; then
                    echo "Gateway API is stable and ready (HTTP \$STATUS)! Proceeding..."
                    break
                fi
            else
                success_streak=0
                echo "Gateway API not ready yet (HTTP \$STATUS). Waiting ${waitSeconds}s..."
            fi
            sleep ${waitSeconds}
        done

        if [[ \$success_streak -lt ${consecutiveSuccesses} ]]; then
            echo "ERROR: Gateway API did not reach ${consecutiveSuccesses} consecutive ready checks after ${maxAttempts} attempts. Last HTTP status: \$STATUS"
            exit 1
        fi
    """
}

/**
 * Wait until APIM pods are stable (all running/ready and no restart-count changes).
 * This reduces flaky test starts when pods are still settling after rollout.
 *
 * @param kubeContext Kubernetes context name.
 * @param namespace   Namespace that contains APIM pods.
 * @param maxAttempts Max loop attempts (default 40).
 * @param waitSeconds Sleep between attempts in seconds (default 15).
 * @param requiredStableIterations Number of consecutive stable iterations (default 6).
 */
def waitForApimPodStability(String kubeContext, String namespace, int maxAttempts = 40, int waitSeconds = 15, int requiredStableIterations = 6) {
    sh """#!/bin/bash
        set -euo pipefail
        stable_count=0
        prev_restarts=""

        for i in \$(seq 1 ${maxAttempts}); do
            pod_snapshot=\$(kubectl --context=${kubeContext} get pods -n ${namespace} -l product=apim --no-headers || true)
            total_count=\$(echo "\$pod_snapshot" | awk 'NF>0 {c++} END {print c+0}')
            ready_count=\$(echo "\$pod_snapshot" | awk '\$2 == "1/1" && \$3 == "Running" {c++} END {print c+0}')
            restart_snapshot=\$(kubectl --context=${kubeContext} get pods -n ${namespace} -l product=apim -o jsonpath='{range .items[*]}{.metadata.name}:{range .status.containerStatuses[*]}{.restartCount}{","}{end}{"\\n"}{end}' | sort || true)

            echo "APIM stability check \$i: ready=\${ready_count}/\${total_count}"
            echo "Restart snapshot: \${restart_snapshot}"

            if [[ "\$total_count" -ge 6 && "\$ready_count" -eq "\$total_count" && "\$restart_snapshot" == "\$prev_restarts" ]]; then
                stable_count=\$((stable_count + 1))
                echo "Stable iteration streak: \${stable_count}/${requiredStableIterations}"
            else
                stable_count=0
                prev_restarts="\$restart_snapshot"
                echo "Pods not stable yet. Waiting ${waitSeconds}s..."
            fi

            if [[ \$stable_count -ge ${requiredStableIterations} ]]; then
                echo "APIM pods are stable in namespace ${namespace}."
                exit 0
            fi

            sleep ${waitSeconds}
        done

        echo "ERROR: APIM pods did not reach a stable state in namespace ${namespace}."
        kubectl --context=${kubeContext} get pods -n ${namespace} -l product=apim -o wide || true
        exit 1
    """
}

/**
 * Execute DB scripts to create and initialise databases.
 * @param dbSuffix  Suffix for unique DB names per test pattern (e.g. "all_staging").
 *                  Use empty string "" for single-pattern / custom mode (names stay shared_db, apim_db).
 */
def executeDBScripts(String dbEngine, String dbEndpoint, String dbUser, String dbPassword, String scriptPath, String dbSuffix) {
    def (sharedDbName, apimDbName) = getDbNames(dbSuffix)
    println "Executing DB scripts for ${dbEngine} at ${dbEndpoint} (databases: ${sharedDbName}, ${apimDbName})..."

    try {
        timeout(time: 5, unit: 'MINUTES') {
            if (dbEngine == "mysql") {
                // Execute MySQL scripts
                println "Executing MySQL scripts..."
                sh """
                    mysql -h ${dbEndpoint} -u ${dbUser} -p$dbPassword -e "DROP DATABASE IF EXISTS ${sharedDbName};"
                    mysql -h ${dbEndpoint} -u ${dbUser} -p$dbPassword -e "DROP DATABASE IF EXISTS ${apimDbName};"
                    mysql -h ${dbEndpoint} -u ${dbUser} -p$dbPassword -e "CREATE DATABASE IF NOT EXISTS ${sharedDbName} CHARACTER SET latin1;"
                    mysql -h ${dbEndpoint} -u ${dbUser} -p$dbPassword -e "CREATE DATABASE IF NOT EXISTS ${apimDbName} CHARACTER SET latin1;"
                    mysql -h ${dbEndpoint} -u ${dbUser} -p$dbPassword -D${sharedDbName} < ${scriptPath}/dbscripts/mysql.sql
                    mysql -h ${dbEndpoint} -u ${dbUser} -p$dbPassword -D${apimDbName} < ${scriptPath}/dbscripts/apimgt/mysql.sql
                """
            } else if (dbEngine == "postgres") {
                // Execute PostgreSQL scripts
                println "Executing PostgreSQL scripts..."
                sh """
                    PGPASSWORD=$dbPassword psql -h ${dbEndpoint} -U ${dbUser} -d postgres -c "DROP DATABASE IF EXISTS ${sharedDbName};"
                    PGPASSWORD=$dbPassword psql -h ${dbEndpoint} -U ${dbUser} -d postgres -c "DROP DATABASE IF EXISTS ${apimDbName};"
                    PGPASSWORD=$dbPassword psql -h ${dbEndpoint} -U ${dbUser} -d postgres -c "CREATE DATABASE ${sharedDbName};"
                    PGPASSWORD=$dbPassword psql -h ${dbEndpoint} -U ${dbUser} -d postgres -c "CREATE DATABASE ${apimDbName};"
                    PGPASSWORD=$dbPassword psql -h ${dbEndpoint} -U ${dbUser} -d ${sharedDbName} -f ${scriptPath}/dbscripts/postgresql.sql
                    PGPASSWORD=$dbPassword psql -h ${dbEndpoint} -U ${dbUser} -d ${apimDbName} -f ${scriptPath}/dbscripts/apimgt/postgresql.sql
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
    String dockerRegistry, String dockerRegistryUsername, String dockerRegistryPassword, Boolean useStaging, Boolean skipUpdate) {
    
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
            [$class: 'StringParameterValue', name: 'docker_apim_branch', value: dockerRepoBranch],
            [$class: 'BooleanParameterValue', name: 'use_staging', value: useStaging],
            [$class: 'BooleanParameterValue', name: 'skip_update', value: skipUpdate],
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

def collectApimLogs(String kubeContext, String namespace, String outputDir, String logPrefix) {
    def kubeContextQuoted = shellQuote(kubeContext)
    def namespaceQuoted = shellQuote(namespace)
    def outputDirQuoted = shellQuote(outputDir)
    def logPrefixQuoted = shellQuote(logPrefix)

    try {
        sh """#!/bin/bash
            set +e
            kube_context=${kubeContextQuoted}
            namespace=${namespaceQuoted}
            output_dir=${outputDirQuoted}
            log_prefix=${logPrefixQuoted}
            mkdir -p "\${output_dir}"

            if ! kubectl config get-contexts "\${kube_context}" >/dev/null 2>&1; then
                echo "Kubernetes context \${kube_context} not found. Skipping APIM log collection." > "\${output_dir}/\${log_prefix}-log-collection.txt"
                exit 0
            fi

            ns_check_output="\$(kubectl --context="\${kube_context}" get namespace "\${namespace}" 2>&1)"
            ns_check_exit=\$?
            if [[ \$ns_check_exit -ne 0 ]]; then
                if [[ "\$ns_check_output" == *"(NotFound)"* || "\$ns_check_output" == *"not found"* ]]; then
                    echo "Namespace \${namespace} not found. Skipping APIM log collection." > "\${output_dir}/\${log_prefix}-log-collection.txt"
                else
                    {
                        echo "Failed to access namespace \${namespace}. Skipping APIM log collection."
                        echo "\$ns_check_output"
                    } > "\${output_dir}/\${log_prefix}-log-collection.txt"
                fi
                exit 0
            fi

            kubectl --context="\${kube_context}" get pods -n "\${namespace}" -o wide > "\${output_dir}/\${log_prefix}-pods-wide.txt" || true
            kubectl --context="\${kube_context}" get events -n "\${namespace}" --sort-by=.metadata.creationTimestamp > "\${output_dir}/\${log_prefix}-events.txt" || true

            mapfile -t pods < <(kubectl --context="\${kube_context}" get pods -n "\${namespace}" -l product=apim -o custom-columns=:metadata.name --no-headers 2>/dev/null || true)
            if [[ \${#pods[@]} -eq 0 ]]; then
                echo "No APIM pods found in namespace \${namespace}." > "\${output_dir}/\${log_prefix}-log-collection.txt"
                exit 0
            fi

            for pod in "\${pods[@]}"; do
                [[ -z "\${pod}" ]] && continue
                kubectl --context="\${kube_context}" describe pod "\${pod}" -n "\${namespace}" > "\${output_dir}/\${log_prefix}-\${pod}.describe.txt" || true
                kubectl --context="\${kube_context}" logs "\${pod}" -n "\${namespace}" > "\${output_dir}/\${log_prefix}-\${pod}.log" || true
                kubectl --context="\${kube_context}" logs "\${pod}" -n "\${namespace}" --previous > "\${output_dir}/\${log_prefix}-\${pod}.previous.log" || true
            done
        """
    } catch (Exception e) {
        println "Failed to collect APIM logs for namespace ${namespace}: ${e.message}"
    }
}

def shellQuote(String value) {
    if (value == null) {
        return "''"
    }
    return "'" + value.replace("'", "'\"'\"'") + "'"
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
                    validatePipelineInputs(project, product, productVersion, productDeploymentRegion, osList, databaseList)
                    peerTestPatterns = resolvePeerTestPatterns(skipPeerTest)

                    println "OS List: ${osList}"
                    println "Database List: ${databaseList}"
                    println "Skip Peer Test: ${skipPeerTest}"
                    println "Peer Test Patterns: ${peerTestPatterns.collect { it.name }}"
                    println "Update Levels — ACP: ${acpUpdateLevel}, TM: ${tmUpdateLevel}, GW: ${gwUpdateLevel}"
                    createDeploymentPatterns(project, product, productVersion, osList, databaseList, dbEngineList, deploymentPatterns,
                                            nodesPerNamespace, peerTestPatterns.size())

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

                                        # Scale Nginx to handle parallel test traffic from multiple
                                        # peer-test patterns hitting the same ELB concurrently.
                                        kubectl -n ingress-nginx scale deployment ingress-nginx-controller --replicas=4

                                        # Delete Nginx admission if it exists.
                                        kubectl delete -A ValidatingWebhookConfiguration ingress-nginx-admission || echo "WARNING : Failed to delete nginx admission."

                                        # Wait for nginx to come alive.
                                        kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=480s ||  { echo 'Nginx service is not ready within the expected time limit.';  exit 1; }

                                        # Install Kubernetes Gateway API CRDs required by helm-apim 4.7.x
                                        kubectl apply -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.1.0/standard-install.yaml
                                    """

                                    def hostName = sh(script: "kubectl -n ingress-nginx get svc ingress-nginx-controller -o json | jq -r '.status.loadBalancer.ingress[0].hostname'", returnStdout: true).trim()
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
                            
                            if (!skipPeerTest) {
                                // ============================================================
                                // PEER TEST MODE: Build 6 unique images per DB
                                //   3 apps x 2 variants (latest, ga)
                                //   Tags: {db}-latest  (updated version, user-provided update levels)
                                //         {db}-ga      (initial version, update level 0)
                                // ============================================================
                                // Build "latest" variant — updated version with user-provided update levels
                                parallelBuilds["Build ${currentOs}-${db}-latest wso2am-acp"] = {
                                    buildDockerImage(project, "wso2am-acp", productVersion, currentOs, acpUpdateLevel, "${db}-latest", dbDriverUrl, dockerRegistry, dockerRegistryUsername, dockerRegistryPassword, useStaging, skipUpdate)
                                }
                                parallelBuilds["Build ${currentOs}-${db}-latest wso2am-tm"] = {
                                    buildDockerImage(project, "wso2am-tm", productVersion, currentOs, tmUpdateLevel, "${db}-latest", dbDriverUrl, dockerRegistry, dockerRegistryUsername, dockerRegistryPassword, useStaging, skipUpdate)
                                }
                                parallelBuilds["Build ${currentOs}-${db}-latest wso2am-universal-gw"] = {
                                    buildDockerImage(project, "wso2am-universal-gw", productVersion, currentOs, gwUpdateLevel, "${db}-latest", dbDriverUrl, dockerRegistry, dockerRegistryUsername, dockerRegistryPassword, useStaging, skipUpdate)
                                }
                                // Build "ga" variant — initial version (update level 0, no updates applied)
                                parallelBuilds["Build ${currentOs}-${db}-ga wso2am-acp"] = {
                                    buildDockerImage(project, "wso2am-acp", productVersion, currentOs, "0", "${db}-ga", dbDriverUrl, dockerRegistry, dockerRegistryUsername, dockerRegistryPassword, useStaging, false)
                                }
                                parallelBuilds["Build ${currentOs}-${db}-ga wso2am-tm"] = {
                                    buildDockerImage(project, "wso2am-tm", productVersion, currentOs, "0", "${db}-ga", dbDriverUrl, dockerRegistry, dockerRegistryUsername, dockerRegistryPassword, useStaging, false)
                                }
                                parallelBuilds["Build ${currentOs}-${db}-ga wso2am-universal-gw"] = {
                                    buildDockerImage(project, "wso2am-universal-gw", productVersion, currentOs, "0", "${db}-ga", dbDriverUrl, dockerRegistry, dockerRegistryUsername, dockerRegistryPassword, useStaging, false)
                                }
                            } else {
                                // ============================================================
                                // CUSTOM MODE: Build 3 images using user-provided update levels
                                //   Tag: {db}-latest (same as original v2 behaviour)
                                // ============================================================
                                parallelBuilds["Build ${currentOs}-${db} wso2am-acp image"] = {
                                    buildDockerImage(project, "wso2am-acp", productVersion, currentOs, acpUpdateLevel, "${db}-latest", dbDriverUrl, dockerRegistry, dockerRegistryUsername, dockerRegistryPassword, useStaging, skipUpdate)
                                }
                                parallelBuilds["Build ${currentOs}-${db} wso2am-tm image"] = {
                                    buildDockerImage(project, "wso2am-tm", productVersion, currentOs, tmUpdateLevel, "${db}-latest", dbDriverUrl, dockerRegistry, dockerRegistryUsername, dockerRegistryPassword, useStaging, skipUpdate)
                                }
                                parallelBuilds["Build ${currentOs}-${db} wso2am-universal-gw image"] = {
                                    buildDockerImage(project, "wso2am-universal-gw", productVersion, currentOs, gwUpdateLevel, "${db}-latest", dbDriverUrl, dockerRegistry, dockerRegistryUsername, dockerRegistryPassword, useStaging, skipUpdate)
                                }
                            }
                        }
                    }
                    
                    println "Total docker builds: ${parallelBuilds.size()} (skipPeerTest=${skipPeerTest})"
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
                    
                    // Download APIM pack once (only needed for DB scripts and keystores)
                    withCredentials([
                        [
                            $class: 'AmazonWebServicesCredentialsBinding',
                            credentialsId: awsCred,
                            accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                            secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                        ]
                    ]) {
                        sh """
                            if [ ! -d "./${apimPackDirectory}/${product}-${productVersion}" ]; then
                                aws s3 cp --quiet s3://${apimPackS3Bucket}/packs/${product}-${productVersion}.zip .
                                unzip -o ${product}-${productVersion}.zip -d ./${apimPackDirectory}
                                ls -la ./${apimPackDirectory}/${product}-${productVersion}/
                            else
                                echo "APIM pack already downloaded."
                            fi
                        """
                        // Copy the extracted pack into each pattern directory so deploy stages can reference it
                        for (def pattern : deploymentPatterns) {
                            sh "cp -r ./${apimPackDirectory} ${pattern.directory}/"
                        }
                    }

                    // ====================================================================
                    // Deploy and test all combinations in parallel
                    //   skipPeerTest=false : Deploys all 4 peer test patterns per infra
                    //   skipPeerTest=true  : Deploys a single pattern per infra (original)
                    //
                    //   Each peer test pattern runs in its own K8s namespace with its own
                    //   DB names and hostnames to avoid collisions.
                    // ====================================================================
                    def parallelDeployments = [:]
                    
                    for (def pattern : deploymentPatterns) {
                        def patternDir = pattern.directory
                        for (def tp : peerTestPatterns) {
                            for (def dbEngine : pattern.dbEngines) {
                                def dbEngineName = dbEngine.engine
                                
                                // Capture variables for closure safety
                                def patternDirSafe = patternDir
                                def dbEngineNameSafe = dbEngineName
                                def patternSafe = pattern
                                def dockerRegistrySafe = pattern.dockerRegistry.registry
                                def dockerRegistryUsernameSafe = pattern.dockerRegistry.username
                                def dockerRegistryPasswordSafe = pattern.dockerRegistry.password
                                def dpSafe = tp
                                def dpName = tp.name

                                // Unique stage identifier including peer test pattern name
                                def stageId = "${patternDirSafe}-${dbEngineNameSafe}-${dpName}"
                                // DB suffix for unique database names. Empty for custom single-pattern mode.
                                def dbSuffix = (peerTestPatterns.size() > 1) ? dpName.replace('-', '_') : ""

                                // Determine the docker image tag for each component based on peer test pattern
                                def acpImageTag = "${dbEngineNameSafe}-${dpSafe.acpVariant}"  // e.g. mysql-latest or mysql-ga
                                def tmImageTag  = "${dbEngineNameSafe}-${dpSafe.tmVariant}"
                                def gwImageTag  = "${dbEngineNameSafe}-${dpSafe.gwVariant}"
                                // Namespace and hosts for this branch (stable across deploy/test stages)
                                def namespace = (peerTestPatterns.size() > 1) ? "${patternSafe.id}-${dbEngineNameSafe}-${dpName}" : "${patternSafe.id}-${dbEngineNameSafe}"
                                def hostSuffix = (peerTestPatterns.size() > 1) ? "${dbEngineNameSafe}-${dpName}" : "${dbEngineNameSafe}"
                                def portalHost = "am-${hostSuffix}.wso2.com"
                                def gwHost     = "gw-${hostSuffix}.wso2.com"
                                def wsHost     = "websocket-${hostSuffix}.wso2.com"
                                def websubHost = "websub-${hostSuffix}.wso2.com"
                                def branchLogsDir = "${env.WORKSPACE}/${collectedLogsDirectory}"
                                def branchLogPrefix = "${patternSafe.os}-${dpName}-${dbEngineNameSafe}"
                                
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
                                                    // Unique DB names for this peer test pattern
                                                    def (sharedDbName, apimDbName) = getDbNames(dbSuffix)

                                                    sh """
                                                        # Ensure a clean namespace to avoid state leaks across runs.
                                                        if kubectl --context=${patternDirSafe} get namespace ${namespace} >/dev/null 2>&1; then
                                                            echo "Namespace ${namespace} already exists. Deleting it for a clean deployment."
                                                            kubectl --context=${patternDirSafe} delete namespace ${namespace} --timeout=600s
                                                        fi

                                                        # Create a fresh namespace for the deployment
                                                        kubectl --context=${patternDirSafe} create namespace ${namespace}

                                                        # Recreate secret directly (no kubectl apply) to avoid oversized
                                                        # last-applied annotation when keystore binaries are large.
                                                        kubectl --context=${patternDirSafe} delete secret apim-keystore-secret -n ${namespace} --ignore-not-found
                                                        kubectl --context=${patternDirSafe} create secret generic apim-keystore-secret --from-file=${pwd}/${patternDirSafe}/${apimPackDirectory}/${product}-${productVersion}/repository/resources/security/wso2carbon.jks --from-file=${pwd}/${patternDirSafe}/${apimPackDirectory}/${product}-${productVersion}/repository/resources/security/client-truststore.jks -n ${namespace}
                                                    """
                                                    println "Namespace created: ${namespace}"

                                                    // Fetch image digests using variant-specific tags
                                                    String wso2amAcpImageDigest = sh(script: "aws ecr describe-images --repository-name ${project}-wso2am-acp --query 'imageDetails[?imageTags != null && contains(imageTags, `${acpImageTag}`)].imageDigest' --region ${productDeploymentRegion} --output text", returnStdout: true).trim()
                                                    String wso2amTmImageDigest = sh(script: "aws ecr describe-images --repository-name ${project}-wso2am-tm --query 'imageDetails[?imageTags != null && contains(imageTags, `${tmImageTag}`)].imageDigest' --region ${productDeploymentRegion} --output text", returnStdout: true).trim()
                                                    String wso2amGwImageDigest = sh(script: "aws ecr describe-images --repository-name ${project}-wso2am-universal-gw --query 'imageDetails[?imageTags != null && contains(imageTags, `${gwImageTag}`)].imageDigest' --region ${productDeploymentRegion} --output text", returnStdout: true).trim()


                                                    // Execute DB scripts with per-pattern database names
                                                    executeDBScripts(dbEngineNameSafe, endpoint, dbUser, dbPassword, "${pwd}/${patternDirSafe}/${apimPackDirectory}/${product}-${productVersion}", dbSuffix)

                                                    String helmChartPath = "${pwd}/${helmDirectory}"
                                                    // Install the product using Helm
                                                    sh """
                                                        # Gateway REST ingress
                                                        helm --kube-context=${patternDirSafe} install apim-ing ${pwd}/${apimIntgDirectory}/kubernetes/gw-ingress \
                                                            --set hostname=${gwHost} \
                                                            --namespace ${namespace}
                                                        
                                                        # Deploy wso2am-acp (variant: ${dpSafe.acpVariant})
                                                        echo "Deploying WSO2 API Manager - API Control Plane [${dpSafe.acpVariant}] in ${namespace} namespace..."
                                                        helm --kube-context=${patternDirSafe} install apim-acp ${helmChartPath}/distributed/control-plane \
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
                                                            --set kubernetes.ingress.controlPlane.hostname="${portalHost}" \
                                                            --set wso2.apim.configurations.gateway.environments[0].name="Default" \
                                                            --set wso2.apim.configurations.gateway.environments[0].type="hybrid" \
                                                            --set wso2.apim.configurations.gateway.environments[0].gatewayType="Regular" \
                                                            --set wso2.apim.configurations.gateway.environments[0].provider="wso2" \
                                                            --set wso2.apim.configurations.gateway.environments[0].displayInApiConsole=true \
                                                            --set wso2.apim.configurations.gateway.environments[0].description="This is a hybrid gateway that handles both production and sandbox token traffic." \
                                                            --set wso2.apim.configurations.gateway.environments[0].showAsTokenEndpointUrl=true \
                                                            --set wso2.apim.configurations.gateway.environments[0].serviceName="apim-universal-gw-wso2am-universal-gw-service" \
                                                            --set wso2.apim.configurations.gateway.environments[0].servicePort=9443 \
                                                            --set wso2.apim.configurations.gateway.environments[0].wsHostname="${wsHost}" \
                                                            --set wso2.apim.configurations.gateway.environments[0].httpHostname="${gwHost}" \
                                                            --set wso2.apim.configurations.gateway.environments[0].websubHostname="${websubHost}" \
                                                            --set wso2.apim.configurations.devportal.enableApplicationSharing=true \
                                                            --set wso2.apim.configurations.devportal.applicationSharingType="default" \
                                                            --set wso2.apim.configurations.encryption.key="${encryptionKey}" \
                                                            --set kubernetes.gatewayAPI.enabled=false \
                                                            --set kubernetes.ingress.controlPlane.enabled=true \
                                                            --set wso2.apim.configurations.oauth_config.oauth2JWKSUrl="https://apim-acp-wso2am-acp-service:9443/oauth2/jwks" \
                                                            --set wso2.deployment.image.registry="${dockerRegistrySafe}" \
                                                            --set wso2.deployment.image.repository="${project}-wso2am-acp:${acpImageTag}" \
                                                            --set wso2.deployment.image.digest="${wso2amAcpImageDigest}" \
                                                            --set wso2.deployment.image.imagePullSecrets.enabled=true \
                                                            --set wso2.deployment.image.imagePullSecrets.username="${dockerRegistryUsernameSafe}" \
                                                            --set wso2.deployment.image.imagePullSecrets.password="${dockerRegistryPasswordSafe}" \
                                                            --set wso2.apim.configurations.databases.type="${dbEngineList[dbEngineNameSafe].dbType}" \
                                                            --set wso2.apim.configurations.databases.jdbc.driver="${dbEngineList[dbEngineNameSafe].dbDriver}" \
                                                            --set wso2.apim.configurations.databases.apim_db.url="jdbc:${dbEngineList[dbEngineNameSafe].dbType}://${endpoint}:${dbPort}/${apimDbName}?useSSL=false" \
                                                            --set wso2.apim.configurations.databases.apim_db.username="${dbUser}" \
                                                            --set wso2.apim.configurations.databases.apim_db.password="${dbPassword}" \
                                                            --set wso2.apim.configurations.databases.shared_db.url="jdbc:${dbEngineList[dbEngineNameSafe].dbType}://${endpoint}:${dbPort}/${sharedDbName}?useSSL=false" \
                                                            --set wso2.apim.configurations.databases.shared_db.username="${dbUser}" \
                                                            --set wso2.apim.configurations.databases.shared_db.password="${dbPassword}"
                                                        
                                                        # Wait for the deployment to be ready
                                                        kubectl --context=${patternDirSafe} wait --for=condition=available --timeout=400s deployment/apim-acp-wso2am-acp-deployment-1 -n ${namespace}
                                                        kubectl --context=${patternDirSafe} wait --for=condition=available --timeout=400s deployment/apim-acp-wso2am-acp-deployment-2 -n ${namespace}

                                                        # Deploy wso2am-tm (variant: ${dpSafe.tmVariant})
                                                        echo "Deploying WSO2 API Manager - Traffic Manager [${dpSafe.tmVariant}] in ${namespace} namespace..."
                                                        helm --kube-context=${patternDirSafe} install apim-tm ${helmChartPath}/distributed/traffic-manager \
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
                                                            --set wso2.apim.configurations.eventhub.enabled=true \
                                                            --set wso2.apim.configurations.eventhub.serviceUrl="apim-acp-wso2am-acp-service" \
                                                            --set wso2.apim.configurations.eventhub.urls="{apim-acp-wso2am-acp-1-service,apim-acp-wso2am-acp-2-service}" \
                                                            --set wso2.apim.configurations.encryption.key="${encryptionKey}" \
                                                            --set wso2.deployment.image.registry="${dockerRegistrySafe}" \
                                                            --set wso2.deployment.image.repository="${project}-wso2am-tm:${tmImageTag}" \
                                                            --set wso2.deployment.image.digest=${wso2amTmImageDigest} \
                                                            --set wso2.deployment.image.imagePullSecrets.enabled=true \
                                                            --set wso2.deployment.image.imagePullSecrets.username="${dockerRegistryUsernameSafe}" \
                                                            --set wso2.deployment.image.imagePullSecrets.password="${dockerRegistryPasswordSafe}" \
                                                            --set wso2.apim.configurations.databases.type="${dbEngineList[dbEngineNameSafe].dbType}" \
                                                            --set wso2.apim.configurations.databases.jdbc.driver="${dbEngineList[dbEngineNameSafe].dbDriver}" \
                                                            --set wso2.apim.configurations.databases.apim_db.url="jdbc:${dbEngineList[dbEngineNameSafe].dbType}://${endpoint}:${dbPort}/${apimDbName}?useSSL=false" \
                                                            --set wso2.apim.configurations.databases.apim_db.username="${dbUser}" \
                                                            --set wso2.apim.configurations.databases.apim_db.password="${dbPassword}" \
                                                            --set wso2.apim.configurations.databases.shared_db.url="jdbc:${dbEngineList[dbEngineNameSafe].dbType}://${endpoint}:${dbPort}/${sharedDbName}?useSSL=false" \
                                                            --set wso2.apim.configurations.databases.shared_db.username="${dbUser}" \
                                                            --set wso2.apim.configurations.databases.shared_db.password="${dbPassword}"

                                                        # Wait for the deployment to be ready
                                                        kubectl --context=${patternDirSafe} wait --for=condition=available --timeout=400s deployment/apim-tm-wso2am-tm-deployment-1 -n ${namespace}
                                                        kubectl --context=${patternDirSafe} wait --for=condition=available --timeout=400s deployment/apim-tm-wso2am-tm-deployment-2 -n ${namespace}

                                                        # Deploy wso2am-gw (variant: ${dpSafe.gwVariant})
                                                        echo "Deploying WSO2 API Manager - Gateway [${dpSafe.gwVariant}] in ${namespace} namespace..."
                                                        helm --kube-context=${patternDirSafe} install apim-universal-gw ${helmChartPath}/distributed/gateway \
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
                                                            --set kubernetes.gatewayAPI.enabled=false \
                                                            --set kubernetes.ingress.gateway.enabled=true \
                                                            --set kubernetes.ingress.gateway.hostname="${gwHost}" \
                                                            --set kubernetes.ingress.websocket.enabled=true \
                                                            --set kubernetes.ingress.websocket.hostname="${wsHost}" \
                                                            --set kubernetes.ingress.websub.enabled=true \
                                                            --set kubernetes.ingress.websub.hostname="${websubHost}" \
                                                            --set wso2.apim.configurations.km.serviceUrl="apim-acp-wso2am-acp-service" \
                                                            --set wso2.apim.configurations.throttling.serviceUrl="apim-tm-wso2am-tm-service" \
                                                            --set wso2.apim.configurations.throttling.urls="{apim-tm-wso2am-tm-1-service,apim-tm-wso2am-tm-2-service}" \
                                                            --set wso2.apim.configurations.eventhub.enabled=true \
                                                            --set wso2.apim.configurations.eventhub.serviceUrl="apim-acp-wso2am-acp-service" \
                                                            --set wso2.apim.configurations.eventhub.urls="{apim-acp-wso2am-acp-1-service,apim-acp-wso2am-acp-2-service}" \
                                                            --set wso2.apim.configurations.encryption.key="${encryptionKey}" \
                                                            --set wso2.deployment.image.registry="${dockerRegistrySafe}" \
                                                            --set wso2.deployment.image.repository="${project}-wso2am-universal-gw:${gwImageTag}" \
                                                            --set wso2.deployment.image.digest=${wso2amGwImageDigest} \
                                                            --set wso2.deployment.image.imagePullSecrets.enabled=true \
                                                            --set wso2.deployment.image.imagePullSecrets.username="${dockerRegistryUsernameSafe}" \
                                                            --set wso2.deployment.image.imagePullSecrets.password="${dockerRegistryPasswordSafe}" \
                                                            --set wso2.apim.configurations.databases.type="${dbEngineList[dbEngineNameSafe].dbType}" \
                                                            --set wso2.apim.configurations.databases.jdbc.driver="${dbEngineList[dbEngineNameSafe].dbDriver}" \
                                                            --set wso2.apim.configurations.databases.shared_db.url="jdbc:${dbEngineList[dbEngineNameSafe].dbType}://${endpoint}:${dbPort}/${sharedDbName}?useSSL=false" \
                                                            --set wso2.apim.configurations.databases.shared_db.username="${dbUser}" \
                                                            --set wso2.apim.configurations.databases.shared_db.password="${dbPassword}" \
                                                            --set wso2.deployment.replicas=2 \
                                                            --set wso2.deployment.minReplicas=2
                                                        
                                                        # Wait for the deployment to be ready
                                                        kubectl --context=${patternDirSafe} rollout status deployment/apim-universal-gw-wso2am-universal-gw-deployment --timeout=400s -n ${namespace}
                                                        kubectl --context=${patternDirSafe} wait --for=condition=ready --timeout=300s pod -l deployment=apim-universal-gw-wso2am-universal-gw -n ${namespace}
                                                    """
                                                }
                                            }
                                        } catch (Exception e) {
                                            println "Deployment failed for ${stageId}: ${e}"
                                            withCredentials([[
                                                $class: 'AmazonWebServicesCredentialsBinding',
                                                credentialsId: awsCred,
                                                accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                                                secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                                            ]]) {
                                                collectApimLogs(patternDirSafe, namespace, branchLogsDir, "${branchLogPrefix}-deploy-failure")
                                            }
                                            error "Deployment failed for ${stageId}. Please check the logs for more details."
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
                                                // Create an isolated copy of the test directory for this
                                                // branch to prevent concurrent main.sh / Newman collisions.
                                                def testDir = "${apimIntgDirectory}-${patternSafe.os}-${dbEngineNameSafe}-${dpName}-${env.BUILD_NUMBER}"
                                                sh "rm -rf ${testDir}"
                                                sh "cp -r ${apimIntgDirectory} ${testDir}"

                                                dir("${testDir}") {
                                                    echo "Waiting for APIM pod stability for ${stageId}..."
                                                    waitForApimPodStability(patternDirSafe, namespace)

                                                    echo "Waiting for DCR endpoint to be ready for ${stageId}..."
                                                    waitForDcrEndpoint(patternSafe.hostName, portalHost)

                                                    echo "Waiting for Publisher API to be ready for ${stageId}..."
                                                    waitForPublisherApi(patternSafe.hostName, portalHost)

                                                    echo "Waiting for Gateway API to be ready for ${stageId}..."
                                                    waitForGatewayApi(patternSafe.hostName, gwHost)

                                                    // All HTTP endpoints are reachable, but under heavy
                                                    // parallel load internal JMS/EventHub subscriber threads
                                                    // may still be catching up.
                                                    echo "All HTTP endpoints are ready. Waiting 60s for internal JMS/EventHub sync..."
                                                    sleep 60

                                                    sh """#!/bin/bash
                                                        set +e
                                                        ./main.sh --HOSTNAME="${patternSafe.hostName}" \\
                                                            --PORTAL_HOST="${portalHost}" \\
                                                            --GATEWAY_HOST="${gwHost}" \\
                                                            --kubernetes_namespace="${namespace}"
                                                        TEST_EXIT_CODE=\$?
                                                        if [[ \$TEST_EXIT_CODE -ne 0 ]]; then
                                                            echo "main.sh failed for ${stageId} with exit code \$TEST_EXIT_CODE. Capturing diagnostics."
                                                            kubectl --context=${patternDirSafe} get pods -n ${namespace} -o wide || true
                                                            kubectl --context=${patternDirSafe} get events -n ${namespace} --sort-by=.metadata.creationTimestamp | tail -n 80 || true

                                                            echo "Describing APIM pods..."
                                                            for p in \$(kubectl --context=${patternDirSafe} get pods -n ${namespace} -l product=apim -o jsonpath='{.items[*].metadata.name}'); do
                                                                echo "========== kubectl describe pod \$p =========="
                                                                kubectl --context=${patternDirSafe} describe pod \$p -n ${namespace} || true
                                                                echo "========== kubectl logs --previous \$p =========="
                                                                kubectl --context=${patternDirSafe} logs \$p -n ${namespace} --previous || true
                                                            done
                                                        fi
                                                        exit \$TEST_EXIT_CODE
                                                    """
                                                }
                                            }
                                        } catch (Exception e) {
                                            println "Test execution failed for ${stageId}: ${e}"
                                            error "Test execution failed for ${stageId}. Please check the logs for more details."
                                        } finally {
                                            withCredentials([[
                                                $class: 'AmazonWebServicesCredentialsBinding',
                                                credentialsId: awsCred,
                                                accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                                                secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                                            ]]) {
                                                collectApimLogs(patternDirSafe, namespace, branchLogsDir, "${branchLogPrefix}-test")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Run all deployment patterns in parallel
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
                        // Separate collected logs into two directories:
                        //   logs/       -> only current .log files (developer-facing)
                        //   diagnostics/ -> everything else (describe, events, previous logs, etc.)
                        sh """#!/bin/bash
                            set +e
                            mkdir -p ${logsDirectory} ${diagnosticsDirectory}
                            if [[ -d ${collectedLogsDirectory} ]]; then
                                # Move current .log files (exclude .previous.log) into logs/
                                find ${collectedLogsDirectory} -maxdepth 1 -name '*.log' ! -name '*.previous.log' -exec mv {} ${logsDirectory}/ \\;
                                # Move everything else into diagnostics/
                                find ${collectedLogsDirectory} -maxdepth 1 -type f -exec mv {} ${diagnosticsDirectory}/ \\;
                            fi
                        """
                        archiveArtifacts artifacts: "${logsDirectory}/**/*", fingerprint: true, allowEmptyArchive: true
                        archiveArtifacts artifacts: "${diagnosticsDirectory}/**/*", fingerprint: true, allowEmptyArchive: true
                    }
                    // Clean up the workspace
                    cleanWs()
                }
            }
        }
    }
}

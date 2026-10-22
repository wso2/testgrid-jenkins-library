/*
* Copyright (c) 2022 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
import groovy.io.FileType
import hudson.model.*

def deploymentDirectories = []
def updateType = ""
// Test groups run on a shared stack. Only populated when the v2 (one EC2 per group) CFN
// template is selected - see isSharedStackTemplate().
def sharedStackGroups = []

pipeline {
agent {label 'pipeline-agent'}
stages {
    stage('Clone CFN repo') {
        steps {
            script {
                cfn_repo_url="https://github.com/wso2/testgrid.git"
                cfn_repo_branch="master"
                if (apim_pre_release.toBoolean()){
                    cfn_repo_branch="apim-pre-release"
                }
                if (use_wum.toBoolean()){
                    updateType="wum"
                }else{
                    updateType="u2"
                }
                if (use_staging.toBoolean()){
                    updateType="staging"
                }
                dir("testgrid") {
                    git branch: "${cfn_repo_branch}",
                    credentialsId: "WSO2_GITHUB_TOKEN",
                    url: "${cfn_repo_url}"
                }
            }
        }
    }
    stage('Constructing parameter files'){
        steps {
            script {
                withCredentials([string(credentialsId: 'AWS_ACCESS_KEY_ID', variable: 'accessKey'),
                string(credentialsId: 'AWS_SECRET_ACCESS_KEY', variable: 'secretAccessKey'),
                string(credentialsId: 'WUM_USERNAME', variable: 'wumUserName'),
                string(credentialsId: 'WUM_PASSWORD', variable: 'wumPassword'),
                string(credentialsId: 'DEPLOYMENT_DB_PASSWORD', variable: 'dbPassword'),
                string(credentialsId: 'AWS_ACCESS_KEY_ID', variable: 's3accessKey'),
                string(credentialsId: 'AWS_SECRET_ACCESS_KEY', variable: 's3secretKey'),
                string(credentialsId: 'TESTGRID_EMAIL_PASSWORD', variable: 'testgridEmailPassword')])
                {
                    sh '''
                        echo "Writting AWS-Access Key ID to parameter file"
                        ./scripts/write-parameter-file.sh "AWSAccessKeyId" ${accessKey} "${WORKSPACE}/parameters/parameters.json"
                        echo "Writting AWS-Secret Access Key to parameter file"
                        ./scripts/write-parameter-file.sh "AWSAccessKeySecret" ${secretAccessKey} "${WORKSPACE}/parameters/parameters.json"
                        echo "Writting WUM Password to parameter file"
                        ./scripts/write-parameter-file.sh "WUMPassword" "${wumPassword}" "${WORKSPACE}/parameters/parameters.json"
                        echo "Writting WUM Username to parameter file"
                        ./scripts/write-parameter-file.sh "WUMUsername" ${wumUserName} "${WORKSPACE}/parameters/parameters.json"
                        echo "Writting DB password to parameter file"
                        ./scripts/write-parameter-file.sh "DBPassword" ${dbPassword} "${WORKSPACE}/parameters/parameters.json"
                        echo "Writting S3 access key id to parameter file"
                        ./scripts/write-parameter-file.sh "S3AccessKeyID" ${s3accessKey} "${WORKSPACE}/parameters/parameters.json"
                        echo "Writting S3 secret access key to parameter file"
                        ./scripts/write-parameter-file.sh "S3SecretAccessKey" ${s3secretKey} "${WORKSPACE}/parameters/parameters.json"
                        echo "Writing testgrid email key to parameter file"
                        ./scripts/write-parameter-file.sh "TESTGRID_EMAIL_PASSWORD" ${testgridEmailPassword} "${WORKSPACE}/parameters/parameters.json"
                    '''
                }
                withCredentials([usernamePassword(credentialsId: 'WSO2_GITHUB_TOKEN', usernameVariable: 'githubUserName', passwordVariable: 'githubPassword')]) 
                {
                    sh '''
                       echo "Writting Github Username to parameter file"
                        ./scripts/write-parameter-file.sh "GithubUserName" ${githubUserName} "${WORKSPACE}/parameters/parameters.json"
                        echo "Writting Github Password to parameter file"
                        ./scripts/write-parameter-file.sh "GithubPassword" ${githubPassword} "${WORKSPACE}/parameters/parameters.json"
                    '''
                }
                if (isSharedStackTemplate()) {
                    // An empty test_groups means "run every group", each on its own EC2.
                    // The same list drives the CFN InstanceNames parameter and the
                    // parallel group branches in the next stage.
                    sharedStackGroups = (test_groups?.trim()) ? test_groups.split(",").collect{ it.trim() }.findAll{ it } : ["group1", "group2", "group3", "group4"]
                    // Group names become CFN logical IDs and shell arguments - alphanumeric only.
                    def invalidGroups = sharedStackGroups.findAll{ !(it ==~ /[A-Za-z0-9]+/) }
                    if (invalidGroups) {
                        error "Test groups must be alphanumeric for the shared-stack CFN: ${invalidGroups}"
                    }
                    println "Shared-stack CFN selected. One EC2 per test group: ${sharedStackGroups}"
                    sh """
                        echo "Writting instance names (one per test group) to parameter file"
                        ./scripts/write-parameter-file.sh "InstanceNames" "${sharedStackGroups.join(",")}" "\${WORKSPACE}/parameters/parameters.json"
                    """
                }
                sh '''
                    echo --- Adding common parameters to parameter file! ---
                    echo "Writting product name to parameter file"
                    ./scripts/write-parameter-file.sh "Product" ${product} "${WORKSPACE}/parameters/parameters.json"
                    echo "Writting product version to parameter file"
                    ./scripts/write-parameter-file.sh "ProductVersion" ${product_version} "${WORKSPACE}/parameters/parameters.json"
                    echo "Writting product deployment region to parameter file"
                    ./scripts/write-parameter-file.sh "Region" ${product_deployment_region} "${WORKSPACE}/parameters/parameters.json"
                    echo "Writting product instance Type to parameter file"
                    ./scripts/write-parameter-file.sh "WSO2InstanceType" ${product_instance_type} "${WORKSPACE}/parameters/parameters.json"
                    echo "Writting product deployment cfn loction to parameter file"
                    ./scripts/write-parameter-file.sh "CloudformationLocation" ${cloudformation_location} "${WORKSPACE}/parameters/parameters.json"
                    echo "Writting product deployment ALB Certificate ARN to parameter file"
                    ./scripts/write-parameter-file.sh "ALBCertificateARN" ${alb_cert_arn} "${WORKSPACE}/parameters/parameters.json"
                    echo "Writting product deployment Product Repository to parameter file"
                    ./scripts/write-parameter-file.sh "ProductRepository" ${product_repository} "${WORKSPACE}/parameters/parameters.json"
                    echo "Writting product deployment Product Test Branch to parameter file"
                    ./scripts/write-parameter-file.sh "ProductTestBranch" ${product_test_branch} "${WORKSPACE}/parameters/parameters.json"
                    echo "Writting product deployment Product Test script location to parameter file"
                    ./scripts/write-parameter-file.sh "ProductTestScriptLocation" ${product_test_script} "${WORKSPACE}/parameters/parameters.json"
                    echo "Writting product update type to parameter file"
                    ./scripts/write-parameter-file.sh "UpdateType" '''+updateType+''' "${WORKSPACE}/parameters/parameters.json"
                    echo "Writting test type to parameter file"
                    ./scripts/write-parameter-file.sh "TestType" "intg" "${WORKSPACE}/parameters/parameters.json"
                    echo "Writting product Surefire Report Directory"
                    ./scripts/write-parameter-file.sh "SurefireReportDir" ${surefire_report_dir} "${WORKSPACE}/parameters/parameters.json"
                    echo "Writing to parameter file completed!"
                    echo --- Preparing parameter files for deployments! ---
                    ./scripts/deployment-builder.sh ${product} ${product_version} '''+updateType+'''
                '''
            }
        }
    }
    stage('Deploying Testing and Logs Uploading') {
        steps {
            script {
                println "Creating deployments for the following combinations!"
                def deployment_path = "${WORKSPACE}/deployment"
                def command = '''
                    ls -l ${WORKSPACE}/deployment | grep -E "^d" | awk '{print $9}'
                '''
                def procDirList = sh(returnStdout: true, script: command).trim().split("\\r?\\n")
                for (procDir in procDirList){
                    deploymentDirectories << procDir
                }
                // Build a single, flat parallel map so Blue Ocean can visualize it.
                // Nested parallel (parallel-within-parallel) is not rendered by Blue
                // Ocean, so each test group becomes its own top-level branch keyed by
                // "<combo> :: <group>" instead of being nested under the combo.
                def build_jobs = [:]
                for (deploymentDirectory in deploymentDirectories){
                    println deploymentDirectory
                    def dir = deploymentDirectory
                    if (sharedStackGroups) {
                        build_jobs["${dir}"] = create_shared_stack_deployment(dir, sharedStackGroups)
                    } else if (test_groups != "") {
                        for (productTestGroup in test_groups.split(",")) {
                            def group = productTestGroup
                            build_jobs["${dir} :: ${group}"] = create_group_deployment(dir, group)
                        }
                    } else {
                        build_jobs["${dir}"] = create_build_jobs(dir)
                    }
                }

                parallel build_jobs
            }
        }
    }
}
post {
    always {
        sh '''
            echo "Job is completed... Cleaning up deployments!"
        '''
        // Guarantee CloudFormation teardown no matter how the build ends (success,
        // failure, or abort). During the normal flow stacks are deleted inside the test
        // scripts, but an aborted/killed build interrupts those scripts before teardown
        // runs, leaving stacks alive. This scans every deployment directory on disk and
        // issues a delete for any stack still standing - and must run BEFORE cleanWs,
        // which wipes the parameter files holding the stack names.
        withCredentials([string(credentialsId: 'AWS_ACCESS_KEY_ID', variable: 'AWS_ACCESS_KEY_ID'),
        string(credentialsId: 'AWS_SECRET_ACCESS_KEY', variable: 'AWS_SECRET_ACCESS_KEY')])
        {
            sh '''
                ./scripts/cleanup-deployments.sh
            '''
        }
        script {
            sendEmail(deploymentDirectories, updateType)
        }
        // Publish the per-branch execution logs (one file per combination+group) as
        // downloadable build artifacts. Must run BEFORE cleanWs wipes the workspace.
        // Independent of Blue Ocean, so logs are always retrievable from the build page.
        archiveArtifacts artifacts: 'build-logs/*.log', allowEmptyArchive: true
        cleanWs deleteDirs: true, notFailBuild: true
    }
}
}

// No test groups specified: deploy the combination once and run the full suite.
def create_build_jobs(deploymentDirectory){
    return{
        deployStack(deploymentDirectory)
        executeTests(deploymentDirectory, "", true)
    }
}

// One isolated deployment (stack) per test group. Returned as a flat parallel branch
// from Stage 3 so Blue Ocean renders each group as its own top-level branch.
def create_group_deployment(deploymentDirectory, productTestGroup){
    return {
        // Prepare an isolated deployment directory & stack name for this test group.
        def groupDeploymentDirectory = sh(
            returnStdout: true,
            script: "./scripts/prepare-group-deployment.sh ${deploymentDirectory} ${productTestGroup}"
        ).trim()
        println "Prepared group deployment directory: ${groupDeploymentDirectory}"
        deployStack(groupDeploymentDirectory)
        executeTests(groupDeploymentDirectory, productTestGroup, true)
    }
}

// One shared stack per combination (v2 CFN): one EC2 per test group plus one RDS that
// every group uses through its own logical databases. The stack is deployed once, the
// groups run as parallel branches against it, and it is torn down once after all of
// them finish (pass or fail). Nested parallel stages are not drawn by Blue Ocean, but
// each group still writes its own build-logs/<combo>-<group>.log.
def create_shared_stack_deployment(deploymentDirectory, groups){
    return {
        try {
            deployStack(deploymentDirectory)
            def group_jobs = [:]
            for (g in groups) {
                def group = g
                group_jobs["${deploymentDirectory} :: ${group}"] = {
                    def groupDeploymentDirectory = sh(
                        returnStdout: true,
                        script: "./scripts/prepare-shared-stack-group.sh ${deploymentDirectory} ${group}"
                    ).trim()
                    println "Prepared shared-stack group directory: ${groupDeploymentDirectory}"
                    executeTests(groupDeploymentDirectory, group, false)
                }
            }
            parallel group_jobs
        } finally {
            stage("Teardown [${deploymentDirectory}]") {
                runTestPhase(deploymentDirectory, "", "teardown")
            }
        }
    }
}

// The v2 CFN provisions one EC2 per test group behind a single shared RDS. Every other
// template keeps the existing one-stack-per-combination / one-stack-per-group flows.
def isSharedStackTemplate() {
    return cloudformation_location.trim().endsWith("wso2-u2-intg-test-cfn-v2.yaml")
}

def deployStack(deploymentDirectory){
    stage("Deploy ${deploymentDirectory}") {
        println "Deploying Stack:- ${deploymentDirectory}..."
        shToBranchLog(deploymentDirectory,
            "./scripts/deployment-handler.sh ${deploymentDirectory} ${env.WORKSPACE}/${cloudformation_location}")
    }
}

// Mirror a parallel branch's shell output into its own log file so per-group logs
// don't interleave into one giant console log and survive Blue Ocean's flaky log
// rendering. deploymentDirectory is the unique "<combo>-<group>" name, so each
// combination+group gets its own file, archived as a build artifact in post{}.
// Logs live under build-logs/ (NOT outputs/, which prepareOutputDir wipes on setup).
// pipefail + PIPESTATUS keep the real exit code so a failing phase still fails the stage.
def shToBranchLog(deploymentDirectory, command) {
    def logFile = "${env.WORKSPACE}/build-logs/${deploymentDirectory}.log"
    sh """#!/bin/bash
set -o pipefail
mkdir -p '${env.WORKSPACE}/build-logs'
{ ${command} ; } 2>&1 | tee -a '${logFile}'
exit \${PIPESTATUS[0]}
"""
}

// Run the remote test flow as two stages (Test + Teardown) to keep the Blue Ocean
// graph legible when many combination x group branches run in parallel - a large
// stage count per branch makes the graph shrink and hide step detail. Each phase is
// still a separate step within the stage (its own log line), so granularity is kept
// without exploding the graph. The run is wrapped in try/finally so reports are
// always collected and the stack is always torn down, even when the tests fail.
// teardown=false is used for groups on a shared stack: sibling groups still use the
// stack, so it is torn down once by create_shared_stack_deployment instead.
def executeTests(deploymentDirectory, productTestGroup, teardown) {
    def label = productTestGroup ? "${productTestGroup} @ ${deploymentDirectory}" : "${deploymentDirectory}"
    println "Executing test ${productTestGroup} for ${product_repository}"
    try {
        stage("Test [${label}]") {
            runTestPhase(deploymentDirectory, productTestGroup, "clone")
            runTestPhase(deploymentDirectory, productTestGroup, "setup")
            runTestPhase(deploymentDirectory, productTestGroup, "update")
            runTestPhase(deploymentDirectory, productTestGroup, "provisiondb")
            runTestPhase(deploymentDirectory, productTestGroup, "test")
        }
    } finally {
        stage(teardown ? "Teardown [${label}]" : "Collect [${label}]") {
            // Report collection is best-effort: a collect failure must never block
            // the teardown below, or the stack would leak until the post-build sweep.
            try {
                runTestPhase(deploymentDirectory, productTestGroup, "collect")
            } catch (err) {
                println "Report collection failed for ${label} (best-effort, continuing to teardown): ${err}"
            }
            if (teardown) {
                runTestPhase(deploymentDirectory, productTestGroup, "teardown")
            }
        }
    }
}

def runTestPhase(deploymentDirectory, productTestGroup, phase) {
    shToBranchLog(deploymentDirectory,
        "./scripts/intg-test-deployment.sh '${deploymentDirectory}' '${product_repository}' '${product_test_branch}' '${product_test_script}' '${productTestGroup}' ${phase}")
}

def sendEmail(deploymentDirectories, updateType) {
    def deployments = ""
    for (deploymentDirectory in deploymentDirectories){
        deployments = deployments + deploymentDirectory + "<br>"
    }
    
    if (currentBuild.currentResult.equals("SUCCESS")){
        headerColour = "#05B349"
    }else{
        headerColour = "#ff0000"
    }
    content="""
        <div style="padding-left: 10px">
            <div style="height: 4px; background-image: linear-gradient(to right, orange, black);">
        </div>
        <table border="0" cellspacing="0" cellpadding="0" valign='top'>
            <td>
                <h1>Integration test results</span></h1>
            </td>
            <td>
                <img src="http://cdn.wso2.com/wso2/newsletter/images/nl-2017/nl2017-wso2-logo-wb.png"/>
            </td>
        </table>
        <div style="margin: auto; background-color: #ffffff;">
            <p style="height:10px;font-family: Lucida Grande;font-size: 20px;">
            <font color="black">
                <b> Testgrid job status </b>
            </font>
            </p>
        <table cellspacing="0" cellpadding="0" border="2" bgcolor="#f0f0f0" width="80%">
        <colgroup>
            <col width="150"/>
            <col width="150"/>
        </colgroup>
        <tr style="border: 1px solid black; font-size: 16px;">
            <th bgcolor="${headerColour}" style="padding-top: 3px; padding-bottom: 3px">Test Specification</th>
            <th bgcolor="${headerColour}" style="black">Test Values</th>
        </tr>
        <tr>
            <td>Product</td>
            <td>${product.toUpperCase()}</td>
        </tr>
        <tr>
            <td>Version</td>
            <td>${product_version}</td>
        </tr>
        <tr>
            <td>Used WUM as Update</td>
            <td>${use_wum}</td>
        </tr>
        <tr>
            <td>Used Staging as Update</td>
            <td>${use_staging}</td>
        </tr>
        <tr>
            <td>Used APIM pre-release</td>
            <td>${apim_pre_release}</td>
        </tr>
        <tr>
            <td>Operating Systems</td>
            <td>${os_list}</td>
        </tr>
        <tr>
            <td>Databases</td>
            <td>${database_list}</td>
        </tr>
        <tr>
            <td>Test Groups</td>
            <td>${test_groups}</td>
        </tr>
        <tr>
            <td>JDKs</td>
            <td>${jdk_list}</td>
        </tr>
        <tr>
            <td>Product Test Repository</td>
            <td>${product_repository}</td>
        </tr>
        <tr>
            <td>Product Test Repository Branch</td>
            <td>${product_test_branch}</td>
        </tr>
        <tr>
            <td>Product Depolyment Combinations</td>
            <td>${deployments}</td>
        </tr>
        </table>
        <br/>
        <br/>
        <p style="height:10px;font-family:Lucida Grande;font-size: 20px;">
            <font color="black">
            <b>Build Info:</b>
            <small><a href="${BUILD_URL}">${BUILD_URL}</a></small>
            </font>
        </p>
        <br/>
        <br/>
        <br/>
        <em>Tested by WSO2 Jenkins TestGrid Pipeline.</em>
        </div>
        """
    subject="[TestGrid][${updateType.toUpperCase()}][${product.toUpperCase()}:${product_version}][INTG]-Build ${currentBuild.currentResult}-#${env.BUILD_NUMBER}"
    senderEmailGroup=""
    if(product.equals("wso2am") || product.equals("ei") || product.equals("esb") || product.equals("mi")){
        senderEmailGroup = "integration-builder@wso2.com"
    }else if(product.equals("is")) {
        senderEmailGroup = "iam-builder@wso2.com"
    }else if(product.equals("ob")) {
        senderEmailGroup = "bfsi-group@wso2.com"
    }
    emailext(to: "${senderEmailGroup},builder@wso2.org",
            subject: subject,
            body: content, mimeType: 'text/html')
}

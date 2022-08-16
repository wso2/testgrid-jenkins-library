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

pipeline {
agent {label 'pipeline-agent'}
stages {
    stage('Clone CFN repo') {
        steps {
            script {
                cfn_repo_url="https://github.com/wso2/testgrid.git"
                cfn_repo_branch="master"
                if (use_wum.toBoolean()){
                    updateType="wum"
                }else{
                    updateType="u2"
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
                        ./scripts/write-parameter-file.sh "WUMPassword" ${wumPassword} "${WORKSPACE}/parameters/parameters.json"
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
                def build_jobs = [:]
                for (deploymentDirectory in deploymentDirectories){
                    println deploymentDirectory
                    build_jobs["${deploymentDirectory}"] = create_build_jobs(deploymentDirectory)
                }

                parallel build_jobs
            }
        }
    }
}
post {
    always {
        sh '''
            echo "Job is completed... Deleting the workspace directories!"
        '''
        script {
            sendEmail(deploymentDirectories, updateType)
        }
        cleanWs deleteDirs: true, notFailBuild: true
    }
}
}

def create_build_jobs(deploymentDirectory){
    return{
        stage("${deploymentDirectory}"){
            stage("Deploy ${deploymentDirectory}") {
                println "Deploying Stack:- ${deploymentDirectory}..."
                sh'''
                    ./scripts/deployment-handler.sh '''+deploymentDirectory+''' ${WORKSPACE}/${cloudformation_location} 
                '''
                stage("Testing ${deploymentDirectory}") {
                    println "Deployment Integration testing..."
                    sh'''
                        ./scripts/intg-test-deployment.sh '''+deploymentDirectory+''' ${product_repository} ${product_test_branch} ${product_test_script}
                    '''
                }
            }
        }
    }
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
            <td>Operating Systems</td>
            <td>${os_list}</td>
        </tr>
        <tr>
            <td>Databases</td>
            <td>${database_list}</td>
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

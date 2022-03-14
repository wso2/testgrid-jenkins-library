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
def updateLevel = ""
def s3BucketName = "janeth-tg-logs"
def s3BuildLogPath = ""
pipeline {
agent {label 'pipeline-slave'}
stages {
    stage('Clone CFN repo') {
        steps {
            script {
                properties([
                    parameters([
                        string(
                            name: 'product',
                            defaultValue: 'apim',
                            description: 'The WSO2 product that needs to be tested from Test Grid.', 
                            trim: false
                        ),
                        string(
                            name: 'cfn_repo_url',
                            defaultValue: 'https://github.com/janethavi/aws-apim.git',
                            description: 'The WSO2 product CFN repo name', 
                            trim: false
                        ),
                        string(
                            name: 'product_version',
                            defaultValue: '3.2.0',
                            description: 'The product version that needs to be tested using testgrid.',
                            trim: false
                        ),
                        string(
                            name: 'product_deployment_region',
                            defaultValue: 'us-east-2',
                            description: 'The region where the product stack is getting deployed.',
                            trim: false
                        ),
                        string(
                            name: 'os_list',
                            defaultValue: 'CentOS7',
                            description: 'The OS and its version. If there are multiple parameters, please add them by separating them by a ","(Comma).',
                            trim: false
                        ),
                        string(
                            name: 'jdk_list',
                            defaultValue: 'OPEN_JDK8',
                            description: 'The JDK and its version. If there are multiple parameters, please add them by separating them by a ","(Comma).',
                            trim: false
                        ),
                        string(
                            name: 'database_list',
                            defaultValue: 'MySQL-5.7',
                            description: 'The Database type and its version. If there are multiple parameters, please add them by separating them by a ","(Comma).',
                            trim: false
                        ),
                        booleanParam(
                            name: 'use_wum',
                            defaultValue: true,
                            description: 'If using WUM this should be true. If using U2 this should be false.'
                        ),
                        string(
                            name: 'product_repository',
                            defaultValue: 'https://github.com/janethavi/product-apim.git',
                            description: 'The product repo where the test scripts are existing',
                            trim: false
                        ),
                        string(
                            name: 'product_test_branch', 
                            defaultValue: 'product-scenarios-3.2.0',
                            description: 'The repo branch where the test script is existing',
                            trim: false
                        ),
                        string(
                            name: 'product_test_script', 
                            defaultValue: 'product-scenarios/test.sh',
                            description: 'The location of the test script',
                            trim: false
                        )
                    ])
                ])
                aws_repo_branch=""
                if (use_wum.toBoolean(){
                    aws_repo_branch="${product_version}-new"
                    updateLevel="wum"
                }else{
                    aws_repo_branch="${product_version}-u2-new"
                    updateLevel="u2"
                }
                dir(product) {
                    git branch: "${aws_repo_branch}",
                    credentialsId: "JANETH_GITHUB_TEMP",
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
                string(credentialsId: 'AWS_SECRET_ACCESS_KEY', variable: 's3secretKey')])
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
                    '''
                }
                withCredentials([usernamePassword(credentialsId: 'JANETH_GITHUB_TEMP', usernameVariable: 'githubUserName', passwordVariable: 'githubPassword')]) 
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
                '''    
                //Generate S3 Log output path
                s3BuildLogPath = "${s3BucketName}/artifacts/jobs/${product}-${product_version}/build-${BUILD_NUMBER}"
                println "Your Logs will be uploaded to: s3://"+s3BuildLogPath
                sh'''
                    echo "Writting S3 Log uploading endpoint to parameter file"
                    ./scripts/write-parameter-file.sh "S3OutputBucketLocation" '''+s3BuildLogPath+''' "${WORKSPACE}/parameters/parameters.json"
                    echo "Writing to parameter file completed!"
                    echo --- Preparing parameter files for deployments! ---
                    ./scripts/parameter-file-handler.sh ${product} ${product_version} '''+updateLevel+'''
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
            echo "Arranging the log files!"
            parameters_directory="${WORKSPACE}/parameters/parameters.json"

            localLogDir="build-${BUILD_NUMBER}"
            mkdir -p ${localLogDir}
            aws s3 cp s3://'''+s3BuildLogPath+'''/ ${localLogDir} --recursive --quiet
            echo "Job is completed... Deleting the workspace directories!"
        '''
        archiveArtifacts artifacts: "build-${env.BUILD_NUMBER}/**/*.*", fingerprint: true
        script {
            sendEmail(deploymentDirectories)
        }
        cleanWs deleteDirs: true, notFailBuild: true
    }
}
}

def create_build_jobs(deploymentDirectory){
    return{
        stage("${deploymentDirectory}"){
            stage("Deploy ${deploymentDirectory}") {
                println "Stack deploying..."
                String[] cloudformationLocation = []
                switch(product) {
                    case "apim":
                        cloudformationLocation = ["${WORKSPACE}/apim/apim/Minimum-HA/apim.yaml"]
                        break;
                    case "is":
                        // The deployment is done in the indexed order
                        cloudformationLocation = ["${WORKSPACE}/is/is/Minimum-HA/identity.yaml", "${WORKSPACE}/is/is-samples/test-is-samples.yml"]
                        break;
                    default:
                        println("Product name is incorrect!");
                }
                sh'''
                    ./scripts/deploy.sh '''+deploymentDirectory+''' '''+cloudformationLocation+''' 
                '''
                stage("Testing ${deploymentDirectory}") {
                    println "Deployment testing..."
                    sh'''
                        ./scripts/test-deployment.sh '''+deploymentDirectory+''' ${product_repository} ${product_test_branch} ${product_test_script}
                    '''
                    stage("Uploading results to ${deploymentDirectory}") {
                        println "Upoading logs..."
                        sh'''
                            ./scripts/post-actions.sh '''+deploymentDirectory+'''
                        '''
                    }
                }
            }
        }
    }
}

def sendEmail(deploymentDirectories) {
    def deployments = ""
    for (deploymentDirectory in deploymentDirectories){
        deployments = deployments + deploymentDirectory + "<br>"
    }
    content="""
        <div style="padding-left: 10px">
            <div style="height: 4px; background-image: linear-gradient(to right, orange, black);">
        </div>
        <table border="0" cellspacing="0" cellpadding="0" valign='top'>
            <td>
                <h1>Scenario test results</span></h1>
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
            <th bgcolor="#05B349" style="padding-top: 3px; padding-bottom: 3px">Test Specification</th>
            <th bgcolor="#05B349" style="black">Test Values</th>
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
        <em>Tested by WSO2 Jenkins TestGrid Pipeline.</em>
        </div>
        """
    subject="[Jenkins TestGrid] ${currentBuild.currentResult}- ${product.toUpperCase()}:${product_version} Build-#${env.BUILD_NUMBER}"
    emailext(to: "janeth@wso2.com",
            subject: subject,
            body: content, mimeType: 'text/html')
}

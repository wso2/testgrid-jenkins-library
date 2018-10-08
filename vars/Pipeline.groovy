/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
 */

import org.wso2.tg.jenkins.alert.Slack
import org.wso2.tg.jenkins.alert.Email
import org.wso2.tg.jenkins.util.Common
import org.wso2.tg.jenkins.util.AWSUtils
import org.wso2.tg.jenkins.executors.TestExecutor

// The pipeline should resite in a call block
def call() {
    /*
    uniqueId can be used when there is a requirement to run specific pipeline block for a certain job.
    What you have to do is;
        1. Pass an environment variable to Jenkins pipeline as 'uniqueId'.
        2. Add the custom pipeline to an additional if-clause with referring the job name similar to 'dev'.
    */
    def uniqueId = env['uniqueId']
    def jobName = "dev"

    echo uniqueId;
    if (uniqueId != null) {
        jobName = uniqueId
    }
    if (jobName == "test") {
        pipeline {
            agent any
            stages {
                stage('Testing') {
                    steps {
                        script {
                            echo "This is a test"
                        }
                    }
                }
            }
        }
    }
    else if (jobName == "dev") {  
        def alert = new Slack()
        def email = new Email()
        def commonUtils = new Common()
        def awsHelper = new AWSUtils()
        def testExecutor = new TestExecutor()
        properties = null

        pipeline {
            agent {
                node {
                    label ""
                    customWorkspace "/testgrid/testgrid-home/jobs/${JOB_BASE_NAME}"
                }
            }

            environment {
                TESTGRID_NAME = 'WSO2-TestGrid'
                TESTGRID_DIST_LOCATION = '/testgrid/testgrid-home/testgrid-dist/'
                TESTGRID_HOME = '/testgrid/testgrid-home/'

                PRODUCT = "${JOB_BASE_NAME}"

                TESTGRID_YAML_LOCATION = "${INFRA_LOCATION}/jobs/${PRODUCT}/testgrid.yaml"

                AWS_ACCESS_KEY_ID = credentials('AWS_ACCESS_KEY_ID')
                AWS_SECRET_ACCESS_KEY = credentials('AWS_SECRET_ACCESS_KEY')
                tomcatUsername = credentials('TOMCAT_USERNAME')
                tomcatPassword = credentials('TOMCAT_PASSWORD')

                WUM_UAT_URL=credentials('WUM_UAT_URL')
                WUM_UAT_APPKEY=credentials('WUM_UAT_APPKEY')
                USER_NAME=credentials('WUM_USERNAME')
                PASSWORD=credentials('WUM_PASSWORD')
                GIT_WUM_USERNAME=credentials('GIT_WUM_USERNAME')
                GIT_WUM_PASSWORD=credentials('GIT_WUM_PASSWORD')

                PWD = pwd()
                JOB_CONFIG_YAML = "job-config.yaml"
                JOB_CONFIG_YAML_PATH = "${PWD}/${JOB_CONFIG_YAML}"
                PRODUCT_GIT_URL = "${PRODUCT_GIT_URL}"
                PRODUCT_GIT_BRANCH = "${PRODUCT_GIT_BRANCH}"
                PRODUCT_DIST_DOWNLOAD_API = "${PRODUCT_DIST_DOWNLOAD_API}"
                WUM_CHANNEL = "${WUM_CHANNEL}"
                PRODUCT_CODE = "${PRODUCT_CODE}"
                WUM_PRODUCT_VERSION = "${WUM_PRODUCT_VERSION}"
                USE_CUSTOM_TESTNG = "${USE_CUSTOM_TESTNG}"
                EXECUTOR_COUNT = "${EXECUTOR_COUNT}"
            }

            tools {
                jdk 'jdk8'
            }

            stages {
                stage('Preparation') {
                    steps {
                        script {
                            try {
                                alert.sendNotification('STARTED', "Initiation", "#build_status_verbose")
                                alert.sendNotification('STARTED', "Initiation", "#build_status")
                                echo pwd()
                                deleteDir()

                                sh """
                                  echo ${TESTGRID_NAME}
                                  cd ${TESTGRID_DIST_LOCATION}
                                  cd ${TESTGRID_NAME}
                    
                                  sed -i 's/-Xms256m -Xmx1024m/-Xmx2G -Xms2G/g' testgrid
                                """
                                // Get testgrid.yaml from jenkins managed files
                                configFileProvider(
                                        [configFile(fileId: "${PRODUCT}-testgrid-yaml", targetLocation:
                                                "${TESTGRID_YAML_LOCATION}")]) {
                                }

                                //Constructing the product git url if test mode is wum. Adding the Git username and password into the product git url.
                                if("${TEST_MODE}"=="WUM"){
                                    def url = "${PRODUCT_GIT_URL}"
                                    def values = url.split('//g')
                                    def productGitUrl = "${values[0]}//${GIT_WUM_USERNAME}:${GIT_WUM_PASSWORD}@g${values[1]}"
                                    PRODUCT_GIT_URL = "${productGitUrl}"

                                }else {
                                    PRODUCT_GIT_URL = "${PRODUCT_GIT_URL}"
                                }

                                sh """
                                    echo 'keyFileLocation: workspace/testgrid-key.pem' > ${JOB_CONFIG_YAML_PATH}
                                    echo 'infrastructureRepository: ${INFRA_LOCATION}/' >> ${JOB_CONFIG_YAML_PATH}
                                    echo 'deploymentRepository: ${INFRA_LOCATION}/' >> ${JOB_CONFIG_YAML_PATH}
                                    echo 'scenarioTestsRepository: ${SCENARIOS_LOCATION}' >> ${JOB_CONFIG_YAML_PATH}
                                    echo 'testgridYamlLocation: ${TESTGRID_YAML_LOCATION}' >> ${JOB_CONFIG_YAML_PATH}
                                    echo 'properties:' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  PRODUCT_GIT_URL: ${PRODUCT_GIT_URL}' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  PRODUCT_GIT_BRANCH: ${PRODUCT_GIT_BRANCH}' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  PRODUCT_DIST_DOWNLOAD_API: ${PRODUCT_DIST_DOWNLOAD_API}' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  SQL_DRIVERS_LOCATION_UNIX: ${SQL_DRIVERS_LOCATION_UNIX}' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  SQL_DRIVERS_LOCATION_WINDOWS: ${SQL_DRIVERS_LOCATION_WINDOWS}' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  REMOTE_WORKSPACE_DIR_UNIX: ${REMOTE_WORKSPACE_DIR_UNIX}' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  REMOTE_WORKSPACE_DIR_WINDOWS: ${REMOTE_WORKSPACE_DIR_WINDOWS}' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  gitURL: ${PRODUCT_GIT_URL}' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  gitBranch: ${PRODUCT_GIT_BRANCH}' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  productDistDownloadApi: ${PRODUCT_DIST_DOWNLOAD_API}' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  sqlDriversLocationUnix: ${SQL_DRIVERS_LOCATION_UNIX}' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  sqlDriversLocationWindows: ${SQL_DRIVERS_LOCATION_WINDOWS}' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  RemoteWorkspaceDirPosix: ${REMOTE_WORKSPACE_DIR_UNIX}' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  LATEST_PRODUCT_RELEASE_API: ${LATEST_PRODUCT_RELEASE_API}' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  LATEST_PRODUCT_BUILD_ARTIFACTS_API: ${LATEST_PRODUCT_BUILD_ARTIFACTS_API}' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  TEST_MODE: ${TEST_MODE}' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  runOnBranch: "false"' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  WUM_CHANNEL: "${WUM_CHANNEL}"' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  PRODUCT_CODE: "${PRODUCT_CODE}"' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  WUM_PRODUCT_VERSION: "${WUM_PRODUCT_VERSION}"' >> ${JOB_CONFIG_YAML_PATH}
                                    echo '  USE_CUSTOM_TESTNG: "${USE_CUSTOM_TESTNG}"' >> ${JOB_CONFIG_YAML_PATH}
                                    
                                    
				                    echo The job-config.yaml :
                                    cat ${JOB_CONFIG_YAML_PATH}
                                    """

                                stash name: "${JOB_CONFIG_YAML}", includes: "${JOB_CONFIG_YAML}"
                                stash name: "TestGridYaml", includes: "${TESTGRID_YAML_LOCATION}"

                                sh """
                                    cd ${TESTGRID_HOME}/testgrid-dist/${TESTGRID_NAME}
                                    ./testgrid generate-test-plan \
                                        --product ${PRODUCT} \
                                        --file ${JOB_CONFIG_YAML_PATH}
                                """
                                dir("${PWD}") {
                                    stash name: "test-plans", includes: "test-plans/**"
                                }
                            } catch (e) {
                                currentBuild.result = "FAILED"
                            } finally {
                                alert.sendNotification(currentBuild.result, "preparation", "#build_status_verbose")
                            }
                        }
                    }
                }

                stage('parallel-run') {
                    steps {
                        script {
                            def name = "unknown"
                            try {
                                parallel_executor_count = 12
                                if(env.EXECUTOR_COUNT != "null"){
                                    echo "executor count is"+ env.EXECUTOR_COUNT
                                    parallel_executor_count = env.EXECUTOR_COUNT
                                }
                                def tests = testExecutor.getTestExecutionMap(parallel_executor_count)
                                parallel tests
                            } catch (e) {
                                currentBuild.result = "FAILED"
                                alert.sendNotification(currentBuild.result, "Parallel", "#build_status_verbose")
                            }
                        }
                    }
                }
            }

            post {
                always {
                    script {
                        try {
                            sh """
                                cd ${TESTGRID_HOME}/testgrid-dist/${TESTGRID_NAME}
                                ./testgrid finalize-run-testplan \
                                --product ${PRODUCT} --workspace ${PWD}
                            """

                            sh """
                                 cd ${TESTGRID_HOME}/testgrid-dist/${TESTGRID_NAME}
                                ./testgrid generate-report \
                                --product ${PRODUCT} \
                                --groupBy scenario
                            """
                            sh """
                                export DISPLAY=:95.0
                                cd ${TESTGRID_HOME}/testgrid-dist/${TESTGRID_NAME}
                                ./testgrid generate-email \
                                --product ${PRODUCT} \
                                --workspace ${PWD}
                            """
                            awsHelper.uploadCharts()
                            //Send email for failed results.
                            if (fileExists("${PWD}/builds/SummarizedEmailReport.html")) {
                                def emailBody = readFile "${PWD}/builds/SummarizedEmailReport.html"
                                email.send("'${env.JOB_NAME}' Integration Test Results! #(${env.BUILD_NUMBER})", "${emailBody}")
                            } else {
                                echo "No SummarizedEmailReport.html file found!!"
                                email.send("'${env.JOB_NAME}'#(${env.BUILD_NUMBER}) - SummarizedEmailReport.html " +
                                        "file not found", "Could not find the summarized email report ${env.BUILD_URL}. This is an error in " +
                                        "testgrid.")
                            }
                        } catch (e) {
                            currentBuild.result = "FAILED"
                        } finally {
                            alert.sendNotification(currentBuild.result, "completed", "#build_status")
                            alert.sendNotification(currentBuild.result, "completed", "#build_status_verbose")
                        }
                    }
                }
            }
        }
    }
}

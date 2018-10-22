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


import org.wso2.tg.jenkins.PipelineContext
import org.wso2.tg.jenkins.alert.Slack
import org.wso2.tg.jenkins.alert.Email
import org.wso2.tg.jenkins.executors.TestGridExecutor
import org.wso2.tg.jenkins.util.AWSUtils
import org.wso2.tg.jenkins.executors.TestExecutor
import org.wso2.tg.jenkins.Properties
import org.wso2.tg.jenkins.util.RuntimeUtils
import org.wso2.tg.jenkins.util.WorkSpaceUtils


// The pipeline should reside in a call block
def call() {
    // Setting the current pipeline context
    PipelineContext.instance.setContext(this)
    // Initializing environment properties
    def props = Properties.instance
    props.instance.initProperties()

    // For scaling we need to create slave nodes before starting the pipeline and schedule it appropriately
    def alert = new Slack()
    def email = new Email()
    def awsHelper = new AWSUtils()
    def testExecutor = new TestExecutor()
    def tgExecutor = new TestGridExecutor()
    def runtime = new RuntimeUtils()
    def ws = new WorkSpaceUtils()

    pipeline {
        agent {
            node {
                label ""
                customWorkspace "${props.WORKSPACE}"
            }
        }
        tools {
            jdk 'jdk8'
        }

        stages {
            stage('Preparation') {
                steps {
                    script {
                        try {
                            //alert.sendNotification('STARTED', "Initiation", "#build_status_verbose")
                            //alert.sendNotification('STARTED', "Initiation", "#build_status")
                            deleteDir()
                            pwd()
                            // Increasing the TG JVM memory params
                            runtime.increaseTestGridRuntimeMemory("2G", "2G")
                            // Get testgrid.yaml from jenkins managed files
                            configFileProvider(
                                    [configFile(fileId: "${props.PRODUCT}-testgrid-yaml", targetLocation:
                                            "${props.WORKSPACE}/${props.TESTGRID_YAML_LOCATION}")]) {
                            }

                            echo "Creating Job config!!!!"
                            // Creating the job config file
                            ws.createJobConfigYamlFile("${props.JOB_CONFIG_YAML_PATH}")
                            sh """
                                echo The job-config.yaml content :
                                cat ${props.JOB_CONFIG_YAML_PATH}
                            """

                            echo "Generating test plans!!"
                            tgExecutor.generateTesPlans(props.PRODUCT, props.JOB_CONFIG_YAML_PATH)

                            echo "Stashing testplans to be used in different slave nodes"
                            dir("${props.WORKSPACE}") {
                                stash name: "test-plans", includes: "test-plans/**"
                            }
                        } catch (e) {
                            currentBuild.result = "FAILED"
                        } finally {
                            //alert.sendNotification(currentBuild.result, "preparation", "#build_status_verbose")
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
                            if (props.EXECUTOR_COUNT != "null") {
                                echo "executor count is" + props.EXECUTOR_COUNT
                                parallel_executor_count = props.EXECUTOR_COUNT
                            }
                            def tests = testExecutor.getTestExecutionMap(parallel_executor_count)
                            parallel tests
                        } catch (e) {
                            currentBuild.result = "FAILED"
                            //alert.sendNotification(currentBuild.result, "Parallel", "#build_status_verbose")
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
                                export TESTGRID_HOME="${props.TESTGRID_HOME}" 
                                cd ${props.TESTGRID_HOME}/testgrid-dist/${props.TESTGRID_NAME}
                                ./testgrid finalize-run-testplan \
                                --product ${props.PRODUCT} --workspace ${props.WORKSPACE}

                                export DISPLAY=:95.0
                                cd ${props.TESTGRID_HOME}/testgrid-dist/${props.TESTGRID_NAME}
                                ./testgrid generate-email \
                                --product ${props.PRODUCT} \
                                --workspace ${props.WORKSPACE}
                            """
                        awsHelper.uploadCharts()
                        //Send email for failed results.
                        if (fileExists("${props.WORKSPACE}/SummarizedEmailReport.html")) {
                            def emailBody = readFile "${props.WORKSPACE}/SummarizedEmailReport.html"
                            email.send("'${props.PRODUCT}' Integration Test Results! #(${env.BUILD_NUMBER})",
                                    "${emailBody}")
                        } else {
                            echo "No SummarizedEmailReport.html file found!!"
                            email.send("'${props.PRODUCT}'#(${env.BUILD_NUMBER}) - SummarizedEmailReport.html " +
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
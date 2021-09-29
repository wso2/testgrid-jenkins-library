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

import org.wso2.tg.jenkins.Logger
import org.wso2.tg.jenkins.PipelineContext
import org.wso2.tg.jenkins.Properties
import org.wso2.tg.jenkins.alert.Email
import org.wso2.tg.jenkins.alert.Slack
import org.wso2.tg.jenkins.executors.TestExecutor
import org.wso2.tg.jenkins.executors.TestGridExecutor
import org.wso2.tg.jenkins.util.*

// The pipeline should reside in a call block
def call() {
    // Setting the current pipeline context, this should be done initially
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
    def common = new Common()
    def log = new Logger()
    def config = new ConfigUtils()

    pipeline {
        agent {
            node {
                label "${props.AGENT_NODE_LABEL}"
                customWorkspace "${props.WORKSPACE}"
            }
        }
        tools {
            jdk 'jdk8'
        }
        // These variables are needed by the shell scripts when setting up and running tests
        environment {
            TESTGRID_HOME = "${props.TESTGRID_HOME}"
            WUM_UAT_URL = common.getJenkinsCredentials('WUM_UAT_URL')
            WUM_UAT_APPKEY = common.getJenkinsCredentials('WUM_UAT_APPKEY')
            USER_NAME = common.getJenkinsCredentials('WUM_USERNAME')
            PASSWORD = common.getJenkinsCredentials('WUM_PASSWORD')
        }

        stages {
            stage('Preparation') {
                    steps {
                        wrap([$class: 'MaskPasswordsBuildWrapper']) { // to enable mask-password plugin
                            script {
                            currentBuild.result = "SUCCESS"
                            try {
                                alert.sendNotification('STARTED', "Initiation", "#build_status_verbose")
                                alert.sendNotification('STARTED', "Initiation", "#build_status")
                                deleteDir()
                                pwd()
                                // Increasing the TG JVM memory params
                                runtime.increaseTestGridRuntimeMemory("2G", "2G")
                                // Get testgrid.yaml from jenkins managed files
                                if (props.TESTGRID_YAML_URL != null) {
                                    log.info("The testgrid.yaml location: ${props.TESTGRID_YAML_URL}")
                                    withCredentials([string(credentialsId: "GIT_WUM_USERNAME", variable: 'user'),
                                                     string(credentialsId: "GIT_WUM_PASSWORD", variable: 'pass')]) {
                                        sh """
                                        set +x
                                        curl --silent --user $user:$pass -k -o ${props.WORKSPACE}/${
                                            props.TESTGRID_YAML_LOCATION
                                        } ${props.TESTGRID_YAML_URL}
                                    """
                                    }
                                } else {
                                    sh """
                                git clone ${props.TESTGRID_JOB_CONFIG_REPOSITORY}
                                """
                                    def jobConfigExists = fileExists "testgrid-job-configs/${props.PRODUCT}-testgrid.yaml"
                                    log.info("The file location is set as " +
                                            "testgrid-job-configs/${props.PRODUCT}-testgrid.yaml and the exist flag is set to "
                                            + jobConfigExists)
                                    if (jobConfigExists) {
                                        log.info("The testgrid yaml is found in remote repository " +
                                                "testgrid-job-configs/${props.PRODUCT}-testgrid.yaml")
                                        sh """
                                    cp "testgrid-job-configs/${props.PRODUCT}-testgrid.yaml" ${props.WORKSPACE}/${
                                            props.TESTGRID_YAML_LOCATION
                                        }
                                """
                                    } else {
                                        log.info("The testgrid yaml is copied from the configFile provider.")
                                        configFileProvider(
                                                [configFile(fileId: "${props.PRODUCT}-testgrid-yaml", targetLocation:
                                                        "${props.WORKSPACE}/${props.TESTGRID_YAML_LOCATION}")]) {
                                        }
                                    }
                                }

                                def tgYamlContent = readYaml file: "${props.WORKSPACE}/${props.TESTGRID_YAML_LOCATION}"
                                if (tgYamlContent.isEmpty() || tgYamlContent.infrastructureConfig == null) {
                                    throw new Exception("Invalid testgrid.yaml file found. Aborting build.")
                                }
                                // We need to set the repository properties
                                props.EMAIL_TO_LIST = tgYamlContent.emailToList
                                props.EMAIL_TO_LIST_INFRA = tgYamlContent.infraFailureEmailToList
                                props.IAC_PROVIDER = tgYamlContent.infrastructureConfig.iacProvider
                                log.info("Infrastructure as code provider is: $props.IAC_PROVIDER")
                                if (props.EMAIL_TO_LIST == null) {
                                    throw new Exception("emailToList property is not found in testgrid.yaml file")
                                }
                                log.info("Creating Job config in " + props.JOB_CONFIG_YAML_PATH)
                                // Select schedule by using current build information
                                def schedule = tgExecutor.selectSchedule(currentBuild)
                                // Creating the job config file
                                ws.createJobConfigYamlFile("${props.JOB_CONFIG_YAML_PATH}", schedule)
                                sh """
                                echo The job-config.yaml content :
                                cat ${props.JOB_CONFIG_YAML_PATH}
                               
                            """
                                configFileProvider(
                                        [configFile(fileId: "common-configs", targetLocation:
                                                "${props.WORKSPACE}/common-configs.properties")]) {
                                }
                                // Providing nexus settings xml as a config file provider through Jenkins.
                                // This is using only in WUM Test mode.
                                configFileProvider(
                                        [configFile(fileId: "uat-nexus-settings", targetLocation:
                                                "${props.WORKSPACE}/uat-nexus-settings.xml")]) {
                                }

                                def commonConfigs = readProperties file: "${props.WORKSPACE}/common-configs.properties"
                                tgYamlContent = config.addCommonConfigsToTestGridYaml(tgYamlContent, commonConfigs)

                                //check if infra includes have been overridden
                                if(props.INFRA_OVERRIDES != null) {
                                    tgYamlContent.infrastructureConfig.includes = []
                                    for (String item : props.INFRA_OVERRIDES) {
                                        tgYamlContent.infrastructureConfig.includes.add(item.trim())
                                    }
                                }

                                //remove the existing testgrid yaml file before creating the new one
                                sh " rm ${props.WORKSPACE}/${props.TESTGRID_YAML_LOCATION}"
                                //write the new testgrid yaml file after adding new config values
                                writeYaml file: "${props.WORKSPACE}/${props.TESTGRID_YAML_LOCATION}", data: tgYamlContent

                                log.info("Generating infrastructure combination plans for the product : $props.PRODUCT")
                                tgExecutor.generateTesPlans(props.PRODUCT, props.JOB_CONFIG_YAML_PATH)

                                log.info("Stashing test plans to be used in different slave nodes")
                                dir("${props.WORKSPACE}") {
                                    stash name: "test-plans", includes: "test-plans/**"
                                }
                            } catch (e) {
                                currentBuild.result = "FAILURE"
                                echo e.toString()
                            } finally {
                                alert.sendNotification(currentBuild.result, "preparation", "#build_status_verbose")
                            }
                        }

                    }
                }
            }

            stage('parallel-run') {
                    steps {
                        wrap([$class: 'MaskPasswordsBuildWrapper']) {
                            script {
                            log.info("Starting parallel execution stage.")
                            def name = "unknown"
                            try {
                                def tests = testExecutor.getTestExecutionMap(props.EXECUTOR_COUNT)
                                log.info("Running parallel tests")
                                parallel tests
                            } catch (e) {
                                echo "Parallel test plan execution error: " + e.toString()
                                currentBuild.result = "FAILURE"
                                alert.sendNotification(currentBuild.result, "Parallel", "#build_status_verbose")
                            }
                        }
                    }
                }
            }
        }

        post {
            always {
                wrap([$class: 'MaskPasswordsBuildWrapper']) {
                    script {
                        try {
                            log.info("Starting post stage.")
                            tgExecutor.finalizeTestPlans(props.PRODUCT, props.WORKSPACE)
                            tgExecutor.generateEmail(props.PRODUCT, props.WORKSPACE)
                            def properties = readProperties file: "${props.CONFIG_PROPERTY_FILE_PATH}"
                            def testgrid_environment = properties['TESTGRID_ENVIRONMENT']
                            if ("${testgrid_environment}" == "local") {
                                //archive logs locally
                            } else {
                                awsHelper.uploadCharts()
                            }
                            def configUtil = new ConfigUtils()
                            //Send email for failed results.
                            if (fileExists("${props.WORKSPACE}/SummarizedEmailReport.html")) {
                                def emailBody = readFile "${props.WORKSPACE}/SummarizedEmailReport.html"
                                def environment = configUtil.getPropertyFromTestgridConfig("TESTGRID_ENVIRONMENT").toUpperCase()
                                email.send(
                                        "[${environment}][${currentBuild.result}] '${props.PRODUCT}' Test Results!" + " #(${env.BUILD_NUMBER})",
                                        "${emailBody}");

                                //If there is an infra error email generated send it to infra email List recipients
                                if (fileExists("${props.WORKSPACE}/InfraErrorEmail.html")) {
                                    echo "Infra Email to List : ${props.EMAIL_TO_LIST_INFRA}"
                                    def infraErrorEmailBody = readFile "${props.WORKSPACE}/InfraErrorEmail.html"
                                    email.sendInfraEmail(
                                            "[${environment}][${currentBuild.result}] '${props.PRODUCT}' Infra Failure!" + " #(${env.BUILD_NUMBER})",
                                            "${infraErrorEmailBody}");
                                } else {
                                    log.info("No InfraErrorEmail to send!")
                                }

                            } else {
                                log.warn("No SummarizedEmailReport to send!")
                                email.send("'${props.PRODUCT}'#(${env.BUILD_NUMBER}) - SummarizedEmailReport.html " +
                                        "file not found", "Could not find the summarized email report ${env.BUILD_URL}. This is an error in " +
                                        "testgrid.")
                            }
                        } catch (e) {
                            log.warn("Error during post step execution: " + e.getMessage())
                            currentBuild.result = "FAILURE"
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

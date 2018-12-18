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
import org.wso2.tg.jenkins.executors.TestGridExecutor
import org.wso2.tg.jenkins.util.AWSUtils

// The pipeline should reside in a call block
def call() {
    // Setting the current pipeline context, this should be done initially
    PipelineContext.instance.setContext(this)
    // Initializing environment properties
    def props = Properties.instance
    props.instance.initProperties()

    def log = new Logger()
    def executor = new TestGridExecutor()
    def email = new Email()

    pipeline {
        agent {
            node {
                label "slave1"
                customWorkspace "${props.WORKSPACE}"
            }
        }
        tools {
            jdk 'jdk8'
        }

        stages {
            stage('Generate Escalations') {
                steps {
                    script {
                        // Cleaning the workspace
                        deleteDir()
                        def excludeList = ""
                        executor.generateEscalationEmail(props.WORKSPACE, excludeList)
                        log.info("Email generation completed")
                        if (fileExists("${props.WORKSPACE}/EscalationMail.html")) {
                            uploadCharts()
                            def emailBody = readFile "${props.WORKSPACE}/EscalationMail.html"
                            email.send("Build Failure Escalation! #(${env.BUILD_NUMBER})",
                                    "${emailBody}")
                        } else {
                            log.warn("No EscalationMail.html file found!!")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Uploads the generated images
 */
def uploadCharts() {
    def props = Properties.instance
    def awsUtil = new AWSUtils()
    sh """
      aws s3 sync ${props.TESTGRID_HOME}/jobs/${props.PRODUCT}/ \
        ${awsUtil.getS3WorkspaceURL()}/charts/escalations/ \
        --exclude "*" \
        --include "*.png" \
        --exclude 'workspace/*' \
        --acl public-read \
        --only-show-errors
      """
}

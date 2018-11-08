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

package org.wso2.tg.jenkins.executors

import org.wso2.tg.jenkins.Logger
import org.wso2.tg.jenkins.PipelineContext
import org.wso2.tg.jenkins.Properties
import org.wso2.tg.jenkins.util.Common
import org.wso2.tg.jenkins.util.AWSUtils
import org.wso2.tg.jenkins.alert.Slack
import org.wso2.tg.jenkins.util.FileUtils
import org.wso2.tg.jenkins.util.RuntimeUtils

def runPlan(tPlan, testPlanId) {
    def commonUtil = new Common()
    def notifier = new Slack()
    def awsHelper = new AWSUtils()
    def fileUtil = new FileUtils()
    def props = Properties.instance
    def tgExecutor = new TestGridExecutor()
    def runtime = new RuntimeUtils()
    def log = new Logger()

    log.info("WWWWWWWWWWW ========== before")
    readRepositoryUrlsfromYaml("${props.WORKSPACE}/${tPlan}")

    fileUtil.createDirectory("${props.WORKSPACE}/${testPlanId}")
    log.info("Preparing workspace for testplan : " + testPlanId)
    prepareWorkspace(testPlanId)
    //sleep(time:commonUtil.getRandomNumber(10),unit:"SECONDS")
    log.info("Unstashing test-plans and testgrid.yaml to ${props.WORKSPACE}/${testPlanId}")
    runtime.unstashTestPlansIfNotAvailable("${props.WORKSPACE}/testplans")
    writeFile file: "${props.WORKSPACE}/${testPlanId}/workspace/${props.DEPLOYMENT_LOCATION}/deploy.sh", text:
            '#!/bin/sh'


    def name = commonUtil.getParameters("${props.WORKSPACE}/${tPlan}")
    notifier.sendNotification("STARTED", "parallel \n Infra : " + name, "#build_status_verbose")
    try {
        tgExecutor.runTesPlans(props.PRODUCT,
                "${props.WORKSPACE}/${tPlan}", "${props.WORKSPACE}/${testPlanId}")
        //commonUtil.truncateTestRunLog(testPlanId)
    } catch (Exception err) {
        log.error("Error : ${err}")
        currentBuild.result = 'UNSTABLE'
    } finally {
        notifier.sendNotification(currentBuild.result, "Parallel \n Infra : " + name, "#build_status_verbose")
    }
    log.info("RESULT: ${currentBuild.result}")
    awsHelper.uploadToS3(testPlanId)
}

def getTestExecutionMap(parallel_executor_count) {
    def commonUtils = new Common()
    def log = new Logger()
    def props = Properties.instance
    def parallelExecCount = parallel_executor_count as int
    def name = "unknown"
    def tests = [:]
    def files = findFiles(glob: '**/test-plans/*.yaml')
    log.info("Found ${files.length} testplans")
    log.info("Parallel exec count " + parallelExecCount)
    for (int f = 1; f < parallelExecCount + 1 && f <= files.length; f++) {
        def executor = f
        name = commonUtils.getParameters("${props.WORKSPACE}/test-plans/" + files[f - 1].name)
        tests["${name}"] = {
            node {
                stage("Parallel Executor : ${executor}") {
                    script {
                        int processFileCount = 0
                        if (files.length < parallelExecCount) {
                            processFileCount = 1
                        } else {
                            processFileCount = files.length / parallelExecCount
                        }
                        if (executor == parallelExecCount) {
                            for (int i = processFileCount * (executor - 1); i < files.length; i++) {
                                /*IMPORTANT: Instead of using 'i' directly in your logic below, 
                                you should assign it to a new variable and use it.
                                (To avoid same 'i-object' being refered)*/
                                // Execution logic
                                int fileNo = i
                                testplanId = commonUtils.getTestPlanId("${props.WORKSPACE}/test-plans/"
                                                                                        + files[fileNo].name)
                                runPlan(files[i], testplanId)
                            }
                        } else {
                            for (int i = 0; i < processFileCount; i++) {
                                int fileNo = processFileCount * (executor - 1) + i
                                testplanId = commonUtils.getTestPlanId("${props.WORKSPACE}/test-plans/"
                                                                                        + files[fileNo].name)
                                runPlan(files[fileNo], testplanId)
                            }
                        }
                    }
                }
            }
        }
    }
    return tests
}

def prepareWorkspace(testPlanId) {
    def props = Properties.instance
    def log = new Logger()
    log.info(" Creating workspace and builds sub-directories")
   
    sh """
        rm -r -f ${props.WORKSPACE}/${testPlanId}/
        mkdir -p ${props.WORKSPACE}/${testPlanId}
        mkdir -p ${props.WORKSPACE}/${testPlanId}/builds
        mkdir -p ${props.WORKSPACE}/${testPlanId}/workspace
        #Cloning should be done before unstashing TestGridYaml since its going to be injected
        #inside the cloned repository
        cd ${props.WORKSPACE}/${testPlanId}/workspace
        echo cloning infrastructure repository: ${props.INFRASTRUCTURE_REPOSITORY_URL} into ${props.WORKSPACE}/${testPlanId}/${props.INFRA_LOCATION}
        git clone ${props.INFRASTRUCTURE_REPOSITORY_URL} ${props.INFRA_LOCATION}

        echo cloning deployment repository: ${props.DEPLOYMENT_REPOSITORY_URL} into ${props.WORKSPACE}/${testPlanId}/${props.DEPLOYMENT_LOCATION}
        git clone ${props.DEPLOYMENT_REPOSITORY_URL} ${props.DEPLOYMENT_LOCATION}      
        
        echo cloning scenarios repository: ${props.SCENARIOS_REPOSITORY_URL} into ${props.WORKSPACE}/${testPlanId}/${props.SCENARIOS_LOCATION}
        git clone ${props.SCENARIOS_REPOSITORY_URL} ${props.SCENARIOS_LOCATION}     

        echo Workspace directory content:
        ls ${props.WORKSPACE}/${testPlanId}/
    """
    log.info("Copying the ssh key file to workspace : ${props.WORKSPACE}/${testPlanId}/${props.SSH_KEY_FILE_PATH}")
    withCredentials([file(credentialsId: 'DEPLOYMENT_KEY', variable: 'keyLocation')]) {
        sh """
            cp ${keyLocation} ${props.WORKSPACE}/${testPlanId}/${props.SSH_KEY_FILE_PATH}
            chmod 400 ${props.WORKSPACE}/${testPlanId}/${props.SSH_KEY_FILE_PATH}
        """
    }
}

def readRepositoryUrlsfromYaml(def testplan) {

    echo "test plan is -----==== ${testplan}"
    def props = Properties.instance
    String yamlContentAsString = readFile "${testplan}".replace("!!", "#")
    echo "${yamlContentAsString}"
    yamlContentAsString.replace("!!", "abcdefgh")
    echo "DDDDD"
    echo "${yamlContentAsString}"
    def tgYamlContent = readYaml file: "${props.WORKSPACE}/${props.TESTGRID_YAML_LOCATION}"

//  def tgYamlContent = "xxxxxxxxxxxx"
    echo "test plan is -----==== ${tgYamlContent}"
    if (tgYamlContent.isEmpty()) {
        throw new Exception("Testgrid Yaml content is Empty")
    }
    // We need to set the repository properties
    props.EMAIL_TO_LIST = tgYamlContent.emailToList
    props.INFRASTRUCTURE_REPOSITORY_URL = tgYamlContent.infrastructureConfig.provisioners[0]
            .repository
    props.DEPLOYMENT_REPOSITORY_URL = tgYamlContent.scenarioConfig.repository
    props.SCENARIOS_REPOSITORY_URL = tgYamlContent.deploymentConfig.repository
    echo "XXXXXX"
    echo "${props.INFRASTRUCTURE_REPOSITORY_URL}"
    echo "${props.DEPLOYMENT_REPOSITORY_URL}"
    echo "${props.SCENARIOS_REPOSITORY_URL}"
    echo "${tgYamlContent}"
    echo "YYYYYY"
}

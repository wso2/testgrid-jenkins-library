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

import org.wso2.tg.jenkins.util.Common
import org.wso2.tg.jenkins.util.AWSUtils
import org.wso2.tg.jenkins.alert.Slack

def runPlan(tPlan, testPlanId) {
    def commonUtil = new Common()
    def notfier = new Slack()
    def awsHelper = new AWSUtils()
    sh """
        echo Executing Test Plan : ${tPlan} On directory : ${testPlanId}
        echo Creating workspace and builds sub-directories
        rm -r -f ${PWD}/${testPlanId}/
        mkdir -p ${PWD}/${testPlanId}/builds
        mkdir -p ${PWD}/${testPlanId}/workspace
        #Cloning should be done before unstashing TestGridYaml since its going to be injected
        #inside the cloned repository
        echo Cloning ${SCENARIOS_REPOSITORY} into ${PWD}/${testPlanId}/${SCENARIOS_LOCATION}
        cd ${PWD}/${testPlanId}/workspace
        git clone ${SCENARIOS_REPOSITORY}

        echo Cloning ${INFRASTRUCTURE_REPOSITORY} into ${PWD}/${testPlanId}/${INFRA_LOCATION}
        git clone ${INFRASTRUCTURE_REPOSITORY}

        echo Unstashing test-plans and testgrid.yaml to ${PWD}/${testPlanId}
    """
    
    dir("${PWD}/${testPlanId}") {
        unstash name: "${JOB_CONFIG_YAML}"
        unstash name: "test-plans"
        unstash name: "TestGridYaml"
    }

    sh """
        cp /testgrid/testgrid-prod-key.pem ${PWD}/${testPlanId}/workspace/testgrid-key.pem
        chmod 400 ${PWD}/${testPlanId}/workspace/testgrid-key.pem
        echo Workspace directory content:
        ls ${PWD}/${testPlanId}/
        echo Test-plans directory content:
        ls ${PWD}/${testPlanId}/test-plans/
    """

    writeFile file: "${PWD}/${testPlanId}/${INFRA_LOCATION}/deploy.sh", text: '#!/bin/sh'

    def name = commonUtil.getParameters("${PWD}/${testPlanId}/${tPlan}")
    notfier.sendNotification("STARTED", "parallel \n Infra : " + name, "#build_status_verbose")
    try {
        sh """
            echo Running Test-Plan: ${tPlan}
            java -version
            #Need to change directory to root to run the next command properly
            cd /
            .${TESTGRID_HOME}/testgrid-dist/${TESTGRID_NAME}/testgrid run-testplan --product ${PRODUCT} \
            --file ${PWD}/${testPlanId}/${tPlan} --workspace ${PWD}/${testPlanId}        
        """
        commonUtil.truncateTestRunLog(testPlanId)
    } catch (Exception err) {
        echo "Error : ${err}"
        currentBuild.result = 'UNSTABLE'
    } finally {
        notfier.sendNotification(currentBuild.result, "Parallel \n Infra : " + name, "#build_status_verbose")
    }
    
    echo "RESULT: ${currentBuild.result}"
    script {
        awsHelper.uploadToS3(testPlanId)
    }
}

def getTestExecutionMap() {
    def commonUtils = new Common()
    def parallelExecCount = 12
    def name = "unknown"
    def tests = [:]
    def files = findFiles(glob: '**/test-plans/*.yaml')
    for (int f = 1; f < parallelExecCount + 1 && f <= files.length; f++) {
        def executor = f
        name = commonUtils.getParameters("${PWD}/test-plans/" + files[f - 1].name)
        echo name
        tests["${name}"] = {
            node {
                stage("Parallel Executor : ${executor}") {
                    script {
                        int processFileCount = 0;
                        if (files.length < parallelExecCount) {
                            processFileCount = 1;
                        } else {
                            processFileCount = files.length / parallelExecCount;
                        }
                        if (executor == parallelExecCount) {
                            for (int i = processFileCount * (executor - 1); i < files.length; i++) {
                                /*IMPORTANT: Instead of using 'i' directly in your logic below, 
                                you should assign it to a new variable and use it. (To avoid same 'i-object' being refered)*/
                                // Execution logic
                                int fileNo = i
                                testplanId = commonUtils.getTestPlanId("${PWD}/test-plans/" + files[fileNo].name)
                                runPlan(files[i], testplanId)
                            }
                        } else {
                            for (int i = 0; i < processFileCount; i++) {
                                int fileNo = processFileCount * (executor - 1) + i
                                testplanId = commonUtils.getTestPlanId("${PWD}/test-plans/" + files[fileNo].name)
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

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


def runPlan(tPlan, parallelNumber) {
    def commonUtil = new Common()
    def notfier = new Slack()
    def awsHelper = new AWSUtils()
    def name;
    echo "Executing Test Plan : ${tPlan} On directory : ${parallelNumber}"
    echo "*******************************************************************"
    echo "Creating workspace and builds sub-directories"
    sh """
        rm -r -f ${PWD}/${parallelNumber}/
        mkdir -p ${PWD}/${parallelNumber}/builds
        mkdir -p ${PWD}/${parallelNumber}/workspace
        """

    /*
    Cloning should be done before unstashing TestGrid Yaml since its going to be injected inside the cloned repository
    */
  echo "********************************************************************"
    echo "Cloning ${SCENARIOS_REPOSITORY} into ${PWD}/${parallelNumber}/${SCENARIOS_LOCATION}"
    // Clone scenario repo
    //sh "mkdir -p ${PWD}/${parallelNumber}/${SCENARIOS_LOCATION}"
    // dir("${PWD}/${parallelNumber}/${SCENARIOS_LOCATION}") {
    //     git branch: 'master', url: "${SCENARIOS_REPOSITORY}"
    // }
    sh """
        cd ${PWD}/${parallelNumber}/workspace
        ls workspace/
        git clone ${SCENARIOS_REPOSITORY}
    """


     echo "Cloning ${INFRASTRUCTURE_REPOSITORY} into ${PWD}/${parallelNumber}/${INFRA_LOCATION}"
    // Clone infra repo
    // sh "mkdir -p ${PWD}/${parallelNumber}/${INFRA_LOCATION}"
    // dir("${PWD}/${parallelNumber}/${INFRA_LOCATION}") {
    //     // Clone scenario repo
    //     git branch: 'master', url: "${INFRASTRUCTURE_REPOSITORY}"
    // }
     sh """
        cd ${PWD}/${parallelNumber}/workspace
        ls
        git clone ${INFRASTRUCTURE_REPOSITORY}
    """


    echo "*******************************************************************"
    echo "Unstashing test-plans and testgrid.yaml to ${PWD}/${parallelNumber}"
    dir("${PWD}/${parallelNumber}") {
        unstash name: "${JOB_CONFIG_YAML}"
        unstash name: "test-plans"
        unstash name: "TestGridYaml"
        sh"""
        cp /testgrid/testgrid-prod-key.pem ${PWD}/${parallelNumber}/workspace/testgrid-key.pem
        chmod 400 workspace/testgrid-key.pem
        """
        echo "Workspace directory content:"
        sh "ls"
        sh "ls test-plans/"
    }
    
  
     dir("${PWD}/${parallelNumber}") {
        sh "ls */*"
     }

    writeFile file: "${PWD}/${parallelNumber}/${INFRA_LOCATION}/deploy.sh", text: '#!/bin/sh'

    try {
        echo "Running Test-Plan: ${tPlan}"
        sh "java -version"
        name = commonUtil.getParameters("${PWD}/${parallelNumber}/${tPlan}")
        notfier.sendNotification("STARTED", "parallel \n Infra : " + name, "#build_status_verbose")
        // sh """
        //     cd ${PWD}/${parallelNumber}/${SCENARIOS_LOCATION}
        //     git clean -fd
        //     cd ${TESTGRID_HOME}/testgrid-dist/pasindu/${TESTGRID_NAME}
        //     ./testgrid run-testplan --product ${PRODUCT} \
        //     --file ${PWD}/${parallelNumber}/${tPlan} --workspace ${PWD}/${parallelNumber}            
        //     """

        sh """
            cd ${PWD}/${parallelNumber}/${SCENARIOS_LOCATION}
            git clean -fd
            cd /
            ./${TESTGRID_HOME}/testgrid-dist/pasindu/${TESTGRID_NAME}/testgrid run-testplan --product ${PRODUCT} \
            --file ${PWD}/${parallelNumber}/${tPlan} --workspace ${PWD}/${parallelNumber}        
            """    
        script {
            commonUtil.truncateTestRunLog()
        }
    } catch (Exception err) {
        echo "Error : ${err}"
        currentBuild.result = 'UNSTABLE'
    } finally {
        notfier.sendNotification(currentBuild.result, "Parallel \n Infra : " + name, "#build_status_verbose")
    }
    echo "RESULT: ${currentBuild.result}"

    script {
        awsHelper.uploadToS3()
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
                                // Execution logic
                                int parallelNo = i + 1
                                runPlan(files[i], parallelNo.toString())
                            }
                        } else {
                            for (int i = 0; i < processFileCount; i++) {
                                int fileNo = processFileCount * (executor - 1) + i
                                int parallelNo = fileNo + 1
                                runPlan(files[fileNo], parallelNo.toString())
                            }
                        }
                    }
                }
            }
        }
    }
    return tests
}


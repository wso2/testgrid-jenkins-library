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

import org.wso2.tg.jenkins.Properties


def generateTesPlans(def product, def configYaml) {
    def props = Properties.instance
    sh """
    cd ${props.TESTGRID_HOME}/testgrid-dist/${props.TESTGRID_NAME}
    export TESTGRID_HOME="${props.TESTGRID_HOME}"
    ./testgrid generate-test-plan \
        --product ${product} \
        --file ${configYaml}
    echo "Following Test plans were generated : "
    ls -al ${props.WORKSPACE}/test-plans
    """
}

def runTesPlans(def product, def testPlanFilePath, def workspace) {
    def props = Properties.instance
    sh """
            echo Running Test-Plan: ${testPlanFilePath}
            cd ${props.TESTGRID_HOME}/testgrid-dist/${props.TESTGRID_NAME}
            export TESTGRID_HOME="${props.TESTGRID_HOME}"
            ./testgrid run-testplan --product ${product} \
            --file ${testPlanFilePath} --workspace ${workspace}        
    """
}

def finalizeTestPlans() {}

def generateEmail() {}

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
import org.wso2.tg.jenkins.Properties

/**
 * Invokes generate-test-plan command of testgrid.
 *
 * @param product the product to be parsed
 * @param configYaml testgrid yaml file location
 */
def generateTesPlans(def product, def configYaml) {
    def props = Properties.instance
    sh """
        cd ${props.TESTGRID_DIST_LOCATION}/${props.TESTGRID_NAME}
        export TESTGRID_HOME="${props.TESTGRID_HOME}"
        ./testgrid generate-test-plan \
            --product ${product} \
            --file ${configYaml}
        echo "Following Test plans were generated : "
        ls -al ${props.WORKSPACE}/test-plans
    """
}

/**
 * Invokes run-testplan command of testgrid.
 *
 * @param product the product to be parsed
 * @param testPlanFilePath test plan location
 * @param workspace execution workspace
 */
def runTesPlans(def product, def testPlanFilePath, def workspace) {
    def props = Properties.instance
    def log = new Logger()
    log.info("Running Test-Plan: " + testPlanFilePath + " of product " + product + " in the workspace " + workspace)
    sh """
        cd ${props.TESTGRID_DIST_LOCATION}/${props.TESTGRID_NAME}
        export TESTGRID_HOME="${props.TESTGRID_HOME}"
        ./testgrid run-testplan --product ${product} \
            --file ${testPlanFilePath} --workspace ${workspace}        
    """
}

/**
 * Invokes finalize-testplan command of testgrid.
 *
 * @param product the product to be parsed
 * @param workspace execution workspace
 */
def finalizeTestPlans(def product, def workspace) {
    def props = Properties.instance
    sh """
        export TESTGRID_HOME="${props.TESTGRID_HOME}"
        cd ${props.TESTGRID_DIST_LOCATION}/${props.TESTGRID_NAME}
        ./testgrid finalize-run-testplan \
            --product ${product} --workspace ${workspace}
    """
}

/**
 * Invokes generate email command of testgrid.
 *
 * @param product the product to be parsed
 * @param workspace execution workspace
 */
def generateEmail(def product, def workspace) {
    def props = Properties.instance
    sh """
       export DISPLAY=:95.0
       export TESTGRID_HOME="${props.TESTGRID_HOME}"
       cd ${props.TESTGRID_DIST_LOCATION}/${props.TESTGRID_NAME}
       ./testgrid generate-email \
            --product ${product} \
                --workspace ${workspace}
    """
}

/**
 * Invokes escalation generate command of testgrid.
 *
 * @param excludeProduct product names to be excluded
 * @param workspace execution workspace
 */
def generateEscalationEmail(def workspace, def excludeProduct) {
    def props = Properties.instance
    sh """
       export DISPLAY=:95.0
       export TESTGRID_HOME="${props.TESTGRID_HOME}"
       cd ${props.TESTGRID_DIST_LOCATION}/${props.TESTGRID_NAME}
       ./testgrid generate-escalation-email \
            --exclude-products ${excludeProduct} \
                --workspace ${workspace}
    """
}

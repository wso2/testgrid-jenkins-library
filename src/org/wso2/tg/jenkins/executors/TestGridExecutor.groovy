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

import com.cloudbees.groovy.cps.NonCPS
import hudson.model.Cause
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
 * Select the schedule considering build cause. If the build has been triggered by a user return
 * the schedule as manual and otherwise return time based period schedule.
 *
 * @param build current build
 * @return schedule the schedule use for generate infrastructure combinations
 */
@NonCPS
static def selectSchedule(build) {
    def props = Properties.instance
    def log = new Logger()
    // Check if the build was triggered by some jenkins user
    def cause = build.rawBuild.getCause(Cause.UserIdCause.class)
    if (cause == null) {
        log.info("Build cause is not a manual trigger. Hence, using time based schedule selection.")
        return selectTimePeriod()
    }
    def userId = cause.properties.userId
    if (userId != null) {
        return props.MANUAL_SCHEDULE
    }
    return selectTimePeriod()
}

/**
 * Select the schedule considering the present date.
 *
 * @return schedule the schedule use for generate infrastructure combinations
 */
static def selectTimePeriod(){
    def props = Properties.instance
    // Check day of the month and day of the week
    Calendar date = Calendar.getInstance()
    int dayOfMonth = date.get(Calendar.DAY_OF_MONTH)
    int dayOfTheWeek = date.get(Calendar.DAY_OF_WEEK)
    if (dayOfMonth == props.MONTHLY_SCHEDULED_DAY) {
        return props.MONTHLY_SCHEDULE
    } else if (dayOfTheWeek == props.WEEKLY_SCHEDULED_DAY) {
        return props.WEEKLY_SCHEDULE
    } else {
        return props.DAILY_SCHEDULE
    }
}

/**
 * Invokes run-testplan command of testgrid.
 *
 * @param product the product to be parsed
 * @param testPlanFilePath test plan location
 * @param workspace execution workspace
 * @param url Jenkins build url for the test plan
 */
def runTesPlans(def product, def testPlanFilePath, def workspace, def url) {
    def props = Properties.instance
    def log = new Logger()
    log.info("Running Test-Plan: " + testPlanFilePath + " of product " + product + " in the workspace " + workspace)
    sh """
        cd ${props.TESTGRID_DIST_LOCATION}/${props.TESTGRID_NAME}
        export TESTGRID_HOME="${props.TESTGRID_HOME}"
        ./testgrid run-testplan --product ${product} \
            --file ${testPlanFilePath} --workspace ${workspace} --url ${url}       
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
    def properties = readProperties file: "${props.CONFIG_PROPERTY_FILE_PATH}"
    def testgrid_environment = properties['TESTGRID_ENVIRONMENT']
    def display
    if("${testgrid_environment}" == "local"){
        display=":0"
    }else {
        display=":95.0"
    }
    sh """
       export DISPLAY=${display}
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
    def includeWUMScenarioRegEx = "^wum-sce.*\$"
    def properties = readProperties file: "${props.CONFIG_PROPERTY_FILE_PATH}"
    def testgrid_environment = properties['TESTGRID_ENVIRONMENT']
    def display
    if("${testgrid_environment}" == "local"){
        display=":0"
    }else {
        display=":95.0"
    }
    sh """
       export DISPLAY=${display}
       export TESTGRID_HOME="${props.TESTGRID_HOME}"
       cd ${props.TESTGRID_DIST_LOCATION}/${props.TESTGRID_NAME}
       ./testgrid generate-escalation-email \
            --exclude-products ${excludeProduct} \
            --product-include-pattern ${includeWUMScenarioRegEx} \
                --workspace ${workspace}
    """
}

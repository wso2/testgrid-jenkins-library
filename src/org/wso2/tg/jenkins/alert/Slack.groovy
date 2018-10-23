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

package org.wso2.tg.jenkins.alert


/**
 * Sends out a slack notification to a given channel.
 * @param buildStatus status of the build
 * @param phase build phase
 * @param channel channel
 */
def sendNotification(String buildStatus = 'STARTED', phase, channel) {
    // build status of null means successful
    buildStatus = buildStatus ?: 'SUCCESS'

    // Default values
    def colorName = 'RED'
    def colorCode = '#FF0000'
    def subject = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
    def summary = "${subject} (${env.RUN_DISPLAY_URL}) in phase : $phase"

    // Override default values based on build status
    if (buildStatus == 'STARTED') {
        color = 'BLUE'
        colorCode = '#003EFF'
    } else if (buildStatus == 'SUCCESS') {
        color = 'GREEN'
        colorCode = '#00FF00'
    } else if (buildStatus == 'UNSTABLE') {
        color = "YELLOW"
        colorCode = "#FFFF00"
    } else {
        color = 'RED'
        colorCode = '#FF0000'
    }

    // Send notifications
    slackSend(channel: channel, color: colorCode, message: summary)

}



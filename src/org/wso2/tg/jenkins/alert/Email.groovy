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

import org.wso2.tg.jenkins.Logger
import org.wso2.tg.jenkins.Properties

/**
 * Sends Email notifications
 *
 * @param subject subject of the Email
 * @param content body of the Email
 * @param comma separated list of Email addresses
 */
def send(subject,  content , recipients) {
    def log = new Logger()
    def props = Properties.instance
    log.info("Sending mail to " + recipients + " with the subject " + subject)
    emailext(to: "${recipients}",
            subject: subject,
            body: content, mimeType: 'text/html')
}

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
 */
def send(subject,  content) {
    def log = new Logger()
    def props = Properties.instance
    if (props.EMAIL_REPLY_TO != null) {
        emailext(to: "${props.EMAIL_TO_LIST}",
                subject: subject,
                replyTo: "${props.EMAIL_REPLY_TO}",
                body: content, mimeType: 'text/html')
    } else {
        emailext(to: "${props.EMAIL_TO_LIST}",
                subject: subject,
                body: content, mimeType: 'text/html')
    }
}

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
package org.wso2.tg.jenkins.util

import hudson.model.Hudson

// The current job name
def checkEscalation(jobName) {
    getJobByName(jobName).builds.findAll{build ->
        echo "${build}"
    }
}

/**
 * Get Jenkins job object
 * @param jobName job name
 * @return job object that matches jobName
 */
def getJobByName(jobName){
    for(item in Hudson.instance.items) {
        if(item.name == jobName){
            return item
        }
    }
}
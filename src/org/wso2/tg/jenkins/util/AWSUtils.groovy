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

import com.cloudbees.groovy.cps.NonCPS
import org.wso2.tg.jenkins.Properties

def uploadToS3(testPlanId) {
    def props = Properties.instance
    sh """
      aws s3 sync ${props.TESTGRID_HOME}/jobs/${props.PRODUCT}/${testPlanId}/ ${getS3WorkspaceURL()}/artifacts/jobs/${props.PRODUCT}/builds/${testPlanId} --include "*" --exclude 'workspace/*'
      """
}

def uploadCharts() {
    def props = Properties.instance
    sh """
      aws s3 sync ${props.TESTGRID_HOME}/jobs/${props.PRODUCT}/builds/ ${getS3WorkspaceURL()}/charts/${props.PRODUCT}/ 
--exclude "*" --include "*.png" --acl public-read
      """
}

private def getS3WorkspaceURL() {
    // We need to upload all the artifacts to product workspace
    return "s3://" + getS3BucketName()
}

private def getS3BucketName() {
    def props = Properties.instance
    def properties = readProperties file: "${props.CONFIG_PROPERTY_FILE_PATH}"
    def bucket = properties['AWS_S3_BUCKET_NAME']
    if ("${bucket}" == "null") {
        bucket = "unknown"
    }
    return bucket
}
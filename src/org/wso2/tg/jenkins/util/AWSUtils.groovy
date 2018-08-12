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

def getS3BucketName() {
    loadProperties()
    def bucket = properties['AWS_S3_BUCKET_NAME']
    if ("${bucket}" == "null") {
        bucket = "unknown"
    }
    return bucket
}

def uploadToS3() {
    def s3BucketName = getS3BucketName()
    sh """
      aws s3 sync ${TESTGRID_HOME}/jobs/${PRODUCT}/builds/ s3://${s3BucketName}/artifacts/jobs/${PRODUCT}/builds --include "*"
      """
}

def uploadCharts() {
    def s3BucketName = getS3BucketName()
    sh """
      aws s3 sync ${TESTGRID_HOME}/jobs/${PRODUCT}/builds/ s3://${buckerName}/charts/${PRODUCT}/ --exclude "*" --include "*.png" --acl public-read
      """
}

def loadProperties() {
    node {
        properties = readProperties file: "${TESTGRID_HOME}/config.properties"
    }
}
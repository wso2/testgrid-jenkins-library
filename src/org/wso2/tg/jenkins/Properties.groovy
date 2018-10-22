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
package org.wso2.tg.jenkins

@Singleton
class Properties {

    static def TESTGRID_NAME                = "WSO2-TestGrid"
    static def TESTGRID_DIST_LOCATION       = "/testgrid/testgrid-home/testgrid-dist/"
    static def TESTGRID_HOME                = "/testgrid/testgrid-home/"
    static def JOB_CONFIG_YAML              = "job-config.yaml"
    static def SQL_DRIVERS_LOCATION_UNIX    ="/home/centos/sql-drivers/"
    static def SQL_DRIVERS_LOCATION_WINDOWS ="/testgrid/sql-drivers"
    static def REMOTE_WORKSPACE_DIR_UNIX    ="/opt/wso2/workspace"
    static def REMOTE_WORKSPACE_DIR_WINDOWS ="c:/testgrid/workspace"
    static def DEPLOYMENT_LOCATION          ="workspace/testgrid"
    static def SCENARIOS_LOCATION           ="workspace/apim-test-integration"
    static def CONFIG_PROPERTY_FILE_PATH    = TESTGRID_HOME + "/config.properties"

    // Job Properties which are set when init is called
    static def PRODUCT
    static def TESTGRID_YAML_LOCATION
    static def AWS_ACCESS_KEY_ID
    static def AWS_SECRET_ACCESS_KEY
    static def TOMCAT_USERNAME
    static def TOMCAT_PASSWORD
    static def TEST_MODE
    static def WUM_UAT_URL
    static def WUM_UAT_APP_KEY
    static def USER_NAME
    static def PASSWORD
    static def GIT_WUM_USERNAME
    static def GIT_WUM_PASSWORD
    static def JOB_CONFIG_YAML_PATH
    static def PRODUCT_GIT_URL
    static def PRODUCT_GIT_BRANCH
    static def PRODUCT_DIST_DOWNLOAD_API
    static def WUM_CHANNEL
    static def PRODUCT_CODE
    static def WUM_PRODUCT_VERSION
    static def USE_CUSTOM_TESTNG
    static def EXECUTOR_COUNT
    static def INFRA_LOCATION
    static def LATEST_PRODUCT_RELEASE_API
    static def LATEST_PRODUCT_BUILD_ARTIFACTS_API
    static def WORKSPACE
    static def SCENARIOS_REPOSITORY
    static def INFRASTRUCTURE_REPOSITORY

    def initProperties() {

        PRODUCT = getJobProperty(Constants.PRODUCT)
        WORKSPACE = TESTGRID_HOME + "/jobs/" + PRODUCT
        TESTGRID_YAML_LOCATION = "/testgrid" + ".yaml"
        JOB_CONFIG_YAML_PATH = WORKSPACE + "/" + JOB_CONFIG_YAML
        TEST_MODE = getJobProperty(Constants.TEST_MODE)
        GIT_WUM_USERNAME = getCredentials('GIT_WUM_USERNAME')
        GIT_WUM_PASSWORD = getCredentials('GIT_WUM_PASSWORD')
        PRODUCT_GIT_URL = getProductGitUrl()
        PRODUCT_GIT_BRANCH = getJobProperty(Constants.PRODUCT_GIT_BRANCH)
        PRODUCT_DIST_DOWNLOAD_API = getJobProperty(Constants.PRODUCT_DIST_DOWNLOAD_API)
        WUM_CHANNEL = getJobProperty(Constants.WUM_CHANNEL, false)
        PRODUCT_CODE = getJobProperty(Constants.PRODUCT_CODE, false)
        WUM_PRODUCT_VERSION = getJobProperty(Constants.WUM_PRODUCT_VERSION, false)
        USE_CUSTOM_TESTNG = getJobProperty(Constants.USE_CUSTOM_TESTNG)
        EXECUTOR_COUNT = getJobProperty(Constants.EXECUTOR_COUNT)
        AWS_ACCESS_KEY_ID = getCredentials('AWS_ACCESS_KEY_ID')
        AWS_SECRET_ACCESS_KEY = getCredentials('AWS_SECRET_ACCESS_KEY')
        TOMCAT_USERNAME = getCredentials('TOMCAT_USERNAME')
        TOMCAT_PASSWORD = getCredentials('TOMCAT_PASSWORD')
        WUM_UAT_URL = getCredentials('WUM_UAT_URL')
        WUM_UAT_APP_KEY = getCredentials('WUM_UAT_APPKEY')
        USER_NAME = getCredentials('WUM_USERNAME')
        PASSWORD = getCredentials('WUM_PASSWORD')
        INFRA_LOCATION = getJobProperty(Constants.INFRA_LOCATION)
        LATEST_PRODUCT_RELEASE_API = getJobProperty(Constants.LATEST_PRODUCT_RELEASE_API)
        LATEST_PRODUCT_BUILD_ARTIFACTS_API = getJobProperty(Constants.LATEST_PRODUCT_BUILD_ARTIFACTS_API)
        SCENARIOS_REPOSITORY = getJobProperty(Constants.SCENARIOS_REPOSITORY)
        INFRASTRUCTURE_REPOSITORY = getJobProperty(Constants.INFRASTRUCTURE_REPOSITORY)
    }

    /**
     * Validate mandatory properties and return property value.
     *
     * @propertyMap map of properties
     * @property property to be validated and read
     * @isMandatory specify whether the property is mandatory
     */
    private def getJobProperty(def property, boolean isMandatory = true) {
        def ctx = PipelineContext.getContext()
        def propertyMap = ctx.currentBuild.getRawBuild().getEnvironment()
        def prop = propertyMap.get(property)
        if (prop == null || prop.trim() == "" && isMandatory) {
            ctx.echo "A mandatory prop " + property + " is empty or null"
            throw new Exception("A mandatory property " + property + " is empty or null")
        }
        ctx.echo "Property : " + property + " value is set as " + prop
        return prop
    }

    private def getProductGitUrl() {
        def ctx = PipelineContext.getContext()
        def propertyMap = ctx.currentBuild.getRawBuild().getEnvironment()
        //Constructing the product git url if test mode is wum. Adding the Git username and password into the product git url.
        def productGitUrl
        if (TEST_MODE == "WUM") {
            def url = propertyMap.get(Constants.PRODUCT_GIT_URL)
            def values = url.split('//g')
            productGitUrl = "${values[0]}//${GIT_WUM_USERNAME}:${GIT_WUM_PASSWORD}@g${values[1]}"
        } else {
            productGitUrl = propertyMap.get(Constants.PRODUCT_GIT_URL)
        }
        return productGitUrl
    }

    private def getCredentials(def key, boolean isMandatory = true){
        def ctx = PipelineContext.getContext()
        def cred = ctx.credentials(key).toString()
        if (cred == null || cred.trim() == "" && isMandatory) {
            ctx.echo "A mandatory credential is empty or null " + key
            throw new Exception("A mandatory property " + key + " is empty or null")
        }
        ctx.echo "Credential for key : " + key + " is found."
        return cred
    }
}

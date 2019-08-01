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

import org.wso2.tg.jenkins.util.Common

@Singleton
class Properties {

    final static def TESTGRID_NAME                = "WSO2-TestGrid"
    final static def TESTGRID_HOME                = "/testgrid/testgrid-home"
    final static def TESTGRID_DIST_LOCATION       = TESTGRID_HOME + "/testgrid-dist"
    final static def JOB_CONFIG_YAML              = "job-config.yaml"
    final static def SQL_DRIVERS_LOCATION_UNIX    ="/opt/testgrid/sql-drivers/"
    final static def SQL_DRIVERS_LOCATION_WINDOWS ="/testgrid/sql-drivers"
    final static def REMOTE_WORKSPACE_DIR_UNIX    ="/opt/testgrid/workspace"
    final static def REMOTE_WORKSPACE_DIR_WINDOWS ="c:/testgrid/workspace"
    final static def CONFIG_PROPERTY_FILE_PATH    = TESTGRID_HOME + "/config.properties"
    final static def DEFAULT_EXECUTOR_COUNT       = 12
    final static def SSH_KEY_FILE_PATH            = "workspace/testgrid-key.pem"
    final static def SSH_KEY_FILE_PATH_INTG       = "testgrid-key.pem"
    final static def TESTGRID_JOB_CONFIG_REPOSITORY = "https://github.com/wso2-incubator/testgrid-job-configs.git"
    final static def INFRA_LOCATION               = "InfraRepository"
    final static def DEPLOYMENT_LOCATION          = "DeploymentRepository"
    final static def SCENARIOS_LOCATION           = "ScenariosRepository"
    final static def GKE_ACC_FILE_PATH            = "data-bucket/key.json"
    final static def GKE_K8S_SECRET_TLS_CERT   = "data-bucket/testgrid-certs-v2.crt"
    final static def GKE_K8S_SECRET_TLS_KEY = "data-bucket/testgrid-certs-v2.key"
    final static def MANUAL_SCHEDULE              = "manual"
    final static def DAILY_SCHEDULE               = "daily"
    final static def WEEKLY_SCHEDULE              = "weekly"
    final static def MONTHLY_SCHEDULE             = "monthly"
    final static def MONTHLY_SCHEDULED_DAY        = 1
    final static def WEEKLY_SCHEDULED_DAY         = Calendar.MONDAY

    // Job Properties which are set when init is called
    static def PRODUCT
    static def TESTGRID_YAML_LOCATION
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
    static def LATEST_PRODUCT_RELEASE_API
    static def LATEST_PRODUCT_BUILD_ARTIFACTS_API
    static def WORKSPACE
    static def TESTGRID_YAML_URL
    static def SCENARIO_CONFIGS = []

    static def INFRASTRUCTURE_REPOSITORY_URL
    static def INFRASTRUCTURE_REPOSITORY_BRANCH

    static def DEPLOYMENT_REPOSITORY_URL
    static def DEPLOYMENT_REPOSITORY_BRANCH

    static def EMAIL_TO_LIST
    static def EMAIL_TO_LIST_INFRA
    static def EMAIL_REPLY_TO

    static def IAC_PROVIDER

    /**
     * Initializing the properties
     */
    def initProperties() {

        PRODUCT = getJobProperty("JOB_BASE_NAME")
        WORKSPACE = TESTGRID_HOME + "/jobs/" + PRODUCT
        TESTGRID_YAML_LOCATION = "/testgrid" + ".yaml"
        JOB_CONFIG_YAML_PATH = WORKSPACE + "/" + JOB_CONFIG_YAML
        TEST_MODE = getJobProperty("TEST_MODE", false)
        GIT_WUM_USERNAME = getCredentials("GIT_WUM_USERNAME")
        GIT_WUM_PASSWORD = getCredentials("GIT_WUM_PASSWORD")
        PRODUCT_GIT_URL = getProductGitUrl()
        PRODUCT_GIT_BRANCH = getJobProperty("PRODUCT_GIT_BRANCH", false)
        PRODUCT_DIST_DOWNLOAD_API = getJobProperty("PRODUCT_DIST_DOWNLOAD_API", false)
        WUM_CHANNEL = getJobProperty("WUM_CHANNEL", false)
        PRODUCT_CODE = getJobProperty("PRODUCT_CODE", false)
        WUM_PRODUCT_VERSION = getJobProperty("WUM_PRODUCT_VERSION", false)
        USE_CUSTOM_TESTNG = getJobProperty("USE_CUSTOM_TESTNG", false)
        EXECUTOR_COUNT = getExecutorCount("EXECUTOR_COUNT")
        WUM_UAT_URL = getCredentials("WUM_UAT_URL", false)
        WUM_UAT_APP_KEY = getCredentials("WUM_UAT_APPKEY", false)
        USER_NAME = getCredentials("WUM_USERNAME", false)
        PASSWORD = getCredentials("WUM_PASSWORD", false)
        LATEST_PRODUCT_RELEASE_API = getJobProperty("LATEST_PRODUCT_RELEASE_API", false)
        LATEST_PRODUCT_BUILD_ARTIFACTS_API = getJobProperty("LATEST_PRODUCT_BUILD_ARTIFACTS_API", false)
        INFRASTRUCTURE_REPOSITORY_URL = getJobProperty("INFRASTRUCTURE_REPOSITORY", false)
        DEPLOYMENT_REPOSITORY_URL = getJobProperty("DEPLOYMENT_REPOSITORY", false)
        EMAIL_TO_LIST = getJobProperty("EMAIL_TO_LIST", false)
        EMAIL_TO_LIST_INFRA = getJobProperty("EMAIL_TO_LIST_INFRA", false)
        EMAIL_REPLY_TO = getJobProperty("EMAIL_REPLY_TO", false);
        TESTGRID_YAML_URL = getJobProperty("TESTGRID_YAML_URL", false)
        IAC_PROVIDER= getJobProperty("IAC_PROVIDER",false)
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
        if ((prop == null || prop.trim() == "") && isMandatory) {
            ctx.echo "A mandatory prop " + property + " is empty or null"
            throw new Exception("A mandatory property " + property + " is empty or null")
        }
        return prop
    }

    /**
     * Construct the product URL based on the test mode.
     * @return the product git URL
     */
    private def getProductGitUrl() {
        def ctx = PipelineContext.getContext()
        def propertyMap = ctx.currentBuild.getRawBuild().getEnvironment()
        //Constructing the product git url if test mode is wum. Adding the Git username and password into the product git url.
        def productGitUrl
        if (TEST_MODE == "WUM") {
            def url = propertyMap.get("PRODUCT_GIT_URL")
            def values = url.split('//g')
            productGitUrl = "${values[0]}//${GIT_WUM_USERNAME}:${GIT_WUM_PASSWORD}@g${values[1]}"
        } else {
            productGitUrl = propertyMap.get("PRODUCT_GIT_URL")
        }
        return productGitUrl
    }

    private def getExecutorCount(def key) {
        def executorCount = getJobProperty(key, false)
        if (executorCount == null || executorCount.trim() == "") {
           executorCount = DEFAULT_EXECUTOR_COUNT
          //TODO parallel execution is broken when the parallel count is > 12
        }
        return executorCount
    }

    private def getCredentials(def key, boolean isMandatory = true){
        def ctx = PipelineContext.getContext()
        def common = new Common()
        def cred = common.getJenkinsCredentials(key)
        if (cred == null || cred.trim() == "" && isMandatory) {
            ctx.echo "A mandatory credential is empty or null " + key
            throw new Exception("A mandatory property " + key + " is empty or null")
        }
        return cred
    }
}

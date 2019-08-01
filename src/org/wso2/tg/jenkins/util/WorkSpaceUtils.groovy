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

import org.wso2.tg.jenkins.Logger
import org.wso2.tg.jenkins.Properties

/**
 * Create the jobConfig.yaml file
 *
 * @param filePath full qualified path of jobconfig.yaml
 */
def createJobConfigYamlFile(def filePath, def schedule) {

    def props = Properties.instance
    def log = new Logger()
    // TODO: this can be improved with inbuilt groovy support
    // https://jenkins.io/doc/pipeline/steps/pipeline-utility-steps/#writeyaml-write-a-yaml
    sh """
    set +x
    echo 'keyFileLocation: "${props.SSH_KEY_FILE_PATH}"' > ${filePath}
    echo 'infrastructureRepository: "workspace/${props.INFRA_LOCATION}/"' >> ${filePath}
    echo 'deploymentRepository: "workspace/${props.DEPLOYMENT_LOCATION}/"' >> ${filePath}
    echo 'scenarioTestsRepository: "workspace/${props.SCENARIOS_LOCATION}"' >> ${filePath}
    echo 'testgridYamlLocation: "${props.TESTGRID_YAML_LOCATION}"' >> ${filePath}
    echo 'schedule: "${schedule}"' >> ${filePath} 
    echo 'properties:' >> ${filePath}
    echo '  PRODUCT_GIT_URL: "${props.PRODUCT_GIT_URL}"' >> ${filePath}
    echo '  PRODUCT_GIT_BRANCH: "${props.PRODUCT_GIT_BRANCH}"' >> ${filePath}
    echo '  PRODUCT_DIST_DOWNLOAD_API: "${props.PRODUCT_DIST_DOWNLOAD_API}"' >> ${filePath}
    echo '  SQL_DRIVERS_LOCATION_UNIX: "${props.SQL_DRIVERS_LOCATION_UNIX}"' >> ${filePath}
    echo '  SQL_DRIVERS_LOCATION_WINDOWS: "${props.SQL_DRIVERS_LOCATION_WINDOWS}"' >> ${filePath}
    echo '  REMOTE_WORKSPACE_DIR_UNIX: "${props.REMOTE_WORKSPACE_DIR_UNIX}"' >> ${filePath}
    echo '  REMOTE_WORKSPACE_DIR_WINDOWS: "${props.REMOTE_WORKSPACE_DIR_WINDOWS}"' >> ${filePath}
    echo '  gitURL: "${props.PRODUCT_GIT_URL}"' >> ${filePath}
    echo '  gitBranch: "${props.PRODUCT_GIT_BRANCH}"' >> ${filePath}
    echo '  productDistDownloadApi: "${props.PRODUCT_DIST_DOWNLOAD_API}"' >> ${filePath}
    echo '  sqlDriversLocationUnix: "${props.SQL_DRIVERS_LOCATION_UNIX}"' >> ${filePath}
    echo '  sqlDriversLocationWindows: "${props.SQL_DRIVERS_LOCATION_WINDOWS}"' >> ${filePath}
    echo '  RemoteWorkspaceDirPosix: "${props.REMOTE_WORKSPACE_DIR_UNIX}"' >> ${filePath}
    echo '  LATEST_PRODUCT_RELEASE_API: "${props.LATEST_PRODUCT_RELEASE_API}"' >> ${filePath}
    echo '  LATEST_PRODUCT_BUILD_ARTIFACTS_API: "${props.LATEST_PRODUCT_BUILD_ARTIFACTS_API}"' >> ${filePath}
    echo '  TEST_MODE: "${props.TEST_MODE}"' >> ${filePath}
    echo '  runOnBranch: "false"' >> ${filePath}
    echo '  WUM_CHANNEL: "${props.WUM_CHANNEL}"' >> ${filePath}
    echo '  PRODUCT_CODE: "${props.PRODUCT_CODE}"' >> ${filePath}
    echo '  WUM_PRODUCT_VERSION: "${props.WUM_PRODUCT_VERSION}"' >> ${filePath}
    echo '  USE_CUSTOM_TESTNG: "${props.USE_CUSTOM_TESTNG}"' >> ${filePath}
    """
}

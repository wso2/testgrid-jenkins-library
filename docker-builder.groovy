#!groovy
/*
* Copyright (c) 2025 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
*
*/

// Input parameters
String wso2_product = params.wso2_product
String wso2_product_version = params.wso2_product_version
String tag = params.tag
String update_level = params.update_level
Boolean skip_update = params.skip_update
String os = params.os
String s3_bucket = params.s3_bucket
String docker_registry = params.docker_registry
String docker_registry_credential = params.docker_registry_credential
String u2_credential = params.u2_credential

// Default values
String dockerDirectory = "docker"
String dockerRepoBranch = "master"
String dockerRepoUrl = "https://github.com/wso2/docker-apim.git"
// Git
String githubCredentialId = "WSO2_GITHUB_TOKEN"
String toolsDirectory = "tools"
String resourceDirectory = "testgrid-intg"
def product_name_map = [
    'wso2am': 'apim',
    'wso2am-acp': 'apim-acp',
    'wso2am-tm': 'apim-tm',
    'wso2am-universal-gw': 'apim-universal-gw',
]

pipeline {
    agent {label 'pipeline-agent'}

    environment {
        WSO2_UPDATES_UPDATE_LEVEL_STATE = "VERIFYING"
    }

    stages {
        stage('Clone repos') {
            steps {
                script {
                    dir(dockerDirectory) {
                        git branch: "${dockerRepoBranch}",
                        credentialsId: githubCredentialId,
                        url: "${dockerRepoUrl}"
                    }
                }
            }
        }
        stage('download-product-packs-from-s3') {
            steps {
                script {
                    sh """
                    export WSO2_PRODUCT='$wso2_product'
                    export WSO2_PRODUCT_VERSION='$wso2_product_version'
                    # aws s3 cp --quiet s3://${s3_bucket}/packs/${WSO2_PRODUCT}/${WSO2_PRODUCT_VERSION}/${WSO2_PRODUCT}-${WSO2_PRODUCT_VERSION}.zip .
                    aws s3 cp --quiet s3://${s3_bucket}/packs/${WSO2_PRODUCT}-${WSO2_PRODUCT_VERSION}.zip .
                    unzip -q ${WSO2_PRODUCT}-${WSO2_PRODUCT_VERSION}.zip
                    rm -rf ${WSO2_PRODUCT}-${WSO2_PRODUCT_VERSION}.zip
                    """
                }
            }
        }
        stage('Download-update-tool') {
            steps {
                script {
                    dir("${toolsDirectory}") {
                        sh """
                        aws s3 cp --quiet s3://${s3_bucket}/wso2update_linux .
                        """
                    }
                }
            }
        }
        stage('update-product-pack') {
            when {
                expression { skip_update == false } 
            }
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: u2_credential, passwordVariable: 'WUM_PASSWORD', usernameVariable: 'WUM_USERNAME')]) {
                        if (!fileExists("$WSO2_PRODUCT-$WSO2_PRODUCT_VERSION/bin/wso2update_linux")) {
                            println "wso2update_linux not found in product directory. Copying from tools directory."
                            sh "cp ${toolsDirectory}/${resourceDirectory}/wso2update_linux $WSO2_PRODUCT-$WSO2_PRODUCT_VERSION/bin/"
                        }
                        def statusCode = sh(
                                script: """
                                chmod +x $WSO2_PRODUCT-$WSO2_PRODUCT_VERSION/bin/wso2update_linux
                                $WSO2_PRODUCT-$WSO2_PRODUCT_VERSION/bin/wso2update_linux version
                                export UPDATE_LEVEL='$update_level'
                                if [ $UPDATE_LEVEL -gt 0 ];
                                then
                                    $WSO2_PRODUCT-$WSO2_PRODUCT_VERSION/bin/wso2update_linux --username '$WUM_USERNAME' --password '$WUM_PASSWORD' --level '$UPDATE_LEVEL' --backup ./
                                else
                                    $WSO2_PRODUCT-$WSO2_PRODUCT_VERSION/bin/wso2update_linux --username '$WUM_USERNAME' --password '$WUM_PASSWORD' --backup ./
                                fi
                                """,
                                returnStatus: true)
                        if (statusCode == 0) {
                            echo 'exit-code(0): Operation successful'
                            currentBuild.result = 'SUCCESS'
                        } else if (statusCode == 1) {
                            echo 'exit-code(1): Default error'
                            currentBuild.result = 'FAILURE'
                            sh "exit 1"
                        } else if (statusCode == 2) {
                            echo 'exit-code(2): Self update'
                            statusCode = sh(
                                    script: """
                                chmod +x $WSO2_PRODUCT-$WSO2_PRODUCT_VERSION/bin/wso2update_linux
                                $WSO2_PRODUCT-$WSO2_PRODUCT_VERSION/bin/wso2update_linux version
                                export UPDATE_LEVEL='$update_level'
                                if [ $UPDATE_LEVEL -gt 0 ];
                                then
                                    $WSO2_PRODUCT-$WSO2_PRODUCT_VERSION/bin/wso2update_linux --username '$WUM_USERNAME' --password '$WUM_PASSWORD' --level '$UPDATE_LEVEL' --no-backup
                                else
                                    $WSO2_PRODUCT-$WSO2_PRODUCT_VERSION/bin/wso2update_linux --username '$WUM_USERNAME' --password '$WUM_PASSWORD' --no-backup
                                fi
                                """,
                                    returnStatus: true)
                            echo 'Retrying'
                            if (statusCode == 0) {
                                echo 'exit-code(0): Operation successful'
                                currentBuild.result = 'SUCCESS'
                            } else {
                                currentBuild.result = 'FAILURE'
                                sh "exit 1"
                            }
                        } else if (statusCode == 3) {
                            echo 'exit-code(3): Conflict(s) encountered'
                            currentBuild.result = 'FAILURE'
                            sh "exit 1"
                        } else if (statusCode == 4) {
                            echo 'exit-code(4): Reverted'
                            currentBuild.result = 'FAILURE'
                            sh "exit 1"
                        } else {
                            echo 'Unknown exit code'
                            currentBuild.result = 'FAILURE'
                            sh "exit 1"
                        }
                    }
                }
            }
        }
        stage('host-packs-locally') {
            steps {
                script {
                    sh """
                        export WSO2_PRODUCT='$wso2_product'
                        export WSO2_PRODUCT_VERSION='$wso2_product_version'

                        rm -rf ${WSO2_PRODUCT}-${WSO2_PRODUCT_VERSION}/bin/update_darwin ${WSO2_PRODUCT}-${WSO2_PRODUCT_VERSION}/bin/update_linux ${WSO2_PRODUCT}-${WSO2_PRODUCT_VERSION}/bin/update_windows.exe
                        rm -rf ${WSO2_PRODUCT}-${WSO2_PRODUCT_VERSION}/updates/wum

                        zip -rq ${WSO2_PRODUCT}-${WSO2_PRODUCT_VERSION}.zip ${WSO2_PRODUCT}-${WSO2_PRODUCT_VERSION}
                        
                        python3 -m http.server 8889 &
                    """
                }
            }
        }

        stage('docker-build') {
            steps {
                script {
                    try {
                        withCredentials([usernamePassword(credentialsId: docker_registry_credential, passwordVariable: 'DOCKER_PASSWORD', usernameVariable: 'DOCKER_USERNAME')]) {
                            String UPDATED_PRODUCT_PACK_HOST_LOCATION_URL = "http://localhost:8889"
                            dir("${dockerDirectory}") {
                                sh """
                                cd dockerfiles/${os}/${product_name_map[wso2_product]}
                                docker login -u ${DOCKER_USERNAME} -p ${DOCKER_PASSWORD} ${docker_registry}
                                docker build -t ${docker_registry}/${wso2_product}:${tag} . --build-arg WSO2_SERVER_DIST_URL=${UPDATED_PRODUCT_PACK_HOST_LOCATION_URL}/${wso2_product}-${wso2_product_version}.zip
                                docker tag ${docker_registry}/${wso2_product}:${tag} 
                                docker push ${docker_registry}/${wso2_product}:${tag}
                                echo "Docker image ${docker_registry}/${wso2_product}:${tag} pushed successfully"
                                """
                            }
                        }
                    } catch (Exception e) {
                        echo "Error occurred during Docker build: ${e.message}"
                        currentBuild.result = 'FAILURE'
                    } finally {
                        // Clean up the server
                        sh """
                            kill -9 \$(lsof -t -i:8889)
                            rm -rf ${WSO2_PRODUCT}-${WSO2_PRODUCT_VERSION}.zip
                            rm -rf ${WSO2_PRODUCT}-${WSO2_PRODUCT_VERSION}
                        """
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                try {
                    println "Cleaning up the workspace..."
                    cleanWs()
                } catch (Exception e) {
                    echo "Workspace cleanup failed: ${e.message}"
                }
            }
        }
    }
}
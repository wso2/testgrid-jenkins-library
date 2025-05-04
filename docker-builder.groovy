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
String project = params.project
String wso2_product = params.wso2_product
String wso2_product_version = params.wso2_product_version
String tag = params.tag
String update_level = params.update_level
Boolean skip_update = params.skip_update
String os = params.os
String s3_bucket = params.s3_bucket
String docker_registry = params.docker_registry
String docker_registry_username = params.docker_registry_username
String docker_registry_password = params.docker_registry_password
String db_driver_url = params.db_driver_url
Boolean use_staging = params.use_staging

// Default values
String wso2_product_full_name = "${project}-${wso2_product}"
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
    agent {label 'pipeline-kubernetes-agent'}

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
                    withCredentials([string(credentialsId: 'AWS_ACCESS_KEY_ID', variable: 'accessKey'),
                    string(credentialsId: 'AWS_SECRET_ACCESS_KEY', variable: 'secretAccessKey')]) {
                        sh """
                        export AWS_ACCESS_KEY_ID='$accessKey'
                        export AWS_SECRET_ACCESS_KEY='$secretAccessKey'
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
        }
        stage('Download-update-tool') {
            steps {
                script {
                    withCredentials([string(credentialsId: 'AWS_ACCESS_KEY_ID', variable: 'accessKey'),
                    string(credentialsId: 'AWS_SECRET_ACCESS_KEY', variable: 'secretAccessKey')]) {
                        dir("${toolsDirectory}") {
                            sh """
                            export AWS_ACCESS_KEY_ID='$accessKey'
                            export AWS_SECRET_ACCESS_KEY='$secretAccessKey'
                            aws s3 cp --quiet s3://${s3_bucket}/${resourceDirectory}/wso2update_linux .
                            """
                        }
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
                    withCredentials([string(credentialsId: 'WUM_USERNAME', variable: 'WUM_USERNAME'),
                        string(credentialsId: 'WUM_PASSWORD', variable: 'WUM_PASSWORD'),]) {
                        if (!fileExists("$WSO2_PRODUCT-$WSO2_PRODUCT_VERSION/bin/wso2update_linux")) {
                            println "wso2update_linux not found in product directory. Copying from tools directory."
                            sh "cp ${toolsDirectory}/wso2update_linux $WSO2_PRODUCT-$WSO2_PRODUCT_VERSION/bin/"
                        }
                        def statusCode = sh(
                                script: """
                                if [ "${use_staging}" = "true" ] || [ "${use_staging}" = true ]; then
                                    export WSO2_UPDATES_UPDATE_LEVEL_STATE=TESTING
                                else
                                    export WSO2_UPDATES_UPDATE_LEVEL_STATE=VERIFYING
                                fi

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
        stage('Customize product-pack') {
            steps {
                script {
                    // Copy db driver to product pack
                    if (db_driver_url != '') {
                        sh """
                        wget -q ${db_driver_url} -O ./database.jar
                        mv database.jar ${WSO2_PRODUCT}-${WSO2_PRODUCT_VERSION}/repository/components/lib/
                        """
                    }

                    withCredentials([string(credentialsId: 'AWS_ACCESS_KEY_ID', variable: 'accessKey'),
                    string(credentialsId: 'AWS_SECRET_ACCESS_KEY', variable: 'secretAccessKey')]) {
                        // Copy pizzashack war file to acp
                        if (wso2_product == 'wso2am-acp') {
                            sh """
                            export AWS_ACCESS_KEY_ID='$accessKey'
                            export AWS_SECRET_ACCESS_KEY='$secretAccessKey'
                            aws s3 cp --quiet s3://${s3_bucket}/${resourceDirectory}/am#sample#pizzashack#v1.war .
                            mv am#sample#pizzashack#v1.war ${WSO2_PRODUCT}-${WSO2_PRODUCT_VERSION}/repository/deployment/server/webapps/
                            """
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
                        String hostIp = sh(script: 'hostname -I | awk \'{print $1}\'', returnStdout: true).trim()
                        String UPDATED_PRODUCT_PACK_HOST_LOCATION_URL = "http://${hostIp}:8889"
                        dir("${dockerDirectory}") {
                            sh """
                            cd dockerfiles/${os}/${product_name_map[wso2_product]}
                            sudo docker login -u ${docker_registry_username} -p ${docker_registry_password} ${docker_registry}
                            sudo docker build --no-cache -t ${docker_registry}/${wso2_product_full_name}:${tag} . --build-arg WSO2_SERVER_DIST_URL=${UPDATED_PRODUCT_PACK_HOST_LOCATION_URL}/${wso2_product}-${wso2_product_version}.zip
                            sudo docker push ${docker_registry}/${wso2_product_full_name}:${tag}
                            echo "Docker image ${docker_registry}/${wso2_product_full_name}:${tag} pushed successfully"
                            """
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
                    sh """
                        # Remove the Docker image
                        sudo docker rmi -f ${docker_registry}/${wso2_product_full_name}:${tag} || echo "Docker image not found or already removed"
                        sudo docker system prune -f || echo "Docker system prune failed"
                        # Remove Docker credentials
                        sudo docker logout ${docker_registry} || echo "Docker logout failed"
                    """
                } catch (Exception e) {
                    echo "Workspace cleanup failed: ${e.message}"
                } finally {
                    cleanWs()
                    println "Workspace cleanup completed."
                }
            }
        }
    }
}

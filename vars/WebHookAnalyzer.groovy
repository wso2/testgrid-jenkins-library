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


import org.wso2.tg.jenkins.Logger
import org.wso2.tg.jenkins.PipelineContext
import org.wso2.tg.jenkins.Properties

// The pipeline should reside in a call block
def call() {
    // Setting the current pipeline context, this should be done initially
    PipelineContext.instance.setContext(this)
    // Initializing environment properties
    def props = Properties.instance
    props.instance.initProperties()
    def log = new Logger()


    pipeline {
        agent any
        triggers {
            GenericTrigger(
                    genericVariables: [
                            [expressionType: 'JSONPath', key: 'sshUrl', value: '$.ssh_url'],
                            [expressionType: 'JSONPath', key: 'repoName', value: '$.repository.name'],
                            [expressionType: 'JSONPath', key: 'branch', value: '$.ref', regexpFilter: 'refs/heads/']
                    ],
                    regexpFilterText: '',
                    regexpFilterExpression: ''
            )
        }
        tools {
            jdk 'jdk8'
        }

        stages {
            stage('Receive web Hooks') {
                steps {
                    script {
                        script {
                            echo "Recieved the web hook request!"
                            // Cloning the git repository
                            log.info("The git branch is : ${branch}")
                            log.info("The git repo name is : ${repoName}")
                            log.info("Git SSH URL is : ${sshUrl}")
                            cloneRepo(${sshUrl}, ${branch})

                            // We need to get a list of Jobs that are configured
                            printAllJobs()
                        }
                    }
                }
            }
        }
    }
}

void cloneRepo(def gitURL, gitBranch) {
    sshagent (credentials: ['github_bot']) {
        sh """
            echo Cloning repository: ${gitURL} into ${dir}
            git clone -b ${gitBranch} ${gitURL}
        """
    }
}

printAllJobs() {
    Jenkins.instance.getAllItems(AbstractItem.class).each {
        println(it.fullName)
    }
}



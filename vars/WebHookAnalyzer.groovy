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
import org.jenkinsci.plugins.envinject.EnvInjectJobPropertyInfo
import org.jenkinsci.plugins.envinject.EnvInjectJobProperty
import org.jenkinsci.plugins.envinject.EnvInjectInfo

// The pipeline should reside in a call block
def call() {
    // Setting the current pipeline context, this should be done initially
    PipelineContext.instance.setContext(this)
    // Initializing environment properties
    def props = Properties.instance
    props.instance.initProperties()
    def log = new Logger()

    final def GIT_REPOSITORY = "${repoName}"
    final def GIT_SSH_URL = "${sshUrl}"
    final def GIT_BRANCH = "${branch}"
    final def TG_YAML_SEARCH_REGEX = "*.testgrid.yaml"

    pipeline {
        agent {
            node {
                label ""
                customWorkspace "${props.WORKSPACE}"
            }
        }
        // This trigger is removed from the pipeline itself due to few issues in the plugin when using
        // shared libraries this configurations are in the job configurations
//        triggers {
//            GenericTrigger(
//                    genericVariables: [
//                            [expressionType: 'JSONPath', key: 'sshUrl', value: '$.repository.ssh_url'],
//                            [expressionType: 'JSONPath', key: 'repoName', value: '$.repository.name'],
//                            [expressionType: 'JSONPath', key: 'branch', value: '$.ref', regexpFilter: 'refs/heads/']
//                    ],
//                    regexpFilterText: '',
//                    regexpFilterExpression: ''
//            )
//        }
        tools {
            jdk 'jdk8'
        }

        stages {
            stage('Receive web Hooks') {
                steps {
                    script {
                        // TODO:  we need to validate the payloads.
                        echo "Received the web hook request!"
                        log.info("The git repo name : " + GIT_REPOSITORY)
                        log.info("Git SSH URL : " + GIT_BRANCH)
                        log.info("Git branch : " + GIT_SSH_URL)

                        deleteDir()
                        cloneRepo(GIT_SSH_URL, GIT_BRANCH)
                        def tgYamls = findTestGridYamls(props.WORKSPACE + "/" + GIT_REPOSITORY)
                        processTgConfigs(tgYamls)
                        // We need to get a list of Jobs that are configured
                    }
                }
            }
        }
    }
}

void processTgConfigs(def files) {
    def log = new Logger()
    // First lets read the yaml and get the properties
    for (int i = 0; i < files.length; i++) {
        log.info("Processing the TG Yaml at : " + files[i])
        def tgYamlContent
        def jobName
        try {
            tgYamlContent = readYaml file: files[i]
            log.info("YAML Content : ${tgYamlContent}")
            def addToJenkins = tgYamlContent.onboardJob
            log.info("The onboarding flag is " + addToJenkins)
            if (!addToJenkins) {
                log.warn("Skipping on-boarding the testgrid yaml for " + files[i])
                continue
            }
            jobName = tgYamlContent.jobName
            def emailToList = tgYamlContent.emailToList

            //check whether a job exist with the same name
            // We will anyway update the job with the latest configs
            if (isJobExists(jobName)) {
                log.warn("Found a job with the name " + jobName + " will be updating the job.")
            }
        } catch (Exception e) {
          log.error("Error while reading the yaml content " + e.getMessage())
          // TODO: We need to generate a Email here
        }
        createJenkinsJob(jobName, "")
    }
}

/**
 * This method is responsible for creating the Jenkins job.
 */
def createJenkinsJob(def jobName, def timerConfig) {

    jobName = "test-job-ycr-4"
    echo "Creating the job ${jobName}"

    //TODO: depending on the environment we need to select the correct pipeline branch, we can get this as a job
    // property
    def jobDSL="@Library('intg_test_template@dev') _\n" +
                "Pipeline()"
    def flowDefinition = new org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition(jobDSL, true)
    def instance = Jenkins.instance
    def job = new org.jenkinsci.plugins.workflow.job.WorkflowJob(instance, jobName )
    job.definition = flowDefinition
    job.setConcurrentBuild(false)

    def spec = "H 0 1 * *";
    hudson.triggers.TimerTrigger newCron = new hudson.triggers.TimerTrigger(spec);
    newCron.start(job, true);
    job.addTrigger(newCron);
    def prop = new EnvInjectJobPropertyInfo("", "ABCD=\"CDE\"", "", "", "", false)
    def prop2 = new org.jenkinsci.plugins.envinject.EnvInjectJobProperty(prop)
    prop2.setOn(true)
    prop2.setKeepBuildVariables(true)
    prop2.setKeepJenkinsSystemVariables(true)
    job.addProperty(prop2)
    job.save()
    Jenkins.instance.reload()

}

/**
 * Clones a given git repo
 * @param gitURL
 * @param gitBranch
 */
void cloneRepo(def gitURL, gitBranch) {
    def props = Properties.instance
    tryAddKnownHost("github.com")
    sshagent(credentials: ['github_bot']) {
        sh """
            echo Cloning repository: ${gitURL}
            cd ${props.WORKSPACE}
            git clone -b ${gitBranch} ${gitURL}
        """
    }
}

/**
 * Add hostUrl to knownhosts on the system (or container) if necessary so that ssh commands will go
 * through even if the certificate was not previously seen.
 * @param hostUrl
 */
void tryAddKnownHost(String hostUrl) {
    // ssh-keygen -F ${hostUrl} will fail (in bash that means status code != 0) if ${hostUrl} is not yet a known host
    def statusCode = sh script: "ssh-keygen -F ${hostUrl}", returnStatus: true
    if (statusCode != 0) {
        sh "mkdir -p ~/.ssh"
        sh "ssh-keyscan ${hostUrl} >> ~/.ssh/known_hosts"
    }
}

def findTestGridYamls(def searchPath) {
    def files
    dir(searchPath) {
        files = findFiles(glob: '**/testgrid.yaml')
    }
    // Generate the absolute paths of TG yaml files
    def absoluteFileList = new String[files.length]
    for (int i = 0; i < files.length; i++) {
        absoluteFileList[i] = searchPath + "/" + files[i]
    }
    echo "${absoluteFileList}"
    return absoluteFileList
}

boolean isJobExists(def jobName) {
    Jenkins.instance.getAllItems(AbstractItem.class).each {
        if (it.fullName.equals(jobName)) {
            return true
        }
    }
    return false
}

def readConfigProperties(def prop) {
    def props = Properties.instance
    def properties = readProperties file: "${props.CONFIG_PROPERTY_FILE_PATH}"
    return properties[prop]
}

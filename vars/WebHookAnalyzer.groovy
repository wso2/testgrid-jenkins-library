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


import jenkins.model.Jenkins
import org.wso2.tg.jenkins.Logger
import org.wso2.tg.jenkins.PipelineContext
import org.wso2.tg.jenkins.Properties
import org.jenkinsci.plugins.envinject.EnvInjectJobPropertyInfo
import org.wso2.tg.jenkins.alert.Email

// The pipeline should reside in a call block
def call() {
    // Setting the current pipeline context, this should be done initially
    PipelineContext.instance.setContext(this)
    // Initializing environment properties
    def props = Properties.instance
    LocalProperties.instance.initProps()
    props.instance.initProperties()
    def log = new Logger()

    pipeline {
        agent {
            node {
                label ""
                customWorkspace "${props.WORKSPACE}"
            }
        }
        tools {
            jdk 'jdk8'
        }

        stages {
            stage('Receive web Hooks') {
                steps {
                    script {
                        echo "Received the web hook request!"
                        log.info("The git repo name : " + LocalProperties.GIT_REPOSITORY)
                        log.info("Git SSH URL : " + LocalProperties.GIT_BRANCH)
                        log.info("Git branch : " + LocalProperties.GIT_SSH_URL)

                        deleteDir()
                        //TODO: We can optimize the process by analyzing the changed files before cloning the repo
                        // Following information is available in the web-hook event regarding the file changes
                        // We nede to take all commits nd check for all the files changed
                        /**
                         "added": [ ],
                         "removed": [ ],
                         "modified": [
                         "test/testgrid.yaml"
                         ]
                         **/

                        cloneRepo(LocalProperties.GIT_SSH_URL, LocalProperties.GIT_BRANCH)
                        def tgYamls = findTestGridYamls(props.WORKSPACE + "/" + LocalProperties.GIT_REPOSITORY)
                        processTgConfigs(tgYamls)
                        // We need to get a list of Jobs that are configured
                    }
                }
            }
        }
    }
}

/**
 * Processes the found TG yaml files and adds the jobs
 * @param files list of tg files
 */
void processTgConfigs(def files) {
    def log = new Logger()
    // First lets read the yaml and get the properties
    for (int i = 0; i < files.length; i++) {
        log.info("Processing the TG Yaml at : " + files[i])
        def tgYamlContent
        def jobName
        try {
            // Reading the yaml file
            tgYamlContent = readYaml file: files[i]
            log.info("YAML Content : ${tgYamlContent}")

            def addToJenkins = tgYamlContent.jobConfigs.onboardJob
            log.info("The onboarding flag is " + addToJenkins)
            if (addToJenkins == false) {
                log.warn("Skipping on-boarding the testgrid yaml for " + files[i])
                continue
            }
            jobName = tgYamlContent.jobConfigs.jobName
            if (jobName == null || jobName == "") {
                jobName = gennerateJobName()
            }
            def emailToList = tgYamlContent.jobConfigs.emailToList
            //check whether a job exist with the same name
            // We will anyway update the job with the latest configs
            if (isJobExists(jobName)) {
                log.warn("Found a job with the name " + jobName + " will be updating the job.")
            }
            createJenkinsJob(jobName, "", files[i])
            //TODO: We need to send an Email to the committer after creating the job
            //Email email = new Email()
            //email.send("Auto build creation Notification")
        } catch (Exception e) {
          log.error("Error while creating the job " + e.getMessage())
          // TODO: We need to generate an Email here
        }
    }

}

/**
 * This method is responsible for creating the Jenkins job.
 * @param jobName jobName
 * @param timerConfig cron expression to schedule the job
 * @return
 */
def createJenkinsJob(def jobName, def timerConfig, def file) {

    echo "Creating the job ${jobName}"
    def jobDSL="@Library('intg_test_template@${LocalProperties.TG_ENV}') _\n" +
                "Pipeline()"
    def flowDefinition = new org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition(jobDSL, true)
    def instance = Jenkins.instance
    def job = new org.jenkinsci.plugins.workflow.job.WorkflowJob(instance, jobName )
    job.definition = flowDefinition
    job.setConcurrentBuild(false)

    if (timerConfig != null && timerConfig != "") {
        hudson.triggers.TimerTrigger newCron = new hudson.triggers.TimerTrigger(timerConfig);
        newCron.start(job, true)
        job.addTrigger(newCron)
    }
    def rawYamlLocation = generateRawYamlLocation(file)
    def prop = new EnvInjectJobPropertyInfo("", "${LocalProperties.TESTGRID_YAML_URL_KEY}=${rawYamlLocation}", "",
            "", "", false)
    def prop2 = new org.jenkinsci.plugins.envinject.EnvInjectJobProperty(prop)
    prop2.setOn(true)
    prop2.setKeepBuildVariables(true)
    prop2.setKeepJenkinsSystemVariables(true)
    job.addProperty(prop2)
    job.save()
    Jenkins.instance.reload()

}

String gennerateJobName() {
    def props = LocalProperties.instance
    def jobName = props.GIT_REPOSITORY + "-" + props.GIT_BRANCH
    return jobName
}

String generateRawYamlLocation(def fileLocation) {
    // We will split from the repo name and get the rest to create the raw URL
    def relativePath = fileLocation.split(LocalProperties.GIT_REPOSITORY)[1]
    return LocalProperties.GH_RAW_URL + "/" + LocalProperties.GIT_ORG_NAME + "/" + LocalProperties.GIT_REPOSITORY +
            "/" + LocalProperties.GIT_BRANCH + relativePath
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

@Singleton
class LocalProperties {

    final static def TG_YAML_SEARCH_REGEX = "*.testgrid.yaml"
    final static def GH_RAW_URL = "https://raw.githubusercontent.com"
    final static def TESTGRID_YAML_URL_KEY = "TESTGRID_YAML_URL"
    // The full name will be something like <ORG_NAME>/<REPO>
    static def GIT_REPOSITORY
    static def GIT_ORG_NAME
    static def GIT_SSH_URL
    static def GIT_BRANCH
    static def TG_ENV

    def initProps() {
        GIT_REPOSITORY = getJobProperty("repoName").split("/")[1]
        GIT_ORG_NAME = getJobProperty("repoName").split("/")[0]
        GIT_SSH_URL = getJobProperty("sshUrl")
        GIT_BRANCH = getJobProperty("branch")
        TG_ENV = getJobProperty("TG_ENV")
    }

    private def getJobProperty(def property, boolean isMandatory = true) {
        def ctx = PipelineContext.getContext()
        def propertyMap = ctx.currentBuild.getRawBuild().getEnvironment()
        def prop = propertyMap.get(property)
        if ((prop == null || prop.trim() == "") && isMandatory) {
            ctx.echo "A mandatory prop " + property + " is empty or null"
            throw new Exception("A mandatory property " + property + " is empty or null")
        }
        ctx.echo "Property : " + property + " value is set as " + prop
        return prop
    }
}

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
import org.jenkinsci.plugins.envinject.EnvInjectJobPropertyInfo
import org.jenkinsci.plugins.envinject.EnvInjectJobProperty
import org.wso2.tg.jenkins.Logger
import org.wso2.tg.jenkins.PipelineContext
import org.wso2.tg.jenkins.Properties
import org.wso2.tg.jenkins.alert.Email
import org.wso2.tg.jenkins.alert.Slack
import org.wso2.tg.jenkins.executors.TestExecutor
import org.wso2.tg.jenkins.executors.TestGridExecutor
import org.wso2.tg.jenkins.util.*

def JENKINS_SHARED_LIB_NAME = 'intg_test_template@dev'
def JOB_CONFIG_REPO_RAW_URL = 'https://raw.githubusercontent.com/wso2-incubator/testgrid-job-configs/master/'

call() //TODO: remove

def call() {
  // Setting the current pipeline context, this should be done initially
  PipelineContext.instance.setContext(this)
  // Initializing environment properties
  def props = Properties.instance
  props.instance.initProperties()

  // For scaling we need to create slave nodes before starting the pipeline and schedule it appropriately
  def alert = new Slack()
  def email = new Email()
  def awsHelper = new AWSUtils()
  def testExecutor = new TestExecutor()
  def tgExecutor = new TestGridExecutor()
  def runtime = new RuntimeUtils()
  def ws = new WorkSpaceUtils()
  def common = new Common()
  def log = new Logger()
  def config = new ConfigUtils()

  pipeline {
    agent {
      node {
        label ""
        customWorkspace "${props.WORKSPACE}"
      }
    }

    stages {
      stage('Create Testgrid Job') {
        steps {
          deleteDir()
          git url: 'https://github.com/kasunbg/testgrid-job-configs', branch: 'job-creator-test'

          script {
            def changeLogSets = currentBuild.changeSet
            for (int i = 0; i < changeLogSets.size(); i++) {
              def entries = changeLogSets[i].items
              for (int j = 0; j < entries.length; j++) {
                def entry = entries[j]
                echo "${entry.commitId} by ${entry.author} on ${new Date(entry.timestamp)}: ${entry.msg}"
                def files = new ArrayList(entry.affectedFiles)
                for (int k = 0; k < files.size(); k++) {
                  def file = files[k]
                  echo "  ${file.editType.name} ${file.path}"
                  handleChange(${file.editType.name}, ${file.path})
                }
              }
            }

          }

        }
      }
    }
  }
}

def handleChange(String action, String filePath) {
  switch (action) {
    case "add":
      addOrModifyJenkinsJob(editType, filePath)
      break
    case "modify":
      addOrModifyJenkinsJob(editType, filePath)
      break
    case "delete":
    default:

      break
  }
}

def addOrModifyJenkinsJob(def action, filePath) {
  echo "Processing file $action: $filePath"
  def tgYamlContent
  def jobName
  try {
    // Reading the yaml file
    jobConfigYaml = readYaml file: filePath
    log.info("Job config yaml content : ${jobConfigYaml}")

//    def addToJenkins = tgYamlContent.jobConfigs.onboardJob
//    log.info("The onboarding flag is " + addToJenkins)
//    if (addToJenkins == false) {
//      log.warn("Skipping on-boarding the testgrid yaml for " + files[i])
//      continue
//    }

    jobName = filePath
//    if (jobName == null || jobName == "") {
//      jobName = gennerateJobName()
//    }
    def emailToList = jobConfigYaml.jobConfig.emailToList
    if ("add" == action && isJobExists(jobName)) {
      log.warn("Found an existing job with the name: " + jobName + ". Will update that instead.")
    }
    createJenkinsJob(jobName, "", filePath, jobConfigYaml)
    //TODO: Email to the committer after creating the job
    //Email email = new Email()
    //email.send("Auto build creation Notification")
  } catch (e) {
    echo "Error while creating the job ${e.getMessage()}"
    // TODO: notify about the error
  }
}

/**
 * TODO: handle job names with folders.
 *
 * @param jobName
 * @return
 */
boolean isJobExists(def jobName) {
  Jenkins.instance.getAllItems(AbstractItem.class).each {
    echo "iterating $it.fullName";
    if (it.fullName == jobName) {
      return true
    }
  }
  return false
}

/**
 * This method is responsible for creating the Jenkins job.
 *
 * @param jobName jobName
 * @param timerConfig cron expression to schedule the job
 * @return
 */
def createJenkinsJob(def jobName, def timerConfig, def file, def jobConfigYaml) {

  echo "Creating the job ${jobName}"
  echo "shared lib name: ${JENKINS_SHARED_LIB_NAME}"  //todo remove
  def jobDSL="@Library('$JENKINS_SHARED_LIB_NAME') _\n" +
          "Pipeline()"
  def instance = Jenkins.instance
  def job = new org.jenkinsci.plugins.workflow.job.WorkflowJob(instance, jobName)
  def flowDefinition = new org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition(jobDSL, true)
  job.definition = flowDefinition
  job.setConcurrentBuild(false)

  if (timerConfig != null && timerConfig != "") {
    hudson.triggers.TimerTrigger newCron = new hudson.triggers.TimerTrigger(timerConfig)
    newCron.start(job, true)
    job.addTrigger(newCron)
  }
  def testgridYamlURL = jobConfigYaml.testgridYamlURL;
  def rawGitHubFileLocation = getRawGitHubFileLocation(file)
  addJobProperty(LocalProperties.TESTGRID_YAML_URL_KEY, testgridYamlURL, job)
  addJobProperty(LocalProperties.JOB_CONFIG_YAML_URL_KEY, rawGitHubFileLocation, job)
  job.save()
  Jenkins.instance.reload()

}

private void addJobProperty(String key, value, org.jenkinsci.plugins.workflow.job.WorkflowJob job) {
  def prop = new EnvInjectJobPropertyInfo("", key + "=${value}", "",
          "", "", false)
  prop = new EnvInjectJobProperty(prop)
  prop.setOn(true)
  prop.setKeepBuildVariables(true)
  prop.setKeepJenkinsSystemVariables(true)
  job.addProperty(prop2)
}

String getRawGitHubFileLocation(def fileLocation) {
  return JOB_CONFIG_REPO_RAW_URL + fileLocation
}

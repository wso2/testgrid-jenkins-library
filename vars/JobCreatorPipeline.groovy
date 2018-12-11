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

import hudson.triggers.TimerTrigger
import jenkins.model.Jenkins
import org.jenkinsci.plugins.envinject.EnvInjectJobProperty
import org.jenkinsci.plugins.envinject.EnvInjectJobPropertyInfo
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.wso2.tg.jenkins.PipelineContext
import org.wso2.tg.jenkins.Properties
import org.wso2.tg.jenkins.alert.Email
import org.wso2.tg.jenkins.alert.Slack

@Singleton
class JobCreatorProperties {
  final static JENKINS_SHARED_LIB_NAME = 'intg_test_template@dev'
  final static GIT_REPO = 'wso2-incubator/testgrid-job-configs'
  final static GIT_BRANCH = 'master'
  final static JOB_CONFIG_REPO_RAW_URL = "https://raw.githubusercontent.com/${GIT_REPO}/${GIT_BRANCH}/"

  final static String TESTGRID_YAML_URL_KEY = "TESTGRID_YAML_URL"
  final static String JOB_CONFIG_YAML_URL_KEY = "JOB_CONFIG_YAML_URL"
}

/**
 * This pipeline listens on the testgrid-job-configs git repo for any file changes.
 * If a file change is detected, then this pipeline will fetch that,
 * and create/update/delete the testgrid jenkins jobs as necessary.
 *
 * Caveats:
 * On deletion, this does not delete the testgrid job from the database. It only
 * deletes the jenkins job.
 *
 */
def call() {
  PipelineContext.instance.setContext(this)
  def props = Properties.instance
  props.instance.initProperties()

  def alert = new Slack()
  def email = new Email()

  pipeline {
    agent {
      node {
        label ""
      }
    }
    stages {
      stage('Create Testgrid Jobs') {
        steps {
          deleteDir()
          git url: "https://github.com/${JobCreatorProperties.GIT_REPO}", branch: "${JobCreatorProperties.GIT_BRANCH}"
          script {
            try {
              def changedFiles = getChangedFiles()
              process(changedFiles)
            } catch (e) {
              handleException(e.getMessage(), e)
            }
          }
        }
      }
    }
  }
}
def process(def changedFiles) {
  for (change in changedFiles) {
    try {
      handleChange(change.type, change.file)
    } catch(e) {
      handleException("Error while processing ${change.file}. ${e.getMessage()}", e)
    }
  }

}

@NonCPS
def getChangedFiles() {
  MAX_MSG_LEN = 150
  def changeString = ""

  List changedFiles = new ArrayList()
  echo "Gathering SCM changes"
  def changeLogSets = currentBuild.changeSets
    for (logset in changeLogSets) {
    for (entry in logset.items) {
      truncated_msg = entry.msg.take(MAX_MSG_LEN)
      commitId = entry.commitId.take(7)
      changeString += "|- ${commitId}: ${truncated_msg} [${entry.author}]\n"
      for (file in entry.affectedFiles) {
        def change = Change.newInstance()
        change.type = file.editType.name
        change.file = file.path
        changedFiles.add(change)
        changeString += "|  |- [${file.editType.name}] ${file.path}\n"
      }
    }
  }

  if (!changeString) {
    changeString = " - No new changes"
  } else {
    changeString = "Changes found: \n${changeString}"
  }

  echo changeString
  return changedFiles
}

/**
 * Handle the change to the file based on what the change is.
 *
 * @param instruction add/edit/delete are supported
 * @param filePath the relative path to changed file.
 */
def handleChange(String instruction, String filePath) {
  echo "Processing '${instruction}' instruction on file ${filePath}"
  switch (instruction) {
    case "add":
    case "edit":
      def exists = fileExists "${filePath}"
      if (!exists) {
        echo "[ERROR] File not found: " + filePath
        // TODO handle
        return
      }

      def jobConfigYaml = readYaml file: filePath
      String jobName = filePath
      if ("add" == instruction && isJobExists(jobName)) {
        echo "Found an existing job with the name: " + jobName + ". Will update that instead."
      }

      createJenkinsJob(jobName, "", filePath, jobConfigYaml)
      break
    case "delete":
      def jobName = filePath;
      shelveJenkinsJob(jobName)
      break
    default:
      echo "Instruction not supported: $instruction. file: $filePath"
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
    if (it.fullName == jobName) {
      return true
    }
  }
  return false
}

/**
 * This method is responsible for creating the Jenkins job.
 * TODO: create folders as needed.
 *
 * @param jobName jobName
 * @param timerConfig cron expression to schedule the job
 * @return
 */
def createJenkinsJob(String jobName, String timerConfig, String file, def jobConfigYaml) {
  echo "Creating the job ${jobName}.."
  def jobDSL = "@Library('$JobCreatorProperties.JENKINS_SHARED_LIB_NAME') _\n" +
          "Pipeline()"
  def instance = Jenkins.instance
  def job = new WorkflowJob(instance, jobName)
  def flowDefinition = new CpsFlowDefinition(jobDSL, true)
  job.definition = flowDefinition
  job.concurrentBuild = false

  if (timerConfig != null && timerConfig != "") {
    TimerTrigger newCron = new TimerTrigger(timerConfig)
    newCron.start(job, true)
    job.addTrigger(newCron)
  }
  def testgridYamlURL = jobConfigYaml.testgridYamlURL
  if (!testgridYamlURL) {
    echo "testgridYamlURL element is not found in the job configuration file: $file. Not adding a job."
    //TODO: notify relevant people
    return
  }

  def rawGitHubFileLocation = getRawGitHubFileLocation(file)
  String properties = """${JobCreatorProperties.JOB_CONFIG_YAML_URL_KEY}="${rawGitHubFileLocation}"
${JobCreatorProperties.TESTGRID_YAML_URL_KEY}="${testgridYamlURL}"
"""
  addJobProperty(properties, job)
  job.save()
  echo "Created the job ${jobName} successfully."
  Jenkins.instance.reload()

  //trigger the initial job
  build job: "${jobName}", quietPeriod: 10, wait: false
}

private static void addJobProperty(String properties, WorkflowJob job) {
  def prop = new EnvInjectJobPropertyInfo("", "${properties}", "", "", "", false)
  prop = new EnvInjectJobProperty(prop)
  prop.setOn(true)
  prop.setKeepBuildVariables(true)
  prop.setKeepJenkinsSystemVariables(true)
  job.addProperty(prop)
}

static String getRawGitHubFileLocation(def fileLocation) {
  return JobCreatorProperties.JOB_CONFIG_REPO_RAW_URL + fileLocation
}

/**
 * TODO: Implement shelve logic. Currently this deletes the job.
 *
 * @param jobName
 * @return
 */
def shelveJenkinsJob(String jobName) {
  boolean deleted = false;
  Jenkins.instance.items.each { item ->
    if (item.class.canonicalName != 'com.cloudbees.hudson.plugins.folder.Folder') {
      if (jobName.contains(item.fullName)) {
        echo "Deleting job: $item.fullName"
        item.delete()
        deleted = true
      }
    }
  }

  if (!deleted) {
    echo "[ERROR] Could not delete job. A job does not exist with the given name: ${jobName}."
  }
}

private void handleException(String msg, Exception e) {
  echo "${msg}"
  def sw = new StringWriter()
  def pw = new PrintWriter(sw)
  e.printStackTrace(pw)
  sw = sw.toString()
  echo sw
}

class Change implements Serializable {
  def type
  def file
}

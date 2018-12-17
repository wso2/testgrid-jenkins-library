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

import hudson.model.AbstractItem
import hudson.model.Item
import hudson.model.TopLevelItem
import hudson.triggers.TimerTrigger
import jenkins.model.Jenkins
import org.jenkinsci.plugins.envinject.EnvInjectJobProperty
import org.jenkinsci.plugins.envinject.EnvInjectJobPropertyInfo
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import com.cloudbees.hudson.plugins.folder.Folder
import org.wso2.tg.jenkins.PipelineContext
import org.wso2.tg.jenkins.Properties
import org.wso2.tg.jenkins.alert.Email
import org.wso2.tg.jenkins.alert.Slack

@Singleton
class JobCreatorProperties {
  final static JENKINS_SHARED_LIB_NAME = 'intg_test_template@dev'
  final static JOB_CONFIG_REPO_RAW_URL_PREFIX = "https://raw.githubusercontent.com/"

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
    environment {
      GIT_REPO = "${env.GIT_REPO}"
      GIT_BRANCH = "${env.GIT_BRANCH}"
    }

    stages {
      stage('Create Testgrid Jobs') {
        steps {
          deleteDir()
          git url: "https://github.com/${env.GIT_REPO}", branch: "${env.GIT_BRANCH}"

          script {
            try {
              def changedFiles = getChangedFiles()
              process(changedFiles)

              synchronizeJenkinsWithGitRepo()
              Jenkins.instance.reload()
            } catch (e) {
              handleException(e.getMessage(), e)
            }
          }
        }
      }
    }
  }
}

/**
 * Processes the changed files iteratively.
 *
 * @param changedFiles
 */
def process(def changedFiles) {
  for (change in changedFiles) {
    try {
      handleChange(change.type, change.file)
    } catch(e) {
      handleException("Error while processing ${change.file}. ${e.getMessage()}", e)
    }
  }

}

/**
 * Read the jenkins changeSets and find the list of changed files.
 *
 * @return a list of @Change instances representing the changed files.
 */
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
 * Handle the change to a given file based on what the change is.
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
        echo "File not found: " + filePath
        return
      }

      def jobConfigYaml = readYaml file: filePath
      String jobName = filePath.replaceAll("-testgrid.yaml", "").replaceAll(".yaml", "")
      if ("add" == instruction && isJobExists(jobName)) {
        echo "Found an existing job with the name: " + jobName + ". Will update that instead."
      }

      createJenkinsJob(jobName, "", filePath, jobConfigYaml)
      break
    case "delete":
      def jobName = filePath.replaceAll("-testgrid.yaml", "").replaceAll(".yaml", "")
      shelveJenkinsJob(jobName)
      break
    default:
      echo "Instruction not supported: $instruction. file: $filePath"
  }
}

/**
 * Find whether a job exists with the given name.
 * Supports recursively searching the job folders.
 *
 * @param jobName
 * @return true if and only if a job exists.
 */
boolean isJobExists(def jobName) {
  boolean isFound = false
  Jenkins.instance.getAllItems(AbstractItem.class).find {
    if (it.fullName == jobName) {
      isFound = true
      return true
    }
  }
  return isFound
}

/**
 * This method is responsible for creating the Jenkins job.
 *
 * @param jobName jobName
 * @param timerConfig cron expression to schedule the job
 * @return
 */
def createJenkinsJob(String jobName, String timerConfig, String file, def jobConfigYaml) {
  echo "Creating the job ${jobName}.."
  def jobDSL = "@Library('$JobCreatorProperties.JENKINS_SHARED_LIB_NAME') _\n" +
          "Pipeline()"
  def testgridYamlURL = jobConfigYaml.testgridYamlURL
  if (!testgridYamlURL) {
    echo "testgridYamlURL element is not found in the job configuration file: $file. Checking whether this is a " +
            "testgrid.yaml instead."
    if (jobConfigYaml.infrastructureConfig && jobConfigYaml.scenarioConfigs) {
      echo "testgrid.yaml content found in the job configuration file. Treating the file as a testgrid.yaml and " +
              "adding a job."
    } else {
      //TODO: notify relevant people
      echo "[WARN] Invalid job configuration file. Not adding a job."
      return
    }
  }

  def parent = createIntermediateJobFolders(file)
  def folderAwareJobName = jobName.split("/")
  folderAwareJobName = folderAwareJobName[folderAwareJobName.length - 1]
  def job = new WorkflowJob(parent, folderAwareJobName)
  def flowDefinition = new CpsFlowDefinition(jobDSL, true)
  job.definition = flowDefinition
  job.concurrentBuild = false

  if (timerConfig != null && timerConfig != "") {
    TimerTrigger newCron = new TimerTrigger(timerConfig)
    newCron.start(job, true)
    job.addTrigger(newCron)
  }

  def rawGitHubFileLocation = getRawGitHubFileLocation(file)
  String properties;
  if (testgridYamlURL) {
    properties = """${JobCreatorProperties.JOB_CONFIG_YAML_URL_KEY}="${rawGitHubFileLocation}"
${JobCreatorProperties.TESTGRID_YAML_URL_KEY}="${testgridYamlURL}"
"""
  } else {
    properties = """${JobCreatorProperties.TESTGRID_YAML_URL_KEY}="${rawGitHubFileLocation}"
"""
  }
  addJobProperty(properties, job)
  job.save()
  if (!isJobExists(jobName)) {
    parent.add(job, folderAwareJobName)
  }
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

String getRawGitHubFileLocation(def fileLocation) {
  return "${JobCreatorProperties.JOB_CONFIG_REPO_RAW_URL_PREFIX}${env.GIT_REPO}/${env.GIT_BRANCH}/${fileLocation}"
}

/**
 * Create intermediate job folders.
 *
 * @param filePath the relative path to the modified file
 */
def createIntermediateJobFolders(String filePath) {
  def folders = filePath.split("/")
  def parent = Jenkins.instance
  folders.eachWithIndex { item, index ->
    if (index != folders.length - 1) {
      echo "Create job folder ${item}"
      Folder folder = new Folder(parent, item)
      folder.save()
      parent = folder
    }
  }

  return parent
}

/**
 * TODO: Implement shelve logic. Currently this deletes the job.
 *
 * @param jobName
 * @return
 */
def shelveJenkinsJob(String jobName) {
  boolean deleted = false
  Jenkins.instance.getAllItems(TopLevelItem.class).find {
    if (it.fullName == jobName) {
      echo "Deleting job: $it.fullName"
      it.getParent().remove(it)
      it.delete()
      deleted = true
      return true
    }
  }

  if (!deleted) {
    echo "[ERROR] Could not delete job. A job does not exist with the given name: ${jobName}."
  }
}

/**
 * Synchronize Jenkins with Git repo to gracefully recover from intermittent issues.
 *
 * If a file is there in git repo, but the relevant testgrid job is not found, then
 * this will add it.
 *
 */
void synchronizeJenkinsWithGitRepo() {
  echo "Synchronizing Jenkins with git repository ${env.GIT_REPO} - ${env.GIT_BRANCH}"
  final yamls = findFiles(glob: '**/*yaml')
  yamls.each { yaml ->
    def job = Jenkins.instance.getItemByFullName(yaml.path)
    if (!job) {
      echo "Testgrid job missing for the file: ${yaml.path}. Creating one."
      handleChange("add", yaml.path)
    }
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

/**
 * Represents a given file change in a repo.
 * type is what's the change type: add/edit/delete
 * file is the relative path to the changed file.
 *
 */
class Change implements Serializable {
  def type
  def file
}

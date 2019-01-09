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

package org.wso2.tg.jenkins.executors

import com.cloudbees.groovy.cps.NonCPS
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode
import org.jenkinsci.plugins.workflow.job.views.FlowGraphAction
import org.wso2.tg.jenkins.Logger
import org.wso2.tg.jenkins.Properties
import org.wso2.tg.jenkins.alert.Slack
import org.wso2.tg.jenkins.util.AWSUtils
import org.wso2.tg.jenkins.util.Common
import org.wso2.tg.jenkins.util.FileUtils
import org.wso2.tg.jenkins.util.RuntimeUtils

import java.nio.charset.StandardCharsets

def runPlan(tPlan, testPlanId) {
    def commonUtil = new Common()
    def notifier = new Slack()
    def awsHelper = new AWSUtils()
    def fileUtil = new FileUtils()
    def props = Properties.instance
    props.instance.initProperties()
    def tgExecutor = new TestGridExecutor()
    def runtime = new RuntimeUtils()
    def log = new Logger()
    def scenarioConfigs = []

    def execName = commonUtil.extractInfraCombination("${testPlanId}")
    def paralleId = getParalleId(execName)
    def url = generateBluoceanLink("${paralleId}")
    echo "Blueocean link URL : " + "${url}"

    scenarioConfigs = readRepositoryUrlsfromYaml("${props.WORKSPACE}/${tPlan}")
    log.info("Preparing workspace for testplan : " + testPlanId)
    prepareWorkspace(testPlanId, scenarioConfigs)
    //sleep(time:commonUtil.getRandomNumber(10),unit:"SECONDS")
    log.info("Unstashing test-plans and testgrid.yaml to ${props.WORKSPACE}/${testPlanId}")
    runtime.unstashTestPlansIfNotAvailable("${props.WORKSPACE}/testplans")

    log.info("Downloading default deploy.sh...")
    sh """
    mkdir -p ${props.WORKSPACE}/${testPlanId}/workspace/${props.DEPLOYMENT_LOCATION}
    curl --max-time 6 --retry 6 -o ${props.WORKSPACE}/${testPlanId}/workspace/${props.DEPLOYMENT_LOCATION}/deploy.sh https://raw.githubusercontent.com/wso2/testgrid/master/jobs/test-resources/deploy.sh
    """
    def name = commonUtil.extractInfraCombination(testplanId)
    notifier.sendNotification("STARTED", "parallel \n Infra : " + name, "#build_status_verbose")
    try {
        tgExecutor.runTesPlans(props.PRODUCT,
                "${props.WORKSPACE}/${tPlan}", "${props.WORKSPACE}/${testPlanId}","${url}")
        //commonUtil.truncateTestRunLog(testPlanId)
        echo "run test plan"
    } catch (Exception err) {
        log.error("Error : ${err}")
        currentBuild.result = 'UNSTABLE'
    } finally {
        notifier.sendNotification(currentBuild.result, "Parallel \n Infra : " + name, "#build_status_verbose")
    }
    log.info("RESULT: ${currentBuild.result}")
    awsHelper.uploadToS3(testPlanId)
}

def getTestExecutionMap(parallel_executor_count) {
    def runtime = new RuntimeUtils()
    def commonUtils = new Common()
    def log = new Logger()
    def props = Properties.instance
    def parallelExecCount = parallel_executor_count as int
    def name = "unknown"
    def tests = [:]
    def files = findFiles(glob: '**/test-plans/*.yaml')
    log.info("Found ${files.length} testplans")
    log.info("Parallel exec count " + parallelExecCount)
    for (int f = 1; f < parallelExecCount + 1 && f <= files.length; f++) {
        def executor = f
        testplanId = commonUtils.getTestPlanId("${props.WORKSPACE}/test-plans/" + files[f - 1].name)
        name = commonUtils.extractInfraCombination(testplanId)

        tests["${name}"] = {
            node {
                stage("Parallel Executor : ${executor}") {
                    script {
                        int processFileCount = 0
                        if (files.length < parallelExecCount) {
                            processFileCount = 1
                        } else {
                            processFileCount = files.length / parallelExecCount
                        }
                        runtime.unstashTestPlansIfNotAvailable("${props.WORKSPACE}/testplans")
                        if (executor == parallelExecCount) {
                            for (int i = processFileCount * (executor - 1); i < files.length; i++) {
                                /*IMPORTANT: Instead of using 'i' directly in your logic below,
                                you should assign it to a new variable and use it.
                                (To avoid same 'i-object' being refered)*/
                                // Execution logic
                                int fileNo = i
                                testplanId = commonUtils.getTestPlanId("${props.WORKSPACE}/test-plans/"
                                        + files[fileNo].name)
                                runPlan(files[i], testplanId)
                            }
                        } else {
                            for (int i = 0; i < processFileCount; i++) {
                                int fileNo = processFileCount * (executor - 1) + i
                                testplanId = commonUtils.getTestPlanId("${props.WORKSPACE}/test-plans/"
                                        + files[fileNo].name)
                                runPlan(files[fileNo], testplanId)
                            }
                        }
                    }
                }
            }
        }
    }
    return tests
}

def prepareWorkspace(testPlanId, scenarioConfigs) {
    def props = Properties.instance
    def log = new Logger()
    log.info(" Creating workspace and builds sub-directories")

    sh """
        rm -r -f ${props.WORKSPACE}/${testPlanId}/
        mkdir -p ${props.WORKSPACE}/${testPlanId}
        mkdir -p ${props.WORKSPACE}/${testPlanId}/builds
        mkdir -p ${props.WORKSPACE}/${testPlanId}/workspace
        #Cloning should be done before unstashing TestGridYaml since its going to be injected
        #inside the cloned repository
        cd ${props.WORKSPACE}/${testPlanId}/workspace
        echo Workspace directory content:
        ls ${props.WORKSPACE}/${testPlanId}/
    """

    tryAddKnownHost("github.com")
    cloneRepo(props.INFRASTRUCTURE_REPOSITORY_URL, props.INFRASTRUCTURE_REPOSITORY_BRANCH, props.WORKSPACE + '/' +
            testPlanId + '/workspace/' + props.INFRA_LOCATION)

    if (props.DEPLOYMENT_REPOSITORY_URL != null) {
        cloneRepo(props.DEPLOYMENT_REPOSITORY_URL, props.DEPLOYMENT_REPOSITORY_BRANCH, props.WORKSPACE + '/' + testPlanId +
                '/workspace/' + props.DEPLOYMENT_LOCATION );
    } else {
        log.info("Deployment repository not specified")
    }

    for (repo in scenarioConfigs) {
        cloneRepo(repo.get("url"), repo.get("branch"), props.WORKSPACE + '/' +
                testPlanId + '/workspace/' + props.SCENARIOS_LOCATION + '/' + repo.get("dir"))
    }
    log.info("Copying the ssh key file to workspace : ${props.WORKSPACE}/${testPlanId}/${props.SSH_KEY_FILE_PATH}")
    withCredentials([file(credentialsId: 'DEPLOYMENT_KEY', variable: 'keyLocation')]) {
        sh """
            cp ${keyLocation} ${props.WORKSPACE}/${testPlanId}/${props.SSH_KEY_FILE_PATH}
            cp -n ${keyLocation} ${props.TESTGRID_HOME}/${props.SSH_KEY_FILE_PATH_INTG}
            chmod 400 ${props.WORKSPACE}/${testPlanId}/${props.SSH_KEY_FILE_PATH}
            chmod 400 ${props.TESTGRID_HOME}/${props.SSH_KEY_FILE_PATH_INTG}
        """
    }

    for (repo in scenarioConfigs) {

        log.info("TEST_MODE ----->>>>>>>>>: ${props.TEST_MODE}")
        log.info("PRODUCT ----->>>>>>>>>: ${props.PRODUCT}")
        log.info("PRODUCT_GIT_BRANCH ----->>>>>>>>>: ${props.PRODUCT_GIT_BRANCH}")

        sh """
            echo "Copying uat-nexus setting file into  : ${props.WORKSPACE}/${testPlanId}/workspace/${props.SCENARIOS_LOCATION}/${repo.get("dir")}/${repo.get("dir")}"
        """

        configFileProvider(
                [configFile(fileId: "uat-nexus-settings", targetLocation:
                        "${props.WORKSPACE}/${testPlanId}/workspace/${props.SCENARIOS_LOCATION}/${repo.get("dir")}/${repo.get("dir")}/uat-nexus-settings.xml")]) {
        }

        sh """
            cd ${props.WORKSPACE}/${testPlanId}/workspace/${props.SCENARIOS_LOCATION}/${repo.get("dir")}/${repo.get("dir")}
            ls 
        """

    }
}

/**
 * Returns the parallel id of the test test plan
 *
 * @param name of the executor where test plan is running
 * @return parallel id of test plan
 */
@NonCPS
def getParalleId(name){

    def parallelname = name
    def build = currentBuild.getRawBuild()
    def actions = build.getAllActions();
    def parallelId = ""
    actions.each{act ->
        if(act instanceof FlowGraphAction){
            def nodes = act.getNodes();
            nodes.each{node ->
                if(node instanceof StepStartNode){
                    if(node.displayName.endsWith(parallelname)){
                        println("Parallel node  : " + parallelname + " : " + node.id.toString())
                        parallelId = node.id.toString()
                    }
                }

            }
        }
    }
    /*
    Its required to make these values null to inform Jenkins that these objects should not be serialized
    during execution. @NonCPS annotation does not seem to work in this instance.
     */
    build = null
    actions = null
    parallelname = null
    return parallelId
}

def readRepositoryUrlsfromYaml(def testplan) {

    def scenarioConfigs = []
    def props = Properties.instance
    def tgYaml = readYaml file: testplan
    if (tgYaml.isEmpty()) {
        throw new Exception("Testgrid Yaml content is Empty")
    }
    // We need to set the repository properties
    props.INFRASTRUCTURE_REPOSITORY_URL = tgYaml.infrastructureConfig.provisioners[0].remoteRepository
    props.INFRASTRUCTURE_REPOSITORY_BRANCH = getRepositoryBranch(tgYaml.infrastructureConfig.provisioners[0].remoteBranch)

    props.DEPLOYMENT_REPOSITORY_URL = tgYaml.deploymentConfig.deploymentPatterns[0].remoteRepository
    props.DEPLOYMENT_REPOSITORY_BRANCH = getRepositoryBranch(tgYaml.deploymentConfig.deploymentPatterns[0].remoteBranch)

    for (repo in tgYaml.scenarioConfigs) {
        scenarioConfigs.add([url : repo.remoteRepository, branch : repo.remoteBranch, dir : repo.name])
    }
    echo ""
    echo "------------------------------------------------------------------------"
    echo "INFRASTRUCTURE_REPOSITORY_URL : ${props.INFRASTRUCTURE_REPOSITORY_URL}"
    echo "INFRASTRUCTURE_REPOSITORY_BRANCH : ${props.INFRASTRUCTURE_REPOSITORY_BRANCH}"

    echo "DEPLOYMENT_REPOSITORY_URL : ${props.DEPLOYMENT_REPOSITORY_URL}"
    echo "DEPLOYMENT_REPOSITORY_BRANCH : ${props.DEPLOYMENT_REPOSITORY_BRANCH}"

    for (repo in scenarioConfigs) {
        echo "SCENARIOS_REPOSITORY_URL : ${repo.get("url")}"
        echo "SCENARIOS_REPOSITORY_BRANCH: ${repo.get("branch")}"
    }

    echo "------------------------------------------------------------------------"
    echo ""
    return scenarioConfigs
}

void cloneRepo(def gitURL, gitBranch, dir) {
    sshagent (credentials: ['github_bot']) {
        sh """
            echo Cloning repository: ${gitURL} into ${dir}
            git clone -b ${gitBranch} ${gitURL} ${dir}
        """
    }
}

/**
 * Add hostUrl to knownhosts on the system (or container) if necessary so that ssh commands will go
 * through even if the certificate was not previously seen.
 * @param hostUrl
 */
void tryAddKnownHost(String hostUrl){
    // ssh-keygen -F ${hostUrl} will fail (in bash that means status code != 0) if ${hostUrl} is not yet a known host
    def statusCode = sh script:"ssh-keygen -F ${hostUrl}", returnStatus:true
    if(statusCode != 0){
        sh "mkdir -p ~/.ssh"
        sh "ssh-keyscan ${hostUrl} >> ~/.ssh/known_hosts"
    }
}

static def getRepositoryBranch(def branch) {
    if (branch != null) {
        branch
    } else {
        "master"
    }
}

/**
 * Builds the blueocean console link for a given test plan
 *
 * @param buildURL Jenkins classic build URL
 * @return Blueocean link
 */
def generateBluoceanLink(def parallelId) {
    def completeJobName = "${env.JOB_NAME}"
    def scapedURL = URLEncoder.encode(completeJobName, StandardCharsets.UTF_8.name())
    def split = completeJobName.split("/")
    def jobName = split[split.length-1]

    def url = "${env.JENKINS_URL}" + "blue/organizations/jenkins/" + "${scapedURL}" +
                "/detail/" + "${jobName}" + "/" + "${env.BUILD_ID}" + "/pipeline/" + "${parallelId}"
    return url
}

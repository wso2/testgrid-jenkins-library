/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
import org.wso2.tg.jenkins.alert.Email
import org.wso2.tg.jenkins.executors.TestGridExecutor

// The pipeline should reside in a call block
def call() {
  // Setting the current pipeline context, this should be done initially
  PipelineContext.instance.setContext(this)
  // Initializing environment properties
  def props = Properties.instance
  props.instance.initProperties()

  def log = new Logger()
  def executor = new TestGridExecutor()
  def email = new Email()

  pipeline {

    pipeline {
      agent {
        node {
          label ""
          customWorkspace "/testgrid/testgrid-home/jobs/${JOB_BASE_NAME}"
        }
      }

      environment {
        TESTGRID_HOME = "/testgrid/testgrid-home"
        WORKSPACE = "${TESTGRID_HOME}/jobs/${JOB_BASE_NAME}"
        PWD = pwd()
        OS_USERNAME = credentials('OS_USERNAME')
        OS_PASSWORD = credentials('OS_PASSWORD')
        WUM_APPKEY_UAT = credentials('WUM_APPKEY_UAT')
        WUM_APPKEY_LIVE = credentials('WUM_APPKEY_LIVE')
        JOB_CONFIG_YAML = "job-config.yaml"
        JOB_CONFIG_YAML_PATH = "${PWD}/${JOB_CONFIG_YAML}"
        CONFIG_FILE_UAT = "${WORKSPACE}/WUM_LOGS/config-uat.txt"
        CONFIG_FILE_LIVE = "${WORKSPACE}/WUM_LOGS/config-live.txt"
        RESPONSE_TIMESTAMP = "${WORKSPACE}/WUM_LOGS/response-timestamp.txt"
        RESPONSE_PRODUCT = "${WORKSPACE}/WUM_LOGS/response-product.txt"
        PRODUCT_LIST = "${WORKSPACE}/WUM_LOGS/product-list.txt"
        RESPONSE_CHANNEL = "${WORKSPACE}/WUM_LOGS/response-channel.txt"
        CHANNEL_LIST = "${WORKSPACE}/WUM_LOGS/channel-list.txt"
        JOB_LIST = "${WORKSPACE}/WUM_LOGS/job-list.txt"
        UPDATENO_LIST = "${WORKSPACE}/WUM_LOGS/updateNo-list.txt"
        PRODUCT_ID = "${WORKSPACE}/WUM_LOGS/product-id.txt"
        PRODUCT_ID_LIST = "${WORKSPACE}/WUM_LOGS/product-id-list.txt"
      }

      stages {
        stage('Preparation') {
          steps {
            script {
              sh """
				            echo ${JOB_CONFIG_YAML_PATH}
					        echo '  TEST_TYPE: ${TEST_TYPE}' >> ${JOB_CONFIG_YAML_PATH}
						        
					        cd ${WORKSPACE}
                            rm -rf WUM_LOGS
                            mkdir WUM_LOGS
                            cd ${WORKSPACE}/WUM_LOGS
					        git clone ${SCENARIOS_REPOSITORY}

					        cd ${WORKSPACE}/WUM_LOGS/test-integration-tests-runner
					        sh get-wum-uat-products.sh
                        """
            }
          }
        }

        stage('parallel-run') {
          steps {
            script {
              try {
                def wumJobs = [:]
                FILECONTENT = readFile "${JOB_LIST}"
                sh """
                                cd ${WORKSPACE}
                                rm -rf sucessresult.txt failresult.txt
                                touch sucessresult.txt failresult.txt
                             """
                def jobNamesArray = FILECONTENT.split('\n')
                for (line in jobNamesArray) {
                  String jobName = line;
                  wumJobs["job:" + jobName] = {
                    def result = build(job: jobName, propagate: false).result
                    if (result == 'SUCCESS') {
                      def output = jobName + " - " + result
                      sh "echo $output >> ${WORKSPACE}/sucessresult.txt "

                    } else if (result == 'FAILURE') {
                      def output = jobName + " - " + result
                      sh "echo $output >> ${WORKSPACE}/failresult.txt "

                    } else if (result == 'UNSTABLE') {
                      def output = jobName + " - " + result
                      sh "echo $output >> ${WORKSPACE}/failresult.txt "
                    }

                  };
                }
                parallel wumJobs

              } catch (e) {
                echo "Few of the builds are not found to trigger."
              }
            }
          }
        }

        stage('result') {
          steps {
            script {
              try {
                sh """
                          cd ${WORKSPACE}
                          cat sucessresult.txt
                          cat failresult.txt
                          cd ${WORKSPACE}/WUM_LOGS/
                         
                        """

                if (fileExists("${WORKSPACE}/sucessresult.txt")) {
                  def emailBodySuccess = readFile "${WORKSPACE}/sucessresult.txt"
                  def emailBodyFail = readFile "${WORKSPACE}/failresult.txt"
                  def productList = readFile "${WORKSPACE}/WUM_LOGS/product-list.txt"
                  def updateNo = readFile "${WORKSPACE}/WUM_LOGS/product-id-list.txt"

                  send("WSO2 TestGrid - Test Results for WUM Updates! #(${env.BUILD_NUMBER})", """ <table width="800" border="0" cellspacing="0" cellpadding="0" valign='top'>
                                <td><img src="http://cdn.wso2.com/wso2/newsletter/images/nl-2017/nl2017-wso2-logo-wb.png"></td>
                                <td><h1>TestGrid <span style="color: #e46226;">: Test Execution Job Status</span></h1></td>
                                </table><div style="margin: auto; background-color: #ffffff;"> 
                       
                                <p style="height:10px;font-family: Lucida Grande;font-size: 20px;"><font color="black"><b> Testgrid Job Status </b></font> </p>

                                <table width="70%" cellspacing="0" cellpadding="1" border="1" align="left" bgcolor="#f0f0f0">
                                <col width="130">
                                <col width="130">
                                <tr style="border: 1px solid black;">
                                <th bgcolor="#33cc33">Job Name with Success Result</th>
                                <th bgcolor="#cc3300">Job Name with Fail Result</th>
                                </tr>
                                <tr>
                                <td>${emailBodySuccess}</td>
                                <td>${emailBodyFail}</td>
                                </tr>
                                </table>
                                <br /><br /><br /><br /><br /><br /><br /><br />
                                  
                                <p style="height:10px;font-family:Lucida Grande;font-size: 20px;"><font color="black"><b>Product Informations </b></font> </p>
                                <table width="70%" cellspacing="0" cellpadding="1" border="1" align="left" bgcolor="#f0f0f0">
                                <col width="130">
                                <col width="130">
                                <tr style="border: 1px solid black;">
                                <th bgcolor="#ffb84d">Product List with Version</th>
                                <th bgcolor="#ffb84d">Product Updated No</th>
                                </tr>
                                <tr>
                                <td>${productList}</td>
                                <td>${updateNo}</td>
                                </tr>
                                </table>
                                <br /><br /><br /><br /><br /><br /><br /><br /><br /><br /><br /><br />

                                <em>Tested by WSO2 TestGrid.</em>""")

                } else {
                  log.info("No WUM Update found..!")
                  send("Testgrid Test Results Summary for WUM Updates!", "No WUM Update found..!")
                }
                //Delete the WUM_LOGS
                dir("${WORKSPACE}/WUM_LOGS") {
                  deleteDir()
                }

              } catch (e) {
                currentBuild.result = "FAILED"
              }
            }
          }
        }
      }

    }

  }
}

def send(subject, content) {
  emailext(to: "${EMAIL_TO_LIST}",
          subject: subject,
          body: content, mimeType: 'text/html')
}

/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

// The pipeline should reside in a call block
def call() {

  pipeline {

    pipeline {
      agent {label 'pipeline-agent'}

      environment {
        TESTGRID_HOME = "/home/ubuntu/workspace"
        WORKSPACE = "${TESTGRID_HOME}/jobs/${JOB_BASE_NAME}"
        PWD = pwd()
        JOB_CONFIG_YAML = "job-config.yaml"
        JOB_CONFIG_YAML_PATH = "${PWD}/${JOB_CONFIG_YAML}"
        PRODUCT_LIST = "${WORKSPACE}/WUM_LOGS/product-list.txt"
        JOB_LIST = "${WORKSPACE}/WUM_LOGS/job-list.txt"
        SCENARIO_BUILD_URL = "https://testgrid.wso2.com/job/WUM/job/Scenario-Tests/"
      }

      stages {
        stage('Preparation') {
            steps {
                script {
                withCredentials([string(credentialsId: 'WUM_USERNAME', variable: 'wumUserName'),
                string(credentialsId: 'WUM_PASSWORD', variable: 'wumPassword'),
                string(credentialsId: 'AWS_ACCESS_KEY_ID', variable: 's3accessKey'),
                string(credentialsId: 'AWS_SECRET_ACCESS_KEY', variable: 's3secretKey'),
                string(credentialsId: 'TESTGRID_EMAIL_PASSWORD', variable: 'testgridEmailPassword')])
                {
                    sh '''
                        echo "Writting WUM Password to parameter file"
                        ./scripts/write-parameter-file.sh "WUMPassword" ${wumPassword} "${WORKSPACE}/parameters/parameters.json"
                        echo "Writting WUM Username to parameter file"
                        ./scripts/write-parameter-file.sh "WUMUsername" ${wumUserName} "${WORKSPACE}/parameters/parameters.json"
                        echo "Writting DB password to parameter file"
                        ./scripts/write-parameter-file.sh "S3AccessKeyID" ${s3accessKey} "${WORKSPACE}/parameters/parameters.json"
                        echo "Writting S3 secret access key to parameter file"
                        ./scripts/write-parameter-file.sh "S3SecretAccessKey" ${s3secretKey} "${WORKSPACE}/parameters/parameters.json"
                        echo "Writing testgrid email key to parameter file"
                        ./scripts/write-parameter-file.sh "TESTGRID_EMAIL_PASSWORD" ${testgridEmailPassword} "${WORKSPACE}/parameters/parameters.json"
                    '''
                }
                    sh '''
                        echo ${JOB_CONFIG_YAML_PATH}
                        echo '  TEST_TYPE: ${TEST_TYPE}' >> ${JOB_CONFIG_YAML_PATH}
                        cd ${WORKSPACE}
                        rm -rf WUM_LOGS
                        mkdir WUM_LOGS
                        cd ${WORKSPACE}/WUM_LOGS
                        git clone ${SCENARIOS_REPOSITORY}
                        cd ${WORKSPACE}/WUM_LOGS/test-integration-tests-runner
                        chmod +x get-wum-uat-products.sh
                    '''
                    //Generate S3 Log output path
                    s3BuildLogPath = "${s3BucketName}/artifacts/jobs/wum-parent/build-${BUILD_NUMBER}"
                    println "Your Logs will be uploaded to: s3://"+s3BuildLogPath
                    sh'''
                        echo "Writting S3 Log uploading endpoint to parameter file"
                        ./scripts/write-parameter-file.sh "S3OutputBucketLocation" '''+s3BuildLogPath+''' "${WORKSPACE}/parameters/parameters.json"
                        echo "Writing to parameter file completed!"
                    '''
                    def live_ts = sh(script: '${WORKSPACE}/WUM_LOGS/test-integration-tests-runner/get-wum-uat-products.sh --get-live-timestamp', returnStdout: true).split("\r?\n")[2]
                    def uat_ts = sh(script: '${WORKSPACE}/WUM_LOGS/test-integration-tests-runner/get-wum-uat-products.sh --get-uat-timestamp', returnStdout: true).split("\r?\n")[2]

                    echo "uat timestamp: ${uat_ts} | live timestamp: ${live_ts}"

                    if ( "${uat_ts}" == "${live_ts}" ){
                    echo "There are no updated product packs for the given timestamp in UAT. Hence Skipping the process."
                    currentBuild.result='SUCCESS'
                    return
                    }
                    sh """
                        sh ${WORKSPACE}/WUM_LOGS/test-integration-tests-runner/get-wum-uat-products.sh --get-job-list ${live_ts}
                    """

                }
            }
        }

        stage('parallel-run') {
            steps {
                wrap([$class: 'MaskPasswordsBuildWrapper']) {
                    script {
                        try {
                            def wumJobs = [:]
                            FILECONTENT = readFile "${JOB_LIST}"
                            sh """
                              cd ${WORKSPACE}
                              rm -rf sucessresult.html failresult.html
                              touch sucessresult.html failresult.html
                            """
                            def jobNamesArray = FILECONTENT.split('\n')
                            for (line in jobNamesArray) {
                                String jobName = line;
                                wumJobs["job:" + jobName] = {
                                    def result = build(job: jobName, propagate: false).result
                                    if (result == 'SUCCESS') {
                                        def output = jobName + " - " + result + "<br/>"
                                        sh "echo '$output' >> ${WORKSPACE}/sucessresult.html "

                                    } else if (result == 'FAILURE') {
                                        def output = jobName + " - " + result + "<br/>"
                                        sh "echo '$output' >> ${WORKSPACE}/failresult.html "

                                    } else if (result == 'UNSTABLE') {
                                        def output = jobName + " - " + result + "<br/>"
                                        sh "echo '$output' >> ${WORKSPACE}/failresult.html "
                                    }

                                };
                            }
                            parallel wumJobs

                        } catch (e) {
                            echo "Few of the builds are not found to trigger. " + e
                        }
                    }
                }
            }
        }

        stage('result') {
                steps {
                    wrap([$class: 'MaskPasswordsBuildWrapper']) {
                        script {
                        try {
                            sh """
                    cd ${WORKSPACE}
                    cat sucessresult.html
                    cat failresult.html
                    cd ${WORKSPACE}/WUM_LOGS/
                """

                            if (fileExists("${WORKSPACE}/sucessresult.html")) {
                                def emailBodySuccess = readFile "${WORKSPACE}/sucessresult.html"
                                def emailBodyFail = readFile "${WORKSPACE}/failresult.html"
                                String productList = readFile "${PRODUCT_LIST}"
                                productList = convertToHtml(productList);
                                def updateNo = readFile "${WORKSPACE}/WUM_LOGS/product-id-list.txt"

                  send("[TestGrid][WUM] Scenario Test Results for WUM Updates! #(${env.BUILD_NUMBER})", """
<div style="padding-left: 10px">
  <div style="height: 4px; background-image: linear-gradient(to right, orange, black);">

  </div>
  <table border="0" cellspacing="0" cellpadding="0" valign='top'>
    <td>
      <h1>Scenario test results for <span style="color: #e46226;">WUM updates</span>
      </h1>
    </td>

    <td>
      <img src="http://cdn.wso2.com/wso2/newsletter/images/nl-2017/nl2017-wso2-logo-wb.png"/>
    </td>
  </table>
  <div style="margin: auto; background-color: #ffffff;">
    <p style="height:10px;font-family: Lucida Grande;font-size: 20px;">
      <font color="black">
        <b> Testgrid job status </b>
      </font>
    </p>
    <table cellspacing="0" cellpadding="0" border="2" bgcolor="#f0f0f0" width="80%">
      <colgroup>
        <col width="150"/>
        <col width="150"/>
      </colgroup>
      <tr style="border: 1px solid black; font-size: 16px;">
        <th bgcolor="#05B349" style="padding-top: 3px; padding-bottom: 3px">Success jobs</th>
        <th bgcolor="#F44336" style="black">Failed/Unstable jobs</th>
      </tr>
      <tr>
        <td>${emailBodySuccess}</td>
        <td>${emailBodyFail}</td>
      </tr>
    </table>
    <br/>
    <p style="height:10px;font-family:Lucida Grande;font-size: 20px;">
      <font color="black">
        <b>Product information</b>
      </font>
    </p>
    <table cellspacing="0" cellpadding="1" border="1" width="80%" style="font-size: 16px; background-color: #f0f0f0">
      <colgroup>
        <col width="150"/>
        <col width="150"/>
      </colgroup>
      <tr style="border: 1px solid black; padding-top: 3px; padding-bottom: 3px; background-color: #9E9E9E;">
        <th>Product version</th>
        <th>Update no.</th>
      </tr>
      <tr>
        <td>${productList}</td>
        <td>${updateNo}</td>
      </tr>
    </table>
    <br/>
    <p style="height:10px;font-family:Lucida Grande;font-size: 20px;">
      <font color="black">
        <b>Build Info:</b>
        <small><a href="${SCENARIO_BUILD_URL}">${SCENARIO_BUILD_URL}</a></small>
      </font>
    </p>
    <br/>
    <em>Tested by WSO2 TestGrid.</em>
  </div>
</div>
                            """)

                            } else {
                                println "No WUM Update found..!"
                                send("Testgrid Test Results Summary for WUM Updates!", "No WUM Update found..!")
                            }
                            //Delete the WUM_LOGS
                            dir("${WORKSPACE}/WUM_LOGS") {
                                deleteDir()
                            }

                        } catch (e) {
                            echo "Error while sending mail: " + e.getMessage()
                            currentBuild.result = "FAILED"
                        }
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

static def convertToHtml(String productList) {
  StringBuilder sb = new StringBuilder("<div>");
  for (String p : productList.split("\n")) {
    sb.append(p).append("<br/>")
  }
  sb.append("</div>")
  return sb.toString()
}

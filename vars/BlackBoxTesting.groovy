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

node {

  stage('Test Setup') {
    withCredentials([string(credentialsId: 'TESTGRID-TEST-EMAIL', variable: 'EMAIL')]) {
      sh """
                    rm -rf ${env.CONFIG}
                    echo 'email=$EMAIL' > ${env.CONFIG}
                    echo 'jenkinsToken=${env.TOKEN}' >> ${env.CONFIG}
                    echo 'buildStatusUrl=${env.PHASE1_STATUS_URL}' >> ${env.CONFIG}
                    echo 'tgApiToken=${env.TG_API_TOKEN}' >> ${env.CONFIG}
                    echo 'tgUser=${env.TG_USER}' >> ${env.CONFIG}
                    echo 'tgUserToken=${env.TG_USER_PASS}' >> ${env.CONFIG}
                """
    }
    withCredentials([string(credentialsId: 'TESTGRID-TEST-EMAIL-PASS', variable: 'EMAIL_PASS')]) {
      sh """
                    echo 'emailPassword=$EMAIL_PASS' >> ${env.CONFIG}
                    cat ${env.CONFIG}
                """
    }
    sh """  
                    rm -rf testRepo
                    git clone ${env.TEST_REPO} testRepo
            """
  }

  stage('Test Run') {
    sh """  
                    export JAVA_HOME=/testgrid/software/java/jdk1.8.0_161
                    export TEST_PROPS=${env.CONFIG}
                    cd testRepo/test/integration-tests
                    mvn clean install \
                    -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn \
                    -fae;
            """
  }
}
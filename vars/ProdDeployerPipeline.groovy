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

import groovy.json.JsonSlurper

/**
 * CI/CD pipeline to push the testgrid latest changes into testgrid dev and prod.
 * Note: this pipeline cannot be run inside groovy sandbox because of err.getCauses()[0] call.
 */
node('COMPONENT_ECS') {
    def MYSQL_DRIVER_LOCATION='http://central.maven.org/maven2/mysql/mysql-connector-java/6.0.6/mysql-connector-java-6.0.6.jar'
    stage('Deploy to Dev'){
        deleteDir()
        copyArtifacts(projectName: 'testgrid/testgrid', filter: 'distribution/target/*.zip, web/target/*.war, deployment-tinkerer/target/*.war');
        sshagent(['testgrid-dev-key']) {
            sh """
                scp -o StrictHostKeyChecking=no web/target/*.war ${DEV_USER}@${DEV_HOST}:/testgrid/deployment/apache-tomcat-8.5.23/webapps/ROOT.war
                scp -o StrictHostKeyChecking=no deployment-tinkerer/target/*.war ${DEV_USER}@${DEV_HOST}:/testgrid/deployment/apache-tomcat-8.5.23/webapps/
                scp -o StrictHostKeyChecking=no distribution/target/*.zip ${DEV_USER}@${DEV_HOST}:/testgrid/testgrid-home/testgrid-dist/WSO2-TestGrid.zip
                ssh -o StrictHostKeyChecking=no ${DEV_USER}@${DEV_HOST} /bin/bash << EOF
                    cd /testgrid/testgrid-home/testgrid-dist/;
                    rm -rf WSO2-TestGrid-unzipped;
                    unzip -q WSO2-TestGrid.zip -d WSO2-TestGrid-unzipped;
                    curl -o ./WSO2-TestGrid-unzipped/WSO2-TestGrid/lib/mysql.jar ${MYSQL_DRIVER_LOCATION}
                    rm -rf WSO2-TestGrid-backup;
                    mv WSO2-TestGrid WSO2-TestGrid-backup;
                    mv WSO2-TestGrid-unzipped/WSO2-TestGrid WSO2-TestGrid;
                    ls
                """
        }
    }
    def test_result = "FAILURE"
    stage ('Test Dev Deployment'){
        def response1 = sh (returnStdout: true, script: "curl -kX GET ${env.TEST_BUILD_URL}${env.TEST_TOKEN} --user ${env.TG_USER}:${env.TG_USER_PASS}")
        echo "Response1: " + response1
        def jobResult = null
        while(jobResult == null) {
            sleep 3
            def response = sh (returnStdout: true, script: "curl --silent -kX GET ${env.TEST_STATE_URL} --user ${env.TG_USER}:${env.TG_USER_PASS}")
            def json = new JsonSlurper().parseText(response)
            jobResult = json.result
        } // end while        
        println("E2e test job completed with status: " + jobResult)
        test_result = jobResult;
    }
    stage('Deploy to Prod'){
        if(test_result == "SUCCESS") {
            def userInput
            mail (
                    to: "${TEAM_MEMBERS},builder@wso2.org",
                    subject: "Interrupt Testgrid (${env.BUILD_NUMBER}) production deployment?",
                    body: "Hi folks,\nLatest TG distribution is deployed to TestGrid-DEV." +
                            "\nPlease do a round of tests, and if unsuccessful, visit ${env.BUILD_URL}/input to ABORT prod deployment." +
                            "\n Note: verify if the existing slaves received the latest pack." +
                            "\nYou have 4 hours left!\n\nThanks!");
            try {
                timeout(time: 4, unit: 'HOURS') {
                    try {
                        userInput = input(
                                message: 'Deploy to Prod environment?', parameters: [
                                [$class: 'BooleanParameterDefinition', defaultValue: true, description: '', name: 'Please confirm you agree with this.']
                        ])
                    } catch(err) {
                        echo "Aborted the prod deployment by user."
                        userInput = false
                    }
                }
            } catch(err) { // input false
                echo "Waiting timeout exceeded. Going forward with PROD deployment."
                userInput = true
            }
            if (userInput == true) {
                sshagent(['testgrid-prod-key']) {
                    sh """
                        scp -o StrictHostKeyChecking=no web/target/*.war ${PROD_USER}@${PROD_HOST}:/testgrid/deployment/apache-tomcat-8.5.24/webapps/ROOT.war
                        scp -o StrictHostKeyChecking=no deployment-tinkerer/target/*.war ${PROD_USER}@${PROD_HOST}:/testgrid/deployment/apache-tomcat-8.5.24/webapps/
                        scp -o StrictHostKeyChecking=no distribution/target/*.zip ${PROD_USER}@${PROD_HOST}:/testgrid/testgrid-home/testgrid-dist/WSO2-TestGrid.zip
                        ssh -o StrictHostKeyChecking=no ${PROD_USER}@${PROD_HOST} /bin/bash << EOF
                            cd /testgrid/testgrid-home/testgrid-dist/;
                            rm -rf WSO2-TestGrid-unzipped;
                            unzip -q WSO2-TestGrid.zip -d WSO2-TestGrid-unzipped;
                            curl -o ./WSO2-TestGrid-unzipped/WSO2-TestGrid/lib/mysql.jar ${MYSQL_DRIVER_LOCATION}
                            rm -rf WSO2-TestGrid-backup;
                            mv WSO2-TestGrid WSO2-TestGrid-backup;
                            mv WSO2-TestGrid-unzipped/WSO2-TestGrid WSO2-TestGrid;
                            ls
                     """
                }
            } else {
                echo "Not proceeding with prod deployment."
            }
        } else {
            echo 'Tests have failed. Please fix the tests in order to do the prod deployment.'
            currentBuild.result = test_result
            currentBuild.description = 'E2e Tests have failed. Please fix the tests in order to do the prod deployment.'
            mail (
                    to: "${TEAM_MEMBERS}",
                    subject: "Testgrid prod deploy failed - (${env.BUILD_NUMBER})",
                    body: "Hi folks,\n" +
                            "\nSome issue with blackbox testing detected." +
                            "\nFor details, visit ${env.BUILD_URL}." +
                            "\n\nThanks!")

        }

    }

}

import groovy.json.*

pipeline {
    agent any
    environment {
        //Set the jq path
        PATH = "/usr/local/Cellar/jq/1.5_3/bin:$PATH"
        
        //Testgrid LDAP username and password
        OS_USERNAME = credentials('OS_USERNAME')
        OS_PASSWORD = credentials('OS_PASSWORD')

        //WUM UAT Appkey
        UAT_APPKEY = credentials('UAT_APPKEY')
        //WUM LIVE Appkey
        LIVE_APPKEY = credentials('LIVE_APPKEY')
        PWD = pwd()
        JOB_CONFIG_YAML = "job-config.yaml"
        JOB_CONFIG_YAML_PATH = "${PWD}/${JOB_CONFIG_YAML}"
        CONFIG_FILE_UAT='/workspace/wum_script/config-uat.txt'
        CONFIG_FILE_LIVE='/workspace/wum_script/config-live.txt'
        RESPONSE_TIMESTAMP='/workspace/wum_script/response-timestamp.txt'
        RESPONSE_PRODUCT='/workspace/wum_script/response-product.txt'
        PRODUCT_LIST='/workspace/wum_script/product-list.txt'
        RESPONSE_CHANNEL='/workspace/wum_script/response-channel.txt'
        CHANNEL_LIST='/workspace/wum_script/channel-list.txt'
    }
    stages {
        stage('Preparation') {
            steps {
                script {
                    echo "PATH is: $PATH"
                    echo pwd()

                    sh """
                          echo ${JOB_CONFIG_YAML_PATH}
                          echo '  SCENARIOS_REPOSITORY: ${SCENARIOS_REPOSITORY}' >> ${JOB_CONFIG_YAML_PATH}
                          echo '  SCENARIOS_LOCATION: ${SCENARIOS_LOCATION}' >> ${JOB_CONFIG_YAML_PATH}
                          echo OS_USERNAME : ${OS_USERNAME}
                          echo OS_PASSWORD : ${OS_PASSWORD}
                          echo UAT_APPKEY : ${UAT_APPKEY}
                          echo LIVE_APPKEY : ${LIVE_APPKEY}
                         
                          #clon the apim integration repo which contains the WUM script
                          cd ${SCENARIOS_LOCATION}
                          if [ ! -d "${SCENARIOS_LOCATION}/apim-test-integration" ] ; then
                              git clone ${SCENARIOS_REPOSITORY}
                          fi
                          cd ${SCENARIOS_LOCATION}

                          # Start running WUM script which have the whole procees of calling to WUM UAT and get the Products, Version and channel list
                          mkdir wum_script
                          cp -f ${SCENARIOS_LOCATION}/apim-test-integration/newshell.sh ${SCENARIOS_LOCATION}/wum_script
                          cd ${SCENARIOS_LOCATION}/wum_script 
                          sh get-wum-uat-products.sh
                    
                    """
                }
            }
        }
        stage('parallel-run') {
            steps {
                script {
                    try{
                        //Converting job name list text as a json file
                        sh """
                            jq -Rs '[ split("\n")[] | select(length > 0)
                                | split(",") | {JobName: .[0]} ]' "${SCENARIOS_LOCATION}/wum_script/job-list.txt" > "${SCENARIOS_LOCATION}/wum_script/job-list.json"
                         """
                        def inputFile = new File("${SCENARIOS_LOCATION}/wum_script/job-list.json")
                        def jsonSulper = new JsonSlurper()
                        def InputJSON = jsonSulper.parseText(inputFile.text)
                        jsonSulper = null

                        def wumJobs = [:]

                        //Get the job name from the job-list.json and trigger builds pararelly
                        for (int index = 0; index < InputJSON.size(); index++) {
                            String jobName = InputJSON[index].JobName;
                            wumJobs["job:"+ jobName] = {build job: jobName};
                        }
                        InputJSON = null
                        parallel wumJobs

                        sh """
                        set +e
                        """
                    }catch(e){
                        echo "Few of the builds are not found to trigger."

                    }

                    //Delete the created directory which have the all the text files.
                    dir("${SCENARIOS_LOCATION}/wum_script") {
                        deleteDir()
                    }
                }
            }
        }
    }
}
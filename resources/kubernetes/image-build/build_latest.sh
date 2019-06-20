#!/usr/bin/env bash
# This will build Docker images for all the latest WSO2 products to use in testgrid
#set -e
LOG_FILE="$(date +%F).log"
AUTH_SERVER_URL="https://dockerauth.wso2.com/auth?service=WSO2%20Docker%20registry&scope="
MAIL="nishika@wso2.com"
TAG_LATEST=1

# Git repo URLs
APIM_GIT_REPO_URL_260="https://github.com/NishikaDeSilva/docker-apim-support/archive/v2.6.0.1.zip"
EI_GIT_REPO_URL_640="https://github.com/NishikaDeSilva/jenkins-test/blob/master/docker-products/ei-6.4.x.zip?raw=true"
IS_GIT_REPO_URL_570="https://github.com/NishikaDeSilva/jenkins-test/blob/master/docker-products/is-5.7.x.zip?raw=true"

# name of the unzipped directory
APIM_GIT_REPO_NAME_260="docker-apim-support-2.6.0.1"
EI_GIT_REPO_NAME_640="docker-ei-6.4.x"
IS_GIT_REPO_NAME_570="docker-is-5.7.x"

echo "----------------------------------------Building new images with latest updates--------------------------------------------------"

echo "----------------------------------------Building wso2am-2.6.0---------------------------------------------------------"
      ./docker_build.sh --log-file ${LOG_FILE} \
      --git-repo-zip-url ${APIM_GIT_REPO_URL_260} \
      --product-name "wso2am-analytics" \
      --wso2-server-version "2.6.0" \
      --git-repo-name ${APIM_GIT_REPO_NAME_260} \
      --docker-file-dir "ubuntu/apim-analytics/base/" \
      --tag "2.6.0" \
      --docker-repo-name "wso2am-analytics" \
      --mail ${MAIL} \

      if [ $? -ne 0 ]; then
       exit 1
      fi

echo "---------------------------------------------------------Building wso2is-5.7.0---------------------------------------------------------"
      ./docker_build.sh --log-file ${LOG_FILE} \
      --git-repo-zip-url ${IS_GIT_REPO_URL_570} \
      --product-name "wso2is" \
      --wso2-server-version "5.7.0" \
      --git-repo-name ${IS_GIT_REPO_NAME_570} \
      --docker-file-dir "ubuntu/is/"\
      --tag "5.7.0" \
      --docker-repo-name "wso2is" \
      --mail ${MAIL} \

      if [ $? -ne 0 ]; then
       exit 1
      fi

echo "---------------------------------------------------------Building wso2ei-base:6.4.0---------------------------------------------------------"
    ./docker_build.sh --log-file ${LOG_FILE} \
      --git-repo-zip-url ${EI_GIT_REPO_URL_640} \
      --product-name "wso2ei" \
      --wso2-server-version "6.4.0" \
      --git-repo-name ${EI_GIT_REPO_NAME_640} \
      --docker-file-dir "ubuntu/integrator/" \
      --tag "6.4.0" \
      --docker-repo-name "wso2ei-base" \
      --mail ${MAIL} \


    if [ $? -ne 0 ]; then
      exit 1
    fi

if [ $(docker images | grep "<none>") ]; then
  docker rmi $(docker images | grep "<none>" | awk '{print $3}')
fi
echo "Docker Image building for all the products is successful !"

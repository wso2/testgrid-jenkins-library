#! /bin/bash

CONFIG_FILE_LIVE=test.txt
WUM_APPKEY_LIVE="WUNMR2hnaGdIU25FajB4SngzNkRSeDFOT1pVYTp5enB4SWd6bWpncjVlWWNkdXFhblpYc2JCRXNh"
OS_USERNAME="testgridwum@wso2.com"
OS_PASSWORD='kDQ0olVR6R*2y8$1bN-=L<-lrY#2455V'

#Generating access token for WUM Live environment to get the latest live synced timestamp
createAccessTokenLive() {
	echo "Generating Access Token for Live"
    uri="https://gateway.api.cloud.wso2.com/token"
    #echo "Calling URI (POST): " ${uri}
    curl -s -X POST -H "Content-Type:application/x-www-form-urlencoded" \
         -H "Authorization:Basic ${WUM_APPKEY_LIVE}" \
         -d "grant_type=password&username=${OS_USERNAME}&password=${OS_PASSWORD}" "${uri}" \
         --output "${CONFIG_FILE_LIVE}"
    #live_access_token=$( jq -r ".access_token" $CONFIG_FILE_LIVE )
}
createAccessTokenLive

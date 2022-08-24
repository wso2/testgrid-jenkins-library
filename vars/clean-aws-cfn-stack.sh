#! /bin/bash

# Copyright (c) 2022, WSO2 LLC. (http://wso2.com) All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

while getopts a:s: flag
do
    case "${flag}" in
        a) AWS_ACCESS_KEY_ID=${OPTARG};;
        s) AWS_SECRET_ACCESS_KEY=${OPTARG};;
    esac
done

## cloudformation params ##
status_filter='ROLLBACK_COMPLETE DELETE_FAILED CREATE_COMPLETE'

## date ##
date_12H=$(date -u -d "-12 hour" +"%Y-%m-%dT%H:%M:%SZ") ### 12 HOURS ###

echo $date_12H

for region in us-east-1 us-east-2 us-west-1 us-west-2;
do
    for f in `AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY aws cloudformation list-stacks --region $region --output text \
    --stack-status-filter $status_filter \
    --query "StackSummaries[?CreationTime < '${date_12H}'].{StackName:StackName}"` \
    ;
    do
        echo "deleting stack --region $region --stack-name $f"
        AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY aws cloudformation delete-stack --region $region --stack-name $f
    done
       
done

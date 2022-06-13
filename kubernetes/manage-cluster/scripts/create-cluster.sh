#!/bin/bash
# -------------------------------------------------------------------------------------
# Copyright (c) 2022 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
#
# WSO2 Inc. licenses this file to you under the Apache License,
# Version 2.0 (the "License"); you may not use this file except
# in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
# --------------------------------------------------------------------------------------

isEmpty () {
    if [ ${#1} -ge 1 ];
        then 
            return 0;
    else
        return 1;
    fi;
}

isEmpty "${EKS_CLUSTER_NAME}";
flag=$?
if [ $flag = 1 ];
    then echo "EKS_CLUSTER_NAME environment variable is empty."; exit 1
fi;

isEmpty "${EKS_CLUSTER_REGION}";
flag=$?
if [ $flag = 1 ];
    then echo "EKS_CLUSTER_REGION environment variable is empty."; exit 1
fi;

isEmpty "${EKS_CLUSTER_NAME}";
flag=$?
if [ $flag = 1 ];
    then echo "EKS_CLUSTER_NAME environment variable is empty."; exit 1
fi;


cat ./kubernetes/manage-cluster/scripts/cluster-blueprint-template.yaml | envsubst '${EKS_CLUSTER_NAME}' | envsubst '${EKS_CLUSTER_REGION}' > ./kubernetes/manage-cluster/scripts/cluster-blueprint.yaml
eksctl get cluster --region ${EKS_CLUSTER_REGION} -n ${EKS_CLUSTER_NAME} && echo "Cluster already exists" && exit 1
eksctl create cluster -f ./kubernetes/manage-cluster/scripts/cluster-blueprint.yaml || { echo "Failed to create the cluster. Try to clean up the created resources." ; eksctl delete cluster --region="${EKS_CLUSTER_REGION}" --name="${EKS_CLUSTER_NAME}" || { echo "Failed to cleanup the resources using eksctl, trying to delete the cloudformation stacks." ; aws cloudformation delete-stack --region "${EKS_CLUSTER_REGION}" --stack-name "eksctl-${EKS_CLUSTER_NAME}-cluster"; aws cloudformation wait stack-delete-complete --region ${EKS_CLUSTER_REGION} --stack-name "eksctl-${EKS_CLUSTER_NAME}-cluster"; exit 1 ; }  }
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.0.4/deploy/static/provider/aws/deploy.yaml 

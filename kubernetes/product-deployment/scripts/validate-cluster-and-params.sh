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

workingdir=$(pwd)
reldir=`dirname $0`
cd $reldir


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

isEmpty "${RDS_STACK_NAME}";
flag=$?
if [ $flag = 1 ];
    then echo "RDS_STACK_NAME environment variable is empty."; exit 1
fi;

isEmpty "${path_to_helm_folder}";
flag=$?
if [ $flag = 1 ];
    then echo "Path to helm folder is empty."; exit 1
fi;

isEmpty "${product_version}";
flag=$?
if [ $flag = 1 ];
    then echo "Product version is empty."; exit 1
fi;

isEmpty "${db_engine}";
flag=$?
if [ $flag = 1 ];
    then echo "DB engine value is empty."; exit 1
fi;


# Update kube config file.
aws eks update-kubeconfig --region ${EKS_CLUSTER_REGION} --name ${EKS_CLUSTER_NAME} || { echo 'Failed to update cluster kube config.';  exit 1; }

# Check whether a cluster exists.
eksctl get cluster --region ${EKS_CLUSTER_REGION} -n ${EKS_CLUSTER_NAME} || { echo 'Cluster does not exists. Please create the cluster before deploying the applications.';  exit 1; }

cd "$workingdir"

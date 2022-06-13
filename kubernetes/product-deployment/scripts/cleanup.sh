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

productName=$1;
echo "Scaling node group instances to zero."
eksctl scale nodegroup --region ${EKS_CLUSTER_REGION} --cluster ${EKS_CLUSTER_NAME} --name ng-1 --nodes=0 || true
sh ./kubernetes/product-deployment/scripts/"$productName"/cleanup.sh || true
echo "Deleting RDS database." || true
aws cloudformation delete-stack --region ${EKS_CLUSTER_REGION} --stack-name ${RDS_STACK_NAME} ; aws cloudformation wait stack-delete-complete --region ${EKS_CLUSTER_REGION} --stack-name ${RDS_STACK_NAME} || true

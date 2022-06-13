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

workingdir=$(pwd)
reldir=`dirname $0`
cd $reldir

dbEngine=""
if [ "${db_engine}" = "postgres" ];
    then 
        dbEngine="postgres"
elif [ "${db_engine}" = "mysql" ];
    then 
        dbEngine="mysql"
elif [ "${db_engine}" = "mssql" ];
    then 
        dbEngine="sqlserver-ex"
elif [ "${db_engine}" = "oracle" ];
    then 
        dbEngine="oracle-se2"
else
    echo "The specified DB engine not supported.";
    exit 1;
fi;

aws cloudformation create-stack --region ${EKS_CLUSTER_REGION} --stack-name ${RDS_STACK_NAME}   --template-body file://testgrid-rds-cf.yaml --parameters ParameterKey=pDbUser,ParameterValue="${DB_USERNAME}" ParameterKey=pDbPass,ParameterValue="${DB_PASSWORD}"  ParameterKey=pDbEngine,ParameterValue="$dbEngine" ParameterKey=pDbVersion,ParameterValue="${db_version}" ParameterKey=pDbInstanceClass,ParameterValue="${db_instance_class}" || { echo 'Failed to create RDS stack.';  exit 1; }

# Wait for RDS DB to come alive.
aws cloudformation wait stack-create-complete --region ${EKS_CLUSTER_REGION} --stack-name ${RDS_STACK_NAME} || { echo 'RDS stack creation timeout.';  exit 1; }

cd "$workingdir"

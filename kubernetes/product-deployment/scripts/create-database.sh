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
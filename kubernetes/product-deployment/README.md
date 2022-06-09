# Kubernetes product deployment pipeline

The pipeline can be used to deploy and test application in kubernetes cluster.

## Jenkins version, blueocean version

Jenkins:- Jenkins 2.319.3
Blueocean Version:- 1.25.3

## Kubernetes product deployment pipeline Variables

### Product Details

1. **product_name** - The name of the product to be deployed to the kubernetes cluster.
   Allowed values - [ apim ]
2. **product_version** - Product version to be deployed. 

## Deployment Details

3. **deployment_script_repo_url** - Repository URL of the deployment scripts for the specific application. TestGrid expects a deploy.sh file and cleanup.sh file in the given repository. The deploy.sh script will be called by TestGrid. The deploy.sh file contains the scripts for deploying the helm charts, database preparation related commands etc. The cleanup.sh file will contain the necessary scripts to uninstall the product from the cluster. This cleanup.sh file will be called at the end of the testing by TestGrid. The product team will prepare this repository with necessary scripts for the deployment of their specific product. 
4. **deployment_script_repo_branch** - Branch of the repository. 
5. **db_engine** - TestGrid supports provisioning database. User can select different databases for the applications. Supported databases are MySql, Postgres, MSSQL and Oracle. Allowed values [ mysql, postgres, mssql, oracle ]
6. **db_version** - Database version.
7. **db_instance_class** - TestGrid will provision the database using AWS RDS. User can specify the instance type. Ex : db.t3.small   

### Kubernetes Details

8. **kubernetes_repo_url** - Product’s helm chart repository.
9. **Kubernetes_repo_branch** - Branch of the helm chart repository.
10. **path_to_helm_folder** - Path to the helm folder in the repository directory structure.
11. **kubernetes_namespace** - The kubernetes cluster namespace name on which the application will be deployed.

### Testing Details

12. **product_testing_repo_url** - Contains the testing related scripts.
13. **product_testing_repo_branch** - Test repository branch.
14. **test_file_path** - Relative file path of the testing entrypoint bash file.
15. **service_startup_timeout** - Amount of time the TestGrid wait for the service to come alive. If all of the kubernetes become healthy within the specified time the TestGrid will start the testing by triggering the test.sh file. Otherwise pipeline will throw an error.  


Check the [Kubernetes deployment pipeline documentation](https://docs.google.com/document/d/1x2CMTP8QJTGmFsLF9DJPVkk5-KwK8NuTaE7PeXW2M5I/edit?usp=sharing)

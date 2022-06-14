# Kubernetes product deployment pipeline

Using TestGrid’s manage-cluster pipeline we are able to create or delete clusters for the deployment purposes. **Note**: when a cluster is running on AWS, a cost of 0.1 dollar/hour is incurred. Deleting the cluster takes about 15 minutes and creating takes about 30 minutes. It is best to schedule cluster creation and deletion jobs in cost-effective way.


## Jenkins version, blueocean version

Jenkins:- Jenkins 2.319.3
Blueocean Version:- 1.25.3

## Creating a cluster

TestGrid supports creating a Kubernetes cluster in AWS. To create a Kubernetes cluster on AWS follow the steps given below:

   1. Go to [TestGrid](https://testgrid.wso2.com/) 
   2. Go to TestGrid Dashboard.
   3. Select ‘Kubernetes deployments’ folder.
   4. Select ‘manage-cluster’ pipeline.
   5. Click ‘Build with parameters’. 
   6. Tick ‘Create’ box in the pipeline UI and untick ‘Delete’ box.
   7. Click ‘Build’ button.

TestGrid supports only one Kubernetes cluster at a given time. If you try to create a new cluster on TestGrid when there is already a cluster running on AWS it will fail to create a cluster. You can use the same cluster to deploy your Kubernetes application.

## Deleting a cluster

You are able to delete a Kubernetes cluster which was created by TestGrid. To delete the cluster follow the steps given below:
   1. Go to [TestGrid](https://testgrid.wso2.com/)
   2. Go to TestGrid Dashboard.
   3. Select the ‘Kubernetes deployments’ folder.
   4. Select ‘manage-cluster’ pipeline.
   5. Click ‘Build with parameters’. 
   6. Tick Delete box in the pipeline UI and untick Create box.
   7. Click ‘Build’ button.

When the user performs the delete action, TestGrid will delete the Kubernetes cluster which was created in “Create a cluster” step. As we already know at a given time TestGrid supports only one Kubernetes cluster. A common use case for deleting the TestGrid Kubernetes cluster would be to save some cost when unused.
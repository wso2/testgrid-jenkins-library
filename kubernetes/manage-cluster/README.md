# Kubernetes product deployment pipeline

Using TestGrid’s manage-cluster pipeline we can delete or create clusters for the deployment purposes. Please note that when there is a cluster up and running on AWS we are charged 0.1 dollar/hour. Deleting the cluster takes about 15 minutes and creating the cluster takes about 30 minutes. It is recommended to schedule the Create cluster job and delete cluster job in a cost effective way. 


## Jenkins version, blueocean version

Jenkins:- Jenkins 2.319.3
Blueocean Version:- 1.25.3

## Create cluster

TestGrid supports creating a kubernetes cluster in AWS. To create a kubernetes cluster on AWS, 

   1. Go to TestGrid 
   2. Go to TestGrid Dashboard 
   3. Go to ‘Kubernetes deployments’ folder 
   4. Select ‘manage-cluster’ pipeline 
   5. Click ‘Build with parameters’. 
   6. Tick ‘Create’ box in the pipeline UI and untick ‘Delete’ box.
   7. Click ‘Build’ button.

TestGrid supports only one kubernetes cluster at a given time. If you try to create a cluster when there is already a cluster running on AWS which was created by TestGrid before, it will fail to create a cluster. You can use the same cluster to deploy your kubernetes application. 

## Delete cluster

You can delete the kubeneretes cluster which was created by TestGrid. To delete the cluster. 
   1. Go to TestGrid 
   2. Go to TestGrid Dashboard 
   3. Go to ‘Kubernetes deployments’ folder 
   4. Select ‘manage-cluster’ pipeline 
   5. Click ‘Build with parameters’. 
   6. Tick Delete box in the pipeline UI and untick Create box.
   7. Click ‘Build’ button.

When user performs this delete action TestGrid will delete the kubernetes cluster which was created in “Create cluster” step. As we already know at a given time TestGrid supports only one kubernestes cluster. Common use case for deleting the TestGrid kubernested cluster would be to save some cost when we dont need the cluster.


Check the [Kubernetes deployment pipeline documentation](https://docs.google.com/document/d/1x2CMTP8QJTGmFsLF9DJPVkk5-KwK8NuTaE7PeXW2M5I/edit?usp=sharing)

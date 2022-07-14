# Testgrid Jenkins Library

This repo contains the related pipeline code that is being used in the Jenkins TestGrid Pipeline.

## Jenkins version, blueocean version

Jenkins:- Jenkins 2.319.3
Blueocean Version:- 1.25.3

## Testgrid Jenkins Pipeline Variables

1. **product**- The WSO2 product that needs to be tested from TestGrid.
Allowed Values- apim, is
2. **cfn_repo_url**- The WSO2 product CFN repo name.
Allowed Value Format- <https://github.com/janethavi/aws-apim.git>
3. **product_version**- The product version that needs to be tested using testgrid.
Allowed Value Format- 3.2.0
4. **product_deployment_region**- The region where the product stack is getting deployed.
Allowed Values- us-east-2, us-east-1
5. **os_list**- The OS and its version. If there are multiple parameters, please add them by separating them by a ","(Comma).
Allowed Values- CentOS7, Ubuntu1804
6. **jdk_list**- The JDK and its version. If there are multiple parameters, please add them by separating them by a ","(Comma).
Allowed Values- OPEN_JDK8, ORACLE_JDK8
7. **database_list**- The Database type and its version. If there are multiple parameters, please add them by separating them by a ","(Comma).
Allowed Values- MySQL-5.7, SQLServer-SE-15.00
8. **use_wum**- If using WUM this should be true. If using U2 this should be false.
Allowed Values- Check or Un Check
9. **product_repository**- The product repo where the test scripts are existing.
Allowed Value Format- <https://github.com/janethavi/product-apim.git>
10. **product_test_branch**- The repo branch where the test script is existing.
Allowed Values- product-scenarios-3.2.0
11. **product_test_script**- The location of the test script.
Allowed Value Format- product-scenarios/test.sh
12. **use_staging**- If testing environment is staging be true. If using UAT this should be false.
Allowed Values- Check or Un Check

Please note all the allowed values are mentioned on the TestGrid pipeline documentation. The above values are the most frequently used values.

## step by step guide to on how to add this repo to a new pipeline

Check the [TestGrid Pipeline Onboarding document](https://docs.google.com/document/d/13uwFCdMyDlJCC-Rd5dDWdxuR4tIrZyJhaXSN4n1XZqM/edit?usp=sharing)

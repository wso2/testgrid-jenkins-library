# Testgrid Jenkins Shared Library

Library that hosts Testgrid shared components and libraries. This contain a bunch of Jenkins pipeline critical for testgrid work:

* [Testgrid job execution pipeline](https://github.com/wso2-incubator/testgrid-jenkins-library/blob/00ee9f2e666119d547ace504f88ce74d6ba97aa1/vars/Pipeline.groovy)
* [GitOps based testgrid job creation pipeline](https://github.com/wso2-incubator/testgrid-jenkins-library/blob/15f7cfbb9e54f09aa859adb505ff64f535d6d20e/vars/JobCreatorPipeline.groovy)

Following is the current folder hierarchy of the source. This structure is required for the Jenkins shared library.

````
.
├── README.md
├── pom.xml
├── src
│   └── org
│       └── wso2
│           └── tg
│               └── jenkins
│                   ├── Log.groovy
│                   ├── alert
│                   │   ├── Email.groovy
│                   │   └── Slack.groovy
│                   ├── executors
│                   │   └── TestExecutor.groovy
│                   └── util
│                       ├── AWSUtils.groovy
│                       └── Common.groovy
├── test
│   └── UtilTest.groovy
└── vars
    ├── Pipeline.groovy <- testgrid job execution pipeline
    └── JobCreatorPipeline.groovy <- testgrid job creator via gitops
````

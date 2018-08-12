# Jenkins Common

Library that hosts Testgrid shared components and libraries that are needed to common runtime

Following is the current hierarchy of the source. This structure is required for the Jenkins shred library.

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
    ├── Const.groovy
    └── Pipeline.groovy
````
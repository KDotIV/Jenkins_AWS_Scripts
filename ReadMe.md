# Serverless Jenkins Pipeline

[TOC]

## Overview

This framework enables continuous iteration within a serverless environment providing with automated code commitments and scheduling.

This utilizes AWS (Amazon Web Services) and React Native framework, staged on a EC2 UNIX instance, a S3 bucket, having Groovyscript and NPM implementation for Jenkins steps. 

This framework uses SonarQube Analysis Quality Gates and ESLint Warnings for code control, testing and retaining standards.

Proxies and artefacts are given through Nexus.

## AWS Instance

### Overview

For hosting and serverless infrastruce a EC2 instance will need to be set up. Depending on security groups and structure multiple instances will be required for scalability, however, it is possible if project is small enough to host all toolsets and environments on one instance.

For more information on [EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/get-set-up-for-amazon-ec2.html) and [S3 buckets](https://aws.amazon.com/s3/getting-started/) documentation follow links provided

## Pipeline Configuration

### Overview

A NPM install can be done to implement Jenkins within the hosted environment following this [documentation](https://jenkins.io/doc/tutorials/build-a-node-js-and-react-app-with-npm/) 

For plug-ins (within Jenkins):

- Amazon Web Services SDK
- Authentication Tokens API Plugin
- Bitbucket Branch Source Plugin
- Bitbucket Plugin
- bouncycastle API PLugin
- Branch API Plugin
- CloudBees
- Javacript libraries
- NodeJS Plugin
- Pipeline Groovy & Libraries
- SonarQube Scanner
- SSH Slaves
- Warnings Next Generation Plugin

### Initial Setup

For "New Item", create a pipeline that allows "Pipeline Script" Definition to allow the use of groovy scripts to execute steps and shell commands

Within build triggers mark "Build when a change is pushed to BitBucket"

Within Jenkins > Manage Jenkins > Configure Credentials create a user credentials that includes Jenkins using an ID and password for it to access repositories. For AWS select "AWS Credentials" from provider dropdown menu and have your Access Key ID and KeyPass be it's credentials.

### Bitbucket Setup

Within the master branch > Settings > Webhooks create a new webhook with the URL to your Jenkins instance, check Status "Active", "Skip Certificate verification" is checked and triggers are "Repository Push"

### SonarQube Setup

Installation and Server Setup is accomplished through this [documentation](https://docs.sonarqube.org/latest/setup/install-server/). Once completed generate Sonar Server Token and create a project.

within */opt/sonar-scanner/conf/sonar-scanner.properties

```
#No information about specific project should appear here

#----- Default SonarQube server (If within the same instance localhost can be used)
sonar.host.url=http://localhost:9000
#----- Default source code encoding
sonar.sourceEncoding=UTF-8

# project must always be the targeted project or it will duplicate if not target incorrect repository
sonar.projectKey=BackendPK
# this is the name and version displayed in the SonarQube UI. Was mandatory prior to SonarQube 6.1.
sonar.projectName=ServerlessBackend
sonar.projectVersion=0.1

# Path is relative to the sonar-project.properties file. Replace "\" by "/" on Windows.
# This property is optional if sonar.modules is set.
sonar.sources=$'target src within jenkinst instance'

sonar.projectBaseDir=$'root src of repository within jenkins instance'
```

Within Jenkins > configuration > SonarQube Servers input the ProjectKey in "Name", input SonarQube URL (default is localhost), and for authentication input the SonarQube Token given.

### Quality Gates and Linter

Profiles can be created within SonarQube that fit project scope. Rules and language that it checks should only be what is necessary for the project. You can assigned quality gates to a specific project by Quality Gates > "$NameofQualityGate" under projects you can choose projects with or without specific gates. For configuration of specific quality gates, follow this [documentation](https://docs.sonarqube.org/latest/user-guide/quality-gates/).

For a JS/Typescript ESlint I recommend [Enact](https://github.com/enactjs/eslint-plugin-enact) as a foundation to what dependencies and configurations to use that fits the project.

## Jenkins Script Execution

### Overview

With Groovyscripts we are able to easily input specific values for our repositories, server URLs, and AWS keys with reusablility without having to hardcode it into a server config file or within the instance itself. 

In order to execute any code must be within the `node{}' as it is how jenkins runs the script and represents the overall stage for the pipeline.

ssh commands can also be used if instance is UNIX/SSH. !#bash can also be used if necessary.

### Packaging
Within the targeted repo there must contain a handler, package.json, and serverless.yml in order to parse to AWS instances and install dependencies. 

.yml example:
```
service: function-with-environment-variables

frameworkVersion: ">=1.2.0 <2.0.0"

provider:
  name: aws
  runtime: nodejs8.10
  environment:
    EMAIL_SERVICE_API_KEY: KEYEXAMPLE1234

functions:
  createUser:
    handler: handler.createUser
    environment:
      PASSWORD_ITERATIONS: 4096
PASSWORD_DERIVED_KEY_LENGTH: 256
```
### Pipeline Script
Initial container is the following:
```
node {
    cleanWs()
    String GIT_CREDENTIALS_ID = '$JenkinsUsername'
    String AWS_REGION = '$AWS_Region'
    String GIT_URL = '$BitBucketURL'
    String REPOS = '$nameOfTargetRepository'
    String PATH_TO_JS_FILES = './src/*'
    String LinterPath = './src/js/*.js' //if using a linter index.js or have Karma/Jasmine test cases
    String CODENAME = '$nameOfProject'
    String PROJECT_SOURCE_FOLDER_PATH = '.*/jenkins/workspace/*/src/'
    String NEXUS_URL = '$nexusURL'
    String NEXUS_NPM_TOKEN = '$tokenToNexus'
    String NEXUS_REPOSITORY = '$nameofNexusRepo'
    String NEXUS_GROUP = '$nameofNexusGroup'
    String SONAR_KEY = '$projectKey'
    def BUILD_NUMBER = env.BUILD_NUMBER //env. will be a parameter to input for stage functions
```
Now to declare a changable stage target variable
`def DEPLOY_TARGET = 'dev'`

Codegate Stage
```
 stage('$nameOfStage'){
        git credentialsId: GIT_CREDENTIALS_ID, url: GIT_URL //this grabs our Jenkins Credentials to log into bitbucket

        sh "npm install" //shell command to install our nodes into our jenkins instance
        //checks for eslint using .eslintrc and eslint.xml, parses code, and outputs new eslint.xml with caught issues
        sh "./node_modules/.bin/eslint -f checkstyle ${LinterPath} > eslint.xml || true"
        sh "ls"
        sh "cat eslint.xml"
        sh "file -bi eslint.xml"
        //check styles configuration to aggregate all results to one file and allows pipeline to fail if it does not pass
        recordIssues aggregatingResults: true, enabledForFailure: true, tools: [esLint(pattern: 'eslint.xml', reportEncoding: 'utf-8')]
```
Adding to the code gate will be a function from SonarQube plugin where the values are injected from the different variables declared above and runs the sonarscanner within the instance
```
 withSonarQubeEnv("$nameOfStage") {
            sh "/opt/sonar-scanner/bin/sonar-scanner -Dsonar.projectKey=${SONAR_KEY} -Dsonar.sources=${PROJECT_SOURCE_FOLDER_PATH}"
        }
```
A build is packaged into a .zip and then uploaded to Nexus
```
  sh "zip -r ${REPOS}_package-${BUILD_NUMBER}.zip ${PROJECT_SOURCE_FOLDER_PATH}"
        sh "echo \"//${NEXUS_URL}/repository/${NEXUS_REPOSITORY}/:_authToken=NpmToken.${NEXUS_NPM_TOKEN}\" > ~/.npmrc"
        nexusArtifactUploader artifacts: [[artifactId: "${REPOS}_package", classifier: '', file: "${REPOS}_package-${BUILD_NUMBER}.zip", type: 'zip']], credentialsId: 'JenkinsNexus', groupId: "${NEXUS_GROUP}", nexusUrl: "${NEXUS_URL}", nexusVersion: 'nexus3', protocol: 'https', repository: "${NEXUS_REPOSITORY}", version: "${BUILD_NUMBER}" 
    }
```
Next stage is called that asks if code is allows to deploy to the different environments. This can be reused for all necessary stages, by changing DEPLOY_TARGET = '$nameOfStage'
```
    stage ("Deploy to ${DEPLOY_TARGET}"){
        deployToEnv("${REPOS}", "${BUILD_NUMBER}", "${DEPLOY_TARGET}", "${AWS_REGION}")
        input (message: "Proceed to Test?", ok: "Yes")
        DEPLOY_TARGET = 'test'
    }
```
Finally build is deployed to S3 bucket and Lambda repository through a call to send payload. If the site has no certificate use `--no-check-certificate` to allow jenkins to access url
```
def void deployToEnv(REPOS, buildNum, deployTarget, awsRegion) {
        sh "wget --no-check-certificate --user=$nexusUsername --password=$nexusPassword '$AWS_URL'"
    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'JenkinsAWS2', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
        sh "export AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}"
        sh "export AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}"
        sh "export AWS_DEFAULT_REGION=${awsRegion}"
        sh "serverless deploy --stage ${deployTarget}"
    }
}
```
Node is completed

Pipeline is then displayed for success or failure with a console log. This will trigger everytime code is committed through bitbucket.

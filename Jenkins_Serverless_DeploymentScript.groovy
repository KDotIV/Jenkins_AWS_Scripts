import groovy.io.FileType

node {
    cleanWs()
    String GIT_CREDENTIALS_ID = 'gitCreds'
    String AWS_REGION = 'us-east-1'
    String GIT_URL = 'https://myRepo.com'
    String REPOS = 'RepoName'
    String PATH_TO_JS_FILES = './src/*'
    String LinterPath = './src/js/*.js'
    String CODENAME = 'serverless-pipeline'
    String PROJECT_SOURCE_FOLDER_PATH = '/var/lib/jenkins/workspace/Serverless-Backend/src/'
    String NEXUS_URL = 'myNexusRepoURL'
    String NEXUS_NPM_TOKEN = 'myNPMtoken'
    String NEXUS_REPOSITORY = 'nexusREPO'
    String NEXUS_GROUP = 'nexusGroup'
    String SONAR_KEY = 'myPrivateKey'
    def BUILD_NUMBER = env.BUILD_NUMBER

    def DEPLOY_TARGET = 'dev' // 'dev' or 'test', or 'stage'

    stage('Checkout and Code Gate'){
        git credentialsId: GIT_CREDENTIALS_ID, url: GIT_URL

        sh "npm install"
        
        sh "./node_modules/.bin/eslint -f checkstyle ${LinterPath} > eslint.xml || true"
        sh "ls"
        sh "cat eslint.xml"
        sh "file -bi eslint.xml"
        recordIssues aggregatingResults: true, enabledForFailure: true, tools: [esLint(pattern: 'eslint.xml', reportEncoding: 'utf-8')]
        
        withSonarQubeEnv("BackendPK") {
            sh "/opt/sonar-scanner/bin/sonar-scanner -Dsonar.projectName=${Sonar_Name} -Dsonar.projectKey=${SONAR_KEY} -Dsonar.sources=${PROJECT_SOURCE_FOLDER_PATH} -Dsonar.projectBaseDir=${PROJECT_SOURCE_FOLDER_PATH}"
        }
        sh "zip -r ${REPOS}_package-${BUILD_NUMBER}.zip ${PROJECT_SOURCE_FOLDER_PATH}"
        sh "echo \"//${NEXUS_URL}/repository/${NEXUS_REPOSITORY}/:_authToken=NpmToken.${NEXUS_NPM_TOKEN}\" > ~/.npmrc"
        nexusArtifactUploader artifacts: [[artifactId: "${REPOS}_package", classifier: '', file: "${REPOS}_package-${BUILD_NUMBER}.zip", type: 'zip']], credentialsId: 'JenkinsNexus', groupId: "${NEXUS_GROUP}", nexusUrl: "${NEXUS_URL}", nexusVersion: 'nexus3', protocol: 'https', repository: "${NEXUS_REPOSITORY}", version: "${BUILD_NUMBER}" 
    }
          
    stage ("Deploy to ${DEPLOY_TARGET}"){
        deployToEnv("${REPOS}", "${BUILD_NUMBER}", "${DEPLOY_TARGET}", "${AWS_REGION}")
        input (message: "Proceed to Test?", ok: "Yes")
        DEPLOY_TARGET = 'test'
    }

    stage ("Deploy to ${DEPLOY_TARGET}"){
        //To Test
        deployToEnv("${REPOS}", "${BUILD_NUMBER}", "${DEPLOY_TARGET}", "${AWS_REGION}")
        input (message: "Proceed to Stage?", ok: "Yes")
        DEPLOY_TARGET = 'stage'
    }

    stage ("Deploy to ${DEPLOY_TARGET}"){
        //To Stage
        deployToEnv("${REPOS}", "${BUILD_NUMBER}", "${DEPLOY_TARGET}", "${AWS_REGION}")
    }
}

def void deployToEnv(REPOS, buildNum, deployTarget, awsRegion) {
        sh "wget --no-check-certificate --user=$<myUsername> --password=$<myPassword> 'https://myEC2Instance.com"
    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'JenkinsAWS2', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
        sh "export AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}"
        sh "export AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}"
        sh "export AWS_DEFAULT_REGION=${awsRegion}"
        sh "serverless deploy --stage ${deployTarget}"
    }
}
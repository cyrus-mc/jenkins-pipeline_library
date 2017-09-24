def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  properties(
    [[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', daysToKeepStr: '14']]]
  )

  name = "${env.JOB_NAME}"
  ID = name.tokenize('/')[1]
  def podName = "${ID}-${env.BRANCH_NAME}-${env.BUILD_NUMBER}"

  currentBuild.result = "SUCCESS"

  version = "1.0.${env.BUILD_NUMBER}"
  currentBuild.displayName = "${version}"

  node('windows') {

    try {

      stage('Checkout') {
        //Clean up existing code on the jenkins slave
        step([$class: 'WsCleanup'])

        checkout scm
      }

      stage('Build') {
        bat "nuget restore src"
        bat 'msbuild /p:Configuration=Release Src'
      }

      stage('SonarQube') {
        withSonarQubeEnv('SonarQube') {
          bat "sonar-scanner -D sonar.branch=${BRANCH_NAME} -D sonar.projectVersion=1"
        }
      }

      stage("Quality Gate"){
        timeout(time: 10, unit: 'MINUTES') {
          def qg = waitForQualityGate()
          if (qg.status != 'OK') {
            currentBuild.result = 'UNSTABLE'
          } else {
            bat 'echo Quality Gate passed'
          }
        }
      }

      if ("${BRANCH_NAME}" == 'master') {
        stage('publish'){
          println config.projectName
          bat "nuget pack src/src/${config.projectName}/${config.projectName}.csproj -Properties Configuration=Release -version ${version}"
          bat 'nuget push *.nupkg -source http://nuget.services.aws -ApiKey yIi7HkVGVHvzpfqLGsWJrFZjt61VZmjx'
        }
      }

      smarshBuildEmails{}

    } catch(err) {
      currentBuild.result = 'FAILURE'
      smarshBuildEmails{}
      if (config.channel_url) {
              println 'office365'
              office365ConnectorSend message:"BUILD status: ${currentBuild.result}",status: currentBuild.result, webhookUrl: "${config.channel_url}"
      } else {
              println 'no teams channel configured'
      }

      throw err
    }

    if (config.channel_url) {
              println 'office365'
              office365ConnectorSend message:"BUILD status: ${currentBuild.result}",status: currentBuild.result, webhookUrl: "${config.channel_url}"
      } else {
              println 'no teams channel configured'
      }
  }
}

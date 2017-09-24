def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  properties([
    buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '14', numToKeepStr: '')),
    disableConcurrentBuilds()
  ])

  name = "${env.JOB_NAME}"
  ID = name.tokenize('/')[1]
  def podName = "${ID}-${env.BRANCH_NAME}-${env.BUILD_NUMBER}"

  podTemplate(label: podName,
    containers: [
      containerTemplate(name: 'ant', image: 'frekele/ant', ttyEnabled: true, alwaysPullImage: true, command: 'cat')
    ]
  ){

    currentBuild.result = "SUCCESS"

    node(podName) {

      try {

        timeout(30) {

          container('ant') {

            stage('Checkout') {
              step([$class: 'WsCleanup'])
              git credentialsId: 'b0e6288f-5273-4d25-877a-7125647f6534', url: 'https://github.com/Smarsh/beacon-automation.git'
            }

            stage('Build') {
              sh 'ant -Dtestng.suite.file=ci_run_config.xml -Ddryrun.mode=false'
            }

            stage('Publish Test Results') {
              step([$class: 'Publisher', reportFilenamePattern: 'test-results/**/testng-results.xml'])
            }

            //**REMOVING THIS STEP AT THE REQUEST OF QA/IFS**
            //This stage publishes sourceFile folders/files to an s3 bucket called beacon-automation
            // stage('Archive to S3') {
            //   step([$class: 'S3BucketPublisher',
            //     consoleLogLevel: 'OFF',
            //     pluginFailureResultConstraint: 'FAILURE',
            //     entries: [[
            //       sourceFile: 'test-results/, dashboard/, dashboard.htm',
            //       bucket: 'beacon-automation',
            //       selectedRegion: 'us-west-2',
            //       noUploadOnFailure: false,
            //       managedArtifacts: true,
            //       flatten: false,
            //       showDirectlyInBrowser: true,
            //       keepForever: true
            //       ]],
            //     //This profile name is pulled from the Jenkins system configuration settings
            //     profileName: 'beacon-automation',
            //     dontWaitForConcurrentBuildCompletion: false,
            //     ])
            // }

          }

        }

        smarshBuildEmails{}

        if (config.channel_url) {
          println 'office365'
          office365ConnectorSend message:"BUILD status: ${currentBuild.result}",status: currentBuild.result, webhookUrl: "${config.channel_url}"
        } else {
          println 'no teams channel configured'
        }

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
    }
  }
}

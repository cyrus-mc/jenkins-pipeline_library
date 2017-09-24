def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  properties(
    [[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', daysToKeepStr: '14']]]
  )

  currentBuild.result = "SUCCESS"

  name = "${env.JOB_NAME}"
  ID = name.tokenize('/')[1]

  node('windows') {

    try {

      stage('Checkout') {
        //Clean up existing code on the jenkins slave
        step([$class: 'WsCleanup'])

        checkout scm

        versionFile = readFile('version').trim()
        versionSplit = versionFile.tokenize('.')
        major = versionSplit[0].toInteger()
        minor = versionSplit[1].toInteger()
        buildNumber = currentBuild.number
        paddedMinor = String.format("%02d", minor)
        paddedBuild = String.format("%04d", buildNumber)
        version = "${major}.${paddedMinor}.${paddedBuild}.0"

        currentBuild.displayName = version

        setVersion = "Get-Childitem -path Src/ -include AssemblyInfo.cs -recurse | ForEach-Object {(Get-Content  \$_).replace('1.0.0.0', '${version}') | Set-Content \$_ }"
        bat "powershell \"$setVersion\""

      }

      stage('Build') {
        bat 'msbuild /p:Configuration=Release Src'
      }

      stage('SonarQube') {
        withSonarQubeEnv('SonarQube') {
          bat "sonar-scanner -D sonar.branch=${BRANCH_NAME} -D sonar.projectVersion=${version}"
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

      stage('Create Release'){
        bat "powershell ./release.ps1 -version ${version}"
        archiveArtifacts artifacts: '*.zip'
      }

      if ("${BRANCH_NAME}" == 'master') {
        stage('Publish to S3') {
          step([$class: "S3BucketPublisher",
            consoleLogLevel: 'OFF',
            pluginFailureResultConstraint: 'FAILURE',
            entries: [[
              sourceFile: "*.zip",
              bucket: "smc-builds/${ID}",
              selectedRegion: "us-west-2",
              noUploadOnFailure: true,
              managedArtifacts: false,
              flatten: true,
              keepForever: true
              ]],
            //This profile name is pulled from the Jenkins system configuration settings
            profileName: "beacon-automation",
            dontWaitForConcurrentBuildCompletion: false,
            ])
        }
      }

      smarshBuildEmails{}

    } catch(err) {
      currentBuild.result = 'FAILURE'
      smarshBuildEmails{}
      throw err
    }
  }
}

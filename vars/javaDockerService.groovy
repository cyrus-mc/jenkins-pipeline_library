def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  properties(
    [[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', daysToKeepStr: '14']]]
  )

  def useTemplate = true
  if (config.containsKey("useTemplate")) useTemplate = config.useTemplate

  println "Config: ${config}"
  println "Use Template: ${useTemplate} / ${config.useTemplate}"

  name = "${env.JOB_NAME}"
  ID = name.tokenize('/')[1]
  def podName = "${ID}-${env.BRANCH_NAME}-${env.BUILD_NUMBER}"

  if (config.pom) {
    buildContainer = 'quay.io/smarsh/docker-build-maven:apt'
  } else {
    buildContainer = 'quay.io/smarsh/docker-build-maven'
  }

  podTemplate(label: podName,
    containers: [
      containerTemplate(name: 'maven', image: buildContainer, ttyEnabled: true, alwaysPullImage: true, command: 'cat'),
      containerTemplate(name: 'docker', image: 'docker', command: 'cat', ttyEnabled: true, alwaysPullImage: true)
    ],
    volumes: [
      hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')
    ]
  ){

    currentBuild.result = "SUCCESS"

    if (config.pom) {
      pomFile = config.pom
    } else {
      pomFile = 'pom.xml'
    }

    node(podName) {

      try {

        container('maven') {

          stage('Checkout') {
            checkout scm
            def pom = readMavenPom file: "${pomFile}"
            pomPackaging = pom.packaging
            pomArtifactId = pom.artifactId
            pomVersionSplit = pom.version.tokenize('.')
            int versionMajorInt = pomVersionSplit[0].toInteger()
            int versionMinorInt = pomVersionSplit[1].toInteger()
            int buildNumberInt = currentBuild.number
            versionMinor = String.format("%02d", versionMinorInt)
            buildNumber = String.format("%04d", buildNumberInt)
            version = "${versionMajorInt}.${versionMinor}.${buildNumber}"

            pom.version = version
            writeMavenPom model: pom
            currentBuild.displayName = "${version}"
            sh "echo ${version} > version"

            sh 'git rev-parse --short HEAD > commit'
            def commit = readFile('commit').trim()

            jobNameSplit = env.JOB_NAME.split('/')
            jobName = jobNameSplit[1].toLowerCase()

            stash name: 'version', includes: 'version'
            stash name: 'commit', includes: 'commit'
          }

          stage('Build') {
            if ("${pomPackaging}" == 'dotnet') {
              sh 'mkdir -p /home/jenkins/.nuget/NuGet/'
              sh 'cp /root/.nuget/NuGet/NuGet.Config /home/jenkins/.nuget/NuGet/NuGet.Config'
            }
            sh "mvn package -f ${pomFile}"
          }

          stage('UNIT Tests') {
            sh "mvn verify -f ${pomFile}"
          }

          stage('SonarQube') {
            withSonarQubeEnv('SonarQube') {
              sh 'echo SONAR-SCANNER'
              sh "sonar-scanner -D sonar.branch=${BRANCH_NAME} -D sonar.projectVersion=${version}"
            }
          }

          stage("Quality Gate"){
            timeout(time: 10, unit: 'MINUTES') {
              def qg = waitForQualityGate()
              if (qg.status != 'OK') {
                currentBuild.result = 'UNSTABLE'
              } else {
                sh 'echo Quality Gate passed'
              }
            }
          }

          stage('Maven Results') {
            if ("${pomPackaging}" == 'dotnet') {
              dir("src/${pomArtifactId}/application") {
                stash name: 'app', includes: '*'
              }
            } else {
              stash name: 'app', includes: 'target/*-executable.jar'
            }
          }

        }

        if ("${pomPackaging}" == 'dotnet') {
          dockerContainer = 'maven'
        } else {
          dockerContainer = 'docker'
        }

        container(dockerContainer){

          dir('application') {
             unstash 'version'
             unstash 'app'
          }

          dir('config') {
          }

          version = readFile('application/version').trim()
          jobNameSplit = env.JOB_NAME.split('/')
          jobName = jobNameSplit[1].toLowerCase()

          stage('Docker Build') {
            if ("${pomPackaging}" == 'dotnet') {
              sh "mvn docker:build -f ${pomFile}"
            } else if(useTemplate == false) {
              sh 'docker login --username=s-automation --password=PASSWORD quay.io'
              sh "docker build -t ${jobName} ."
            } else {
              git credentialsId: 'b0e6288f-5273-4d25-877a-7125647f6534', url: 'https://github.com/Smarsh/docker-template.git'
              sh "docker build -t ${jobName} ."
            }
          }

          stage ('Docker upload') {
            if ("${BRANCH_NAME}" == 'master') {
              tag = "quay.io/smarsh/${jobName}:${version}"
            } else {
              tag = "quay.io/smarsh/${jobName}:${BRANCH_NAME}-${version}"
            }

            sh 'docker login --username=s-automation --password=PASSWORD quay.io'
            sh "docker tag ${jobName} ${tag}"
            sh "docker push ${tag}"
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

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

  if (config.pom) {
    buildContainer = 'quay.io/smarsh/docker-build-maven:apt'
    projectType = "dotnet"
  } else {
    buildContainer = 'quay.io/smarsh/docker-build-maven'
    projectType = "java"
  }

  podTemplate(label: podName,
    containers: [
      containerTemplate(name: 'maven', image: buildContainer, ttyEnabled: true, alwaysPullImage: true, command: 'cat')
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
          }

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

          sh "echo ${versionMajorInt} > major"
          sh "echo ${versionMinor} > minor"

          pom.version = version
          writeMavenPom model: pom
          currentBuild.displayName = "${version}"

          stage('Build') {
            if ("${pomPackaging}" == 'dotnet') {
              sh 'mkdir -p /home/jenkins/.nuget/NuGet/'
              sh 'cp /root/.nuget/NuGet/NuGet.Config /home/jenkins/.nuget/NuGet/NuGet.Config'
            }
            sh "mvn clean package -f ${pomFile}"
          }

          stage('UNIT Tests') {
            sh "mvn test -f ${pomFile}"
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


          stage('Deploy') {
            def major = readFile('major').trim()
            def minor = readFile('minor').trim()
            if ("${BRANCH_NAME}" == 'master') {
              if ("${pomPackaging}" == 'dotnet') {
                sh "dotnet pack /p:Configuration=Release /p:version=${version} src/${pomArtifactId}"
                sh "dotnet nuget push -s http://nuget.services.aws/ -k yIi7HkVGVHvzpfqLGsWJrFZjt61VZmjx src/${pomArtifactId}/bin/Release/${pomArtifactId}.${major}.${versionMinorInt}.${buildNumberInt}.nupkg"
              } else {
                sh  "mvn deploy -f ${pomFile}"
                sh  """ mvn deploy:deploy-file \
                  -DgroupId=${pom.groupId} \
                  -DartifactId=${pom.artifactId} \
                  -Dpackaging=${pom.packaging} \
                  -Dversion=${major}.${minor}-SNAPSHOT \
                  -DgeneratePom=true \
                  -DrepositoryId=archiva \
                  -Dfile=target/${pom.artifactId}-${versionMajorInt}.${versionMinor}.${buildNumber}.jar \
                  -Durl=http://beacon-archiva.default.svc.cluster.local/repository/snapshots
                """
              }
            } else {
              sh 'echo jar not deployed to archiva on branches other then master'
            }
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
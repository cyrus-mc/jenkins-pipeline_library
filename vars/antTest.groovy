def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  properties(
    [[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '30']]]
  )

  name = "${env.JOB_NAME}"
  ID = name.tokenize('/')[1]
  def podName = "${ID}-${env.BRANCH_NAME}-${env.BUILD_NUMBER}"

  podTemplate(label: podName,
    containers: [
      containerTemplate(name: 'ant', image: 'ant', ttyEnabled: true, alwaysPullImage: true, command: 'cat')
    ]
  ){

    currentBuild.result = "SUCCESS"

    node(podName) {

      try {

        container('ant') {

          stage('Checkout') {
            checkout scm
          }

          //def pom = readMavenPom file: 'pom.xml'
          //pomVersionSplit = pom.version.tokenize('.')
          //int versionMajorInt = pomVersionSplit[0].toInteger()
          //int versionMinorInt = pomVersionSplit[1].toInteger()
          //int buildNumberInt = currentBuild.number
          //versionMinor = String.format("%02d", versionMinorInt)
          //buildNumber = String.format("%04d", buildNumberInt)
          //version = "${versionMajorInt}.${versionMinor}.${buildNumber}"

          //sh "echo ${versionMajorInt} > major"
          //sh "echo ${versionMinor} > minor"

          //pom.version = version
          //writeMavenPom model: pom
          //currentBuild.displayName = "${version}"

          stage('Build') {
            sh 'ant build'
          }

        }

        //smarshBuildEmails{}

      }

      catch(err) {
        currentBuild.result = 'FAILURE'
        //smarshBuildEmails{}
        throw err
      }
    }
  }
}

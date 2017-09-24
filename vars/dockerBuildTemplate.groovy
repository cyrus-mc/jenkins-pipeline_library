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
      containerTemplate(name: 'docker', image: 'docker', command: 'cat', ttyEnabled: true, alwaysPullImage: true)
    ],
    volumes: [
      hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')
    ]
  ){

    currentBuild.result = "SUCCESS"

    node(podName) {

      try {

        //Because no container is declared here, this groovy file will use a hidden
        //jenkins slave image with the necessary software installed (i.e., git)

          stage('Checkout Github Repo') {

            checkout scm

            sh 'git rev-parse --short HEAD > commit'
            def commit = readFile('commit').trim()
            version = commit
            sh "echo ${version} > version"
            currentBuild.displayName = "${version}"

            stash name: 'version', includes: 'version'
          }

        container('docker'){

          unstash 'version'

          version = readFile('version').trim()
          jobNameSplit = env.JOB_NAME.split('/')
          jobName = jobNameSplit[1].toLowerCase()
          println "MESSAGE:  This is the jobName: ${jobName}"

          stage('Build Docker Container') {

            sh 'docker login --username=s-automation --password=PASSWORD quay.io'

            //This block of code looks for a Dockerfile in the root directory of the repo,
            //and if no Dockerfile is present, it uses a shared Dockerfile in the
            //docker folder.  This shared Dockerfile may be used across multiple Smarsh
            //projects.

            if (config.Dockerfile) {
              sh "docker build -t ${jobName} ."
            } else {
              sh "docker build -t ${jobName} -f ./docker/Dockerfile ."
            }

          }

          stage ('Upload Docker Image to Quay') {
            if ("${BRANCH_NAME}" == 'master') {
              tag = "quay.io/smarsh/${jobName}:${version}"
            } else {
              tag = "quay.io/smarsh/${jobName}:${BRANCH_NAME}-${version}"
            }

            println "MESSAGE_2:  This is the jobName: ${jobName}"

            sh "docker tag ${jobName} ${tag}"
            sh "docker push ${tag}"
          }

          if ("{BRANCH_NAME}" == 'master' ) {
            stage ('Master - Provision into QA') {
              sh "echo 'This should only show on a master branch'"
            }
          }   

        }

        // smarshBuildEmails{}

      }

      catch(err) {
        currentBuild.result = 'FAILURE'
        // smarshBuildEmails{}

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
}

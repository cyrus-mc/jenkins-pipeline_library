def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  properties (
    [[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '30']]]
  )

  println "This is the value of config: ${config}"      

  name = "${env.JOB_NAME}"
  ID = name.tokenize('/')[1]
  def podName = "${ID}-${env.BRANCH_NAME}-${env.BUILD_NUMBER}"
  def nameSpace = podName.toLowerCase()

  podTemplate(label: podName,
    containers: [
      containerTemplate(name: 'maven', image: 'quay.io/smarsh/docker-build-maven:apt', ttyEnabled: true, alwaysPullImage: true, command: 'cat'),
      containerTemplate(name: 'kubectl', image: 'quay.io/smarsh/jnlp-slave', ttyEnabled: true, alwaysPullImage: true, command: 'cat'),
    ],
    volumes: [
      hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')
    ]
  ){

    currentBuild.result = "SUCCESS"

    node(podName) {

      try {

        container('maven') {

          stage('Cleanup Old Images') {
            sh "docker images ${config.imageName}"
            sh "docker images -q ${config.imageName} | uniq | xargs --no-run-if-empty docker rmi -f"
            sh "docker images -q localhost:5000/${config.imageName} | uniq | xargs --no-run-if-empty docker rmi -f"
          }

          stage('Checkout') {
            checkout scm
          }

          stage('Build') {
            sh "mvn versions:set -DnewVersion=${ID}-SNAPSHOT"               
            sh "mvn clean install fabric8:build fabric8:push -Dpostgres.server=${nameSpace}-postgresql-master -Ddocker.registry=localhost:5000 -DcommitHash=${env.BUILD_NUMBER}"                        
          }
        }

        try {

          container('kubectl') {

            stage('Create Kubernetes Namespace') {
              sh "kubectl create namespace ${nameSpace}"
            }

            stage('Configure Helm') {
              sh "helm init"
              sh "helm repo add smarsh-charts https://smarsh.github.io/charts"
            }

            stage('Install PostgreSQL Chart') {
              sh "helm --name ${nameSpace} --set Namespace=${nameSpace} --set Database.Name=${config.databaseName} install smarsh-charts/postgresql"
              waitUntil {
                  try {
                    sh "kubectl --namespace=${nameSpace} get pods | grep ${nameSpace}-postgresql-0 | grep -v Running"
                    echo "Waiting for master to become ready"
                  } catch (err) {
                    // even though POD is running, it needs to run an init script, sleep for 30 seconds
                    sh "/bin/sleep 30"
                    return true
                  }
                  return false
              }
            }
          }

          container('maven') {

            stage('Flyway') {              
              sh "mvn clean install fabric8:resource-apply -Dfabric8.postgresServer=${nameSpace}-postgresql-master -Dfabric8.namespace=${nameSpace} -Ddocker.image=localhost:5000/${config.imageName}:${env.BUILD_NUMBER} -Ddocker.registry=localhost:5000 -DcommitHash=${env.BUILD_NUMBER}"

              try {

                
                timeout(1) {
                  waitUntil {
                      try {
                        sh "kubectl --namespace=$nameSpace get pods -a | grep '${config.jobName}.*Completed'"                      
                        return true
                      } catch (err) {
                        // even though POD is running, it needs to run an init script, sleep for 30 seconds
                        sh "/bin/sleep 30"                        
                      }
                      return false
                  }
                }
              }
              catch(err) {
                  def job_result = sh(returnStdout: true, script: "kubectl --namespace=${nameSpace} logs `kubectl --namespace=${nameSpace} get pods -a | grep '${config.jobName}.*Error' | awk {'print \$1;exit;}'`").trim()
                  
                  println job_result

                  error(job_result)
              }          

              sh "kubectl --namespace=${nameSpace} logs `kubectl --namespace=${nameSpace} get pods -a | grep '${config.jobName}.*Completed' | awk {'print \$1;exit;}'`"              
            }
         }

        } catch (err) {
            currentBuild.result = "FAILURE"
            throw err
        } finally {
          container('kubectl') {

            stage('Publish to S3') {
              step([$class: "S3BucketPublisher",
                consoleLogLevel: 'OFF',
                pluginFailureResultConstraint: 'FAILURE',
                entries: [[
                  sourceFile: "",
                  bucket: "beacon-jenkins/builds/${ID}-${env.BRANCH_NAME}-${env.BUILD_NUMBER}",
                  selectedRegion: "us-west-2",
                  noUploadOnFailure: false,
                  managedArtifacts: true,
                  flatten: false,
                  showDirectlyInBrowser: true,
                  keepForever: true
                  ]],
                //This profile name is pulled from the Jenkins system configuration settings
                profileName: "beacon-automation",
                dontWaitForConcurrentBuildCompletion: false,
                ])
            }

            //testing
            stage('Cleanup') {
              sh "helm delete --purge ${nameSpace}"
              sh "kubectl delete namespace ${nameSpace}"
            }
          }
        }
      } catch(err) {
        currentBuild.result = 'FAILURE'
        smarshBuildEmails{}

        if (config.channel_url) {
          office365ConnectorSend message: "BUILD status: ${currentBuild.result}", status: currentBuild.result, webhookUrl: "${config.channel_url}"
        }

        throw err
      }

      if (config.channel_url) {
        office365ConnectorSend message: "BUILD status: ${currentBuild.result}", status: currentBuild.result, webhookUrl: "${config.channel_url}"
      }

    }
  }
}

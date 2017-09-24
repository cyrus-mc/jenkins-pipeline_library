def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    properties(
    [[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '30']]]
    )

    currentBuild.result = "SUCCESS"
    
    node('qa') {

      try {

          stage('Do an EFK exists pod check'){

            //Clean up existing code on the jenkins slave
            step([$class: 'WsCleanup'])
            checkout scm

            //Set the version/build number
            sh 'git rev-parse --short HEAD > commit'
            def commit = readFile('commit').trim()
            version = commit
            sh "echo ${version} > version"
            currentBuild.displayName = "${version}"

            //This statement calls serveral functions in a bash script to check if EFK is already installed on the cluster 
            sh 'source ${version}efk-pod-check.sh; decisionfileCheck && podlistCheck && kubectlListQuery && stringnameSearch'

            //Stash the version info for use in the next stage
            stash name: 'version', includes: 'version'
  
          }
     
          stage('Deploy EFK to the cluster') {

            unstash 'version'

            //Read a code from a file to determine whether or not to install EFK on the cluster
            def number = readFile('decision_to_install.txt').trim()

            //Prints the value of the code to the console
            println ("This is the value of number: " + "${number}")

            //Installs EFK on the cluster
            if (number == '1') {

              sh "kubectl create --namespace=kube-system -f ${version}/elasticsearch-controller.yaml"
              sh "kubectl create --namespace=kube-system -f ${version}/elasticsearch-service.yaml"
              sh "kubectl create --namespace=kube-system -f ${version}/fluentd-elasticsearch-daemonset.yaml"
              sh "kubectl create --namespace=kube-system -f ${version}/kibana-controller.yaml"
              sh "kubectl create --namespace=kube-system -f ${version}/kibana-service.yaml"

            } else {
                stage 'EFK is already deployed'
            }

          }

          // smarshBuildEmails{}
      }
      
      catch(err) {
        currentBuild.result = 'FAILURE'
        smarshBuildEmails{}
        throw err
      }
  }
}

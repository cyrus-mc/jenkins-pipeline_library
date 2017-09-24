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

      def properties = readProperties file:'build/yaml.properties'
      def name = properties['name']
      def namespace = properties['namespace']
      def yamlfilename = properties['yamlfilename']

      println "This is the value of name: ${name}"
      println "This is the value of namespace: ${namespace}"
      println "This is the value of yamlfilename: ${yamlfilename}"

      stage('Checkout') {

        //Clean up existing code on the jenkins slave
        step([$class: 'WsCleanup'])
        checkout scm

      }

      stage('Deploy') {

        //Check to see if already deployed to the pod
        sh 'source build/podinstallcheck.sh; stringNameSearch'
        def decision = readFile('decision.txt').trim()

        //if not deployed to the pod, deploy it
        if (decision == '1') {
          //Install on the cluster
          sh "kubectl create -f ${yamlfilename}"
        } else {
          stage 'Already deployed'       
        }
      
      }

      stage('Clean Up') {

        sh 'source build/podinstallcheck.sh; cleanUp'
      }

    }
     //smarshBuildEmails{}
     
  catch(err) {
    currentBuild.result = 'FAILURE'
      smarshBuildEmails{}
    throw err
    }
  }
}
def call(body) {

  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config

  body()

  properties ([
    [ $class: 'BuildDiscarderProperty', strategy: [ $class: 'LogRotator', daysToKeepStr: '7', numToKeep: '10' ] ]
  ])

  withMaven(name: 'maven-java') {
    inside(jnlpImage: 'quay.io/smarsh/jnlp-slave:3.10-1-alpine') {

      currentBuild.result = "SUCCESS"

      def pomFile
      if (config.pom) {
        pomFile = config.pom
      } else {
        pomFile = 'pom.xml'
      }

      def pom
      stage('Checkout Code') {
        checkout scm
      
        /* read POM */
        pom = readMavenPom file: "pom.xml"
        pomVersionSplit = pom.version.tokenize('.')
      
        /* generate version based on master vs branch */
        if (env.BRANCH_NAME == "master") {
          version = String.format("%d.%d.%d", pomVersionSplit[0].toInteger(), pomVersionSplit[1].toInteger(), currentBuild.number)
        } else {
          branchNameSplit = env.BRANCH_NAME.tokenize('-')
          version = String.format("%d.%d.%s-SNAPSHOT", pomVersionSplit[0].toInteger(), pomVersionSplit[1].toInteger(), branchNameSplit[1])
          //pom.artifactId = String.format("%s-%s", pom.artifactId, env.BRANCH_NAME)
        }
        sh "echo ${version} > version"
        pom.version = version
        writeMavenPom model: pom
        currentBuild.displayName = "${version}"
      
        stash name: 'version', includes: 'version'
      
      }

      /*
       * Run the following stages inside the maven container
       */
      customContainer('maven') {

        stage('Build') {
          ksh "mvn clean package -f ${pomFile}"
        }
      
        stage('Unit Tests') {
          ksh "echo mvn test -f ${pomFile}"
        }

        stage('Sonar Scan') {
          unstash 'version'
          withSonarQubeEnv('SonarQube') {
            ksh "mvn sonar:sonar -Dsonar.branch=${env.BRANCH_NAME}"
          }

          timeout(time: 10, unit: 'MINUTES') {
            def qg = waitForQualityGate()
            if (qg.status != 'OK') {
              currentBuild.result = 'UNSTABLE'
            }
          }
        }

        /*
         * Based on branch upload artifact to correct repository
         */ 
        stage('Upload Artifact') {
        
          /* retrieve the stashed version */
          unstash 'version'
      
          def repoURL = "http://beacon-archiva.default.svc.cluster.local/repository/snapshots"
          if (env.BRANCH_NAME == "master") {
            repoURL = "http://beacon-archiva.default.svc.cluster.local/repository/internal"
          }
        
          ksh(""" mvn deploy:deploy-file \
              -DgroupId=${pom.groupId} \
              -DartifactId=${pom.artifactId} \
              -Dpackaging=${pom.packaging} \
              -Dversion=${version} \
              -DgeneratePom=true \
              -DrepositoryId=archiva \
              -Dfile=target/${pom.artifactId}-${version}.jar \
              -Durl=${repoURL}
              """)
        }

      }
    }
  }
}

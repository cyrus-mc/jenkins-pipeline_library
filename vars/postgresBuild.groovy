def call(body) {

  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config

  body()

  /*  pipeline requires helm */
  withMaven {
    withHelm {
      /* modify the JNLP image to our custom one (which includes kubectl) */
      inside(jnlpImage: 'quay.io/smarsh/jnlp-slave:3.10-1-alpine') {

        properties ([
          [ $class: 'BuildDiscarderProperty', strategy: [ $class: 'LogRotator', daysToKeepStr: '7', numToKeep: '10' ] ],
          parameters([ string(description: 'Deploy to Cluster', defaultValue: 'dev', name: 'cluster'),
                       string(description: 'Deploy to Namespace', defaultValue: 'default', name: 'namespace'),
                       booleanParam(description: 'Invoke Deploy', defaultValue: false, name: 'deploy') ])
        ])

        /** set status to SUCCESS */
        currentBuild.result = 'SUCCESS'

        stage('Checkout Code') {

          checkout scm

          sh 'git rev-parse --short HEAD > commit'
          def commit = readFile('commit').trim()
          stash name: 'commit', includes: 'commit'

        }

        def buildId = buildId().replaceAll('_', '-').toLowerCase()
        try {

          /* this can run in our base jnlp because it contains kubectl */
          stage('Create Environment') {

            sh "kubectl create namespace ${buildId}"

          }

           customContainer('helm') {

            stage('Install PostgresSQL Chart') {
                
              dir('postgresql') {
                git branch: 'master', credentialsId: 'b0e6288f-5273-4d25-877a-7125647f6534', url: 'https://github.com/Smarsh/helm-postgres.git'
              }

              /* specify the name and values file corresponds to this db */
              ksh("cd postgresql && helm install --name ${buildId} -f ${config.name}.yaml --set Namespace=${buildId} .")

              /* wait for the database to fully initialize  */
              waitUntil {
                try {
                  /* if this returns 0 we are good and DB is up */
                  sh "kubectl --namespace=${buildId} exec ${config.name}db-0 /opt/cpm/bin/readiness.sh"
                  return true
                } catch (err) {
                  /* sleep for a few seconds */
                  sh "/bin/sleep 5"
                  return false
                }
              }
            }

          }

          customContainer('maven') {

            stage('Run Full Update') {
              ksh("mvn -Ddb.hostname=${config.name}db-master.${buildId} -Ddb.name=${config.name} -Ddb.port=5432 flyway:migrate")
            }
          }


          customContainer('helm') {
          
            stage('Re-install PostgresSQL Chart') {
                
              /* uninstall current release */
               ksh("helm delete --purge ${buildId}")

              /* specify the name and values file corresponds to this db */
              ksh("cd postgresql && helm install --name ${buildId} -f ${config.name}.yaml --set Namespace=${buildId} .")

              /* wait for the database to fully initialize  */
              waitUntil {
                try {
                  /* if this returns 0 we are good and DB is up */
                  sh "kubectl --namespace=${buildId} exec ${config.name}db-0 /opt/cpm/bin/readiness.sh"
                  return true
                } catch (err) {
                  /* sleep for a few seconds */
                  sh "/bin/sleep 5"
                  return false
                }
              }
            }

          }

          customContainer('maven') {

            stage('Run Migrate Update') {
            
              /* jenkins is overly complicated */
              sh "cat .git/config | grep url | awk -F= '{print \$2}' > GIT_URL"
              def gitURL = readFile('GIT_URL').trim()
              
              /* master should be the last known good release */
              dir('master') {
                
                /* jenkins is overly complicated */
                git branch: 'master', credentialsId: 'b0e6288f-5273-4d25-877a-7125647f6534', url: "${gitURL}"
              }
              ksh("cd master && mvn -Ddb.hostname=${config.name}db-master.${buildId} -Ddb.name=${config.name} -Ddb.port=5432 flyway:migrate")
              
              /* switch back to branch and run migrate again */
              ksh("mvn -Ddb.hostname=${config.name}db-master.${buildId} -Ddb.name=${config.name} -Ddb.port=5432 flyway:migrate")
            }
          }

        } finally {

          customContainer('helm') {

            /* delete the actual chart */
            stage('Uninstall PostgresSQL Chart') {
              ksh("helm delete --purge ${buildId}")
            }

          }

          /* delete namespace */
          stage('Clean-Up') {
            sh "kubectl delete namespace ${buildId}"
          }

        }

        customContainer('maven') {

          stage('Package Artifact') {
            def pom = readMavenPom file: 'pom.xml'

            int buildNumberInt = currentBuild.number
            buildNumber = String.format("%d", buildNumberInt)

            /* only push artifact to archiva on master */
            if (env.BRANCH_NAME == "master") {
              ksh("mvn package")
              ksh( """ mvn deploy:deploy-file \
                   -DgroupId=${pom.groupId} \
                   -DartifactId=${pom.artifactId} \
                   -Dpackaging=${pom.packaging} \
                   -Dversion=1.0.${buildNumber} \
                   -DgeneratePom=true \
                   -DrepositoryId=archiva \
                   -Dfile=target/admindb-1-migrations.jar \
                   -Durl=http://beacon-archiva.default.svc.cluster.local/repository/internal
               """ )
            } else {
              println "Only push artifacts on master branch"
            }
          }

        }

        if (env.BRANCH_NAME == "master") {
          node("${params.cluster}") {
            stage('Deploy') {
              /* only do the deploy required */
              if (params.deploy == true) {
                step( [$class: 'WsCleanup'] )

                checkout scm

                /* call flyway:migrate against the master service in the specified namespace */
                sh "mvn -Ddb.hostname=${config.name}db-master.${params.namespace} -Ddb.name=${config.name} -Ddb.port=5432 flyway:migrate"
              }
            }
          }
        } else {
          stage('Deploy') {
            /* stub to keep pipeline consistent with deploy = false */
          }
        }

      }
    }
  }

}

/*
 * Wrapper around sh to invoke all commands via kubectl
*/
def ksh(command) {
  if (env.CONTAINER_NAME) {
    if ((command instanceof String) || (command instanceof GString)) {
      command = kubectl(command)
    }

    if (command instanceof LinkedHashMap) {
      command["script"] = kubectl(command["script"])
    }
  }

  sh(command)
}

def kubectl(command) {
  "kubectl exec -i ${env.HOSTNAME} -c ${env.CONTAINER_NAME} -- /bin/sh -c 'cd ${env.WORKSPACE} && ${command}'"
}

/*
 * Set environment variable CONTAINER_NAME in the current context
*/
def customContainer(String name, Closure body) {
  withEnv(["CONTAINER_NAME=$name"]) {
    body()
  }
}


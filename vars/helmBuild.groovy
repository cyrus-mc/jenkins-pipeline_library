def call(body) {

  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  /*  pipeline requires helm */
  withMaven {
    withHelm(helmImage: 'quay.io/smarsh/helm:2.4.2') {
      /* modify the JNLP image to our custom one (which includes kubectl) */
      inside(jnlpImage: 'quay.io/smarsh/jnlp-slave:3.10-1-alpine') {

        properties ([
          [ $class: 'BuildDiscarderProperty', strategy: [ $class: 'LogRotator', daysToKeepStr: '7', numToKeep: '10' ] ],
          /*parameters([ string(description: 'Chart Name', name: 'chartName') ]) */
        ])

        /** set status to SUCCESS */
        currentBuild.result = 'SUCCESS'

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
            version = String.format("%d.%d-SNAPSHOT", pomVersionSplit[0].toInteger(), pomVersionSplit[1].toInteger())
          }
          sh "echo ${version} > version"
          pom.version = version
          writeMavenPom model: pom

          /* update Chart.yaml */
          sh "sed 's/\${CHART_VER}/${version}/; s/\${CHART_REL}/0/' Chart.yaml.in > ${config.chartName}/Chart.yaml"

          sh 'git rev-parse --short HEAD > commit'
          def commit = readFile('commit').trim()

          stash name: 'commit', includes: 'commit'
          stash name: 'version', includes: 'version'

          // checkout shared library so we can "laod" it and use replay to modify
          dir('library') {
            git branch: 'master', credentialsId: 'b0e6288f-5273-4d25-877a-7125647f6534', url: 'https://github.com/Smarsh/jenkinsBuildLibs.git'
          }
          helm = load 'library/src/com/smarsh/pipeline/helm.groovy'

        }

        customContainer('helm') {

          stage('Lint') {
            /* run some basic sanity check */
            helm.lint("${config.chartName}")
          }

          stage('Dry Run') {
            /* run a dry-run install */
            helm.deploy([ dry_run: true, chart_dir: "${config.chartName}", namespace: 'default', release: "${config.chartName}", values: [] ])

            /* I want to push the output to a PR when a PR build
            step ([
                $class: 'GitHubPRCommentPublisher',
                comment: [ content: 'this is a test' ]
              )]
            */
          }

        }

        customContainer('maven') {

          /* package up chart and upload to our repository */
          stage('Upload Artifact') {
          
            /* retrieve the stashed version */ 
            unstash 'version' 

            def repoURL = "http://beacon-archiva.default.svc.cluster.local/repository/snapshots"
            if (env.BRANCH_NAME == "master") {
              repoURL = "http://beacon-archiva.default.svc.cluster.local/repository/internal"
            }

            ksh("mvn package")
            ksh(""" mvn deploy:deploy-file \
                -DgroupId=${pom.groupId} \
                -DartifactId=${pom.artifactId} \
                -Dpackaging=tar.gz \
                -DgeneratePom=false \
                -DrepositoryId=archiva \
                -Dversion=${version} \
                -Dfile=target/${config.chartName}-${version}-chart.tar.gz \
                -Durl=${repoURL}
                """)
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


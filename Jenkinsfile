podTemplate(label: 'mypod',
    containers: [
        containerTemplate(name: 'maven', image: 'quay.io/smarsh/docker-build-maven:latest', command: 'cat', ttyEnabled: true, alwaysPullImage: true),
    ]
){
  node('mypod') {

    if ("${BRANCH_NAME}" == 'master') {
      container('maven') {
          
        stage ('Checkout') {
          checkout scm
          sh 'git rev-parse --short HEAD > commit'
          def commit = readFile('commit').trim()
          currentBuild.displayName = "${commit}"
        }

        stage ('base-dependencies') {
          dir('pom/base-dependencies') {
            pom = readMavenPom file: 'pom.xml'

            pomVersionSplit = pom.version.tokenize('.')
            version = String.format("%d.%d.%d", pomVersionSplit[0].toInteger(), pomVersionSplit[1].toInteger(), currentBuild.number)
            pom.version = version
            writeMavenPom model: pom

            sh "mvn clean deploy"
          }
        }
        
        stage ('core-data-mybatis') {
          dir('pom/core-data-mybatis') {
            pom = readMavenPom file: 'pom.xml'

            pomVersionSplit = pom.version.tokenize('.')
            version = String.format("%d.%d.%d", pomVersionSplit[0].toInteger(), pomVersionSplit[1].toInteger(), currentBuild.number)
            pom.version = version
            writeMavenPom model: pom

            sh "mvn clean deploy"
          }
        }

          
        office365ConnectorSend message: "BUILD status: ${currentBuild.result}", status: currentBuild.result, webhookUrl:'https://outlook.office.com/webhook/c9379b1e-7b99-4659-86dc-12d80cb9ea3f@01bc53fb-79ca-4409-b3ed-a1a5082bdd00/JenkinsCI/25d32d89dc534d51b6d129e74f03e73b/f127f2ba-a51f-4b4c-b5c7-0b3c421b4f91'
      }
    } else {
      stage 'Nothing to see here'
    }
  }
}


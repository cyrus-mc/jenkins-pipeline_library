class smarshCheckout() {

    checkout scm
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

    pom.version = version
    writeMavenPom model: pom
    currentBuild.displayName = "${version}"
    sh "echo ${version} > version"

    sh 'git rev-parse --short HEAD > commit'
    def commit = readFile('commit').trim()

    jobNameSplit = env.JOB_NAME.split('/')
    jobName = jobNameSplit[1].toLowerCase()

    stash name: 'version', includes: 'version'
    stash name: 'commit', includes: 'commit'

}

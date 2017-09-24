def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  if ( "${BRANCH_NAME}" == 'master' ) {
    to = 'beaconbuildnotification@smarsh.com'
  } else {
    wrap([$class: 'BuildUser']) {
      to = "${env.BUILD_USER_EMAIL}"
    }
  }

  def subject = config.subject ? config.subject : "${env.JOB_NAME} - Version ${currentBuild.displayName} - ${currentBuild.result}!"
  def content = '${JELLY_SCRIPT,template="html"}'

  // Attach buildlog when the build is not successfull
  def attachLog = (config.attachLog != null) ? config.attachLog : (currentBuild.result != "SUCCESS")

  // Send email
  emailext(
    body: content,
    mimeType: 'text/html',
    replyTo: 'Jenkins <jenkins@smarsh.com>',
    subject: subject,
    to: to,
    attachLog: attachLog
  )

}

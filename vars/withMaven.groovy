#!/usr/bin/groovy

/**
 * Wraps the code in a podTemplate with the Maven container.
 * @param parameters  Parameters to customize the Maven container
 * @param body        The code to wrap
 * @return
 */

def call(Map parameters = [:], body) {

  def defaultLabel = buildId('maven')
  def label = parameters.get('label', defaultLabel)
  def name = parameters.get('name', 'maven')

  def cloud = parameters.get('cloud', 'kubernetes')
  def envVars = parameters.get('envVars', [])
  def inheritFrom = parameters.get('inheritFrom', 'base')
  def namespace =  parameters.get('namespace', 'default')
  def serviceAccount = parameters.get('serviceAccount', '')
  def idleMinutes = parameters.get('idle', 10)

  def mavenImage = parameters.get('mavenImage', 'quay.io/smarsh/docker-build-maven:latest')
  def mavenPVC = parameters.get('mavenPVC', 'jenkins-mvn-local-repo')

  podTemplate(cloud: "${cloud}", name: "${name}", namespace: "${namespace}", label: label, inheritFrom: "${inheritFrom}",
          serviceAccount: "${serviceAccount}",
          idleMinutesStr: "${idleMinutes}",
          containers: [ containerTemplate(name: 'maven', image: "${mavenImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true, envVars: envVars) ],
          volumes: [ persistentVolumeClaim(claimName: "${mavenPVC}", mountPath: '/root/.m2/repository')] ) {
     body()
   }
}

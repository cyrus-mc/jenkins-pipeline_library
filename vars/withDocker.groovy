#!/usr/bin/groovy

/**
 * Wraps the code in a podTemplate with the Docker container.
 * @param parameters  Parameters to customize the Docker container
 * @param body        The code to wrap
 * @return
 */

def call(Map parameters = [:], body) {

  def defaultLabel = buildId('docker')
  def label = parameters.get('label', defaultLabel)
  def name = parameters.get('name', 'docker')

  def cloud = parameters.get('cloud', 'kubernetes')
  def envVars = parameters.get('envVars', [])
  def inheritFrom = parameters.get('inheritFrom', 'base')
  def namespace = parameters.get('namespace', 'default')
  def serviceAccount = parameters.get('serviceAccount', '')
  def idleMinutes = parameters.get('idle', 10)

  def dockerImage = parameters.get('dockerImage', 'docker:17.06.0-ce-git')

  podTemplate(cloud: "${cloud}", name: "${name}", namespace: "${namespace}", label: label, inheritFrom: "${inheritFrom}",
          serviceAccount: "${serviceAccount}",
          idleMinutesStr: "${idleMinutes}",
          containers: [ containerTemplate(name: 'docker', image: "${dockerImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true, envVars: envVars) ],
          volumes: [ hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock') ] ) {
    body()
  }
}

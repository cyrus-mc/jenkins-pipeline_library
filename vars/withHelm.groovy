#!/usr/bin/groovy

/**
 * Wraps the code in a podTemplate with the Helm container.
 * @param parameters Parameters to customize Helm container
 * @param body The code to wrap
 * @return
 */

def call(Map parameters = [:], body) {

  def defaultLabel = 'helm'
  def label = parameters.get('label', defaultLabel)
  def name = parameters.get('name', defaultLabel)

  def cloud = parameters.get('cloud', 'kubernetes')
  def envVars = parameters.get('envVars', [])
  def inheritFrom = parameters.get('inheritFrom', 'base')
  def namespace = parameters.get('namespace', 'default')
  def serviceAccount = parameters.get('serviceAccount', '')
  def idleMinutes = parameters.get('idle', 10)

  /** specify the default container image to use */
  def helmImage = parameters.get('helmImage', 'quay.io/smarsh/helm:latest')

  podTemplate(cloud: "${cloud}", name: "${name}", namespace: "${namespace}", label: "${label}", inheritFrom: "${inheritFrom}",
          serviceAccount: "${serviceAccount}",
          idleMinutesStr: "${idleMinutes}",
          containers: [ containerTemplate(name: 'helm', image: "${helmImage}", command: '/bin/sh -c', args: 'cat', ttyEnabled: true, alwaysPullImages: true, envVars: envVars) ] ) {

    body()

  }
}

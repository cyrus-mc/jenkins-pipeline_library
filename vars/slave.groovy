#!/usr/bin/groovy

/**
 * Wraps the code in a podTemplate with the JNLP container.
 * @param parameters  Parameters to customize the JNLP container
 * @param body        The code to wrap
 * @return
 */

def call(Map parameters = [:], body) {

  def defaultLabel = buildId('jnlp')
  def label = parameters.get('label', defaultLabel)
  def name = parameters.get('name', buildId())

  def cloud = parameters.get('cloud', 'kubernetes')
  def inheritFrom = parameters.get('inheritFrom', 'base')
  def namespace = parameters.get('namespace', 'default')
  def serviceAccount = parameters.get('serviceAccount', '')
  def idleMinutes = parameters.get('idle', 10)

  def jnlpImage = parameters.get('jnlpImage', 'jenkinsci/jnlp-slave:2.62-alpine')

  podTemplate(cloud: "${cloud}", name: "${name}", namespace: "${namespace}", label: label, inheritFrom: "${inheritFrom}", 
          serviceAccount: "${serviceAccount}", idleMinutesStr: "${idleMinutes}",
          containers: [ containerTemplate(name: 'jnlp', image: "${jnlpImage}", args: '${computer.jnlpmac} ${computer.name}') ] ) {
     body()
   }
}

#!/usr/bin/groovy

/**
 * Wraps the code in a slave container
 * @param parameters
 * @param body
 * @return
 */

def call(Map parameters = [:], body) {
  
  def defaultLabel = buildId('slave')
  def label = parameters.get('label', defaultLabel)

  slave(parameters) {
    node(label) {
      try {
        body()
      } catch (Exception e) {
        currentBuild.result = 'FAILURE'
        throw e; // rethrow so the build is considered failed 
      }
    }
  }
}

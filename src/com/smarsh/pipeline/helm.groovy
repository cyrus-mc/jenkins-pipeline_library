#! /usr/bin/groovy

package com.smarsh.pipeline;

def pkg(String chart_dir) {

  // read in Chart.yaml
  def chart = readYaml file: 'Chart.yaml'

  def version = chart.version

}

/*
  Read Chart name from Chart.yaml.in template file
*/
def name() {

  // read in Chart.yaml.in (chart name should match)
  def chart = readYaml file: 'Chart.yaml.in'

  return chart.name
}

def stage() {
  // Chart name must match directory name
  def chart = readYaml file: 'Chart.yaml'

  println "Staging helm chart ${chart.name}"
  ksh """
    [ ! -d ${chart.name} ] && mkdir ${chart.name}
    mv Chart.yaml README.md templates values.yaml ${chart.name}
  """

  return chart.name
}

def lint(String chart_dir) {

  // lint helm chart
  println "running helm lint ${chart_dir}"
  ksh "helm lint ${chart_dir}"

}

def config() {

  // setup helm connectivity to Kubernetes API and Tiller
  println "initiliazing helm client"
  ksh "helm init"

  println "checking client/server version"
  ksh "helm version"

}

def addRepo(Map args) {

  // configure helm client and confirm tiller process is installed
  config()

  // add the specified repository
  println "adding helm repository ${args.name}"
  ksh "helm repo add ${args.name} ${args.url}"

}


def deploy(Map args) {

  String set = "";
  for ( a in args.values ) {
    set += "${a.key}=${a.value},"
  }
  if (set.length() > 0) {
    set = set.subSequence(0, set.length() - 1)
  }

  /* check if environment values file exists */
  String values = "";
  if (fileExists("${args.namespace}.yaml")) {
     values += "-f ${args.namespace}.yaml" 
  }

  if (args.dry_run) {

    println "Running dry-run deployment"
    ksh "helm upgrade --dry-run --debug --install ${args.release} ${args.chart_dir} ${values} --set ${set} --namespace=${args.namespace}"

  } else {

    println "Running deployment"
    ksh "helm upgrade --install ${args.release} ${args.chart_dir} ${values} --set ${set} --namespace=${args.namespace}"

    echo "Application ${args.release} successfully deployed. Use helm status ${args.release} to check"

  }

}

def test(Map args) {

  println "Running Helm test"
  ksh "helm test ${args.release} --cleanup"

}

return this;

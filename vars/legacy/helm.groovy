def helmLint(String chart_dir) {

  // lint helm chart
  println "running helm lint ${chart_dir}"
  sh "helm lint ${chart_dir}"

}

def helmConfig() {

	//setup helm connectivity to Kubernetes API and Tiller
  println "initiliazing helm client"
  sh "helm init"

  println "checking client/server version"
  sh "helm version"

}

def helmAddRepo(Map args) {

  // configure helm client and confirm tiller process is installed
  helmConfig()

  // add the specified repository
  println "adding helm repository ${args.name}"
  sh "helm repo add ${args.name} ${args.url}"

}


def helmDeploy(Map args) {

  //configure helm client and confirm tiller process is installed
  helmConfig()

  String set = "";
  for ( a in args.values ) {
    set += "${a.key}=${a.value},"
  }
  set = set.subSequence(0, set.length() - 1)

  if (args.dry_run) {

    println "Running dry-run deployment"
    sh "helm upgrade --dry-run --install ${args.name} ${args.chart_dir} --set ${set} --namespace=${args.name}"

  } else {

    println "Running deployment"
    sh "helm upgrade --install ${args.name} ${args.chart_dir} --set ${set} --namespace=${args.name}"

    echo "Application ${args.name} successfully deployed. Use helm status ${args.name} to check"

  }

}

def helmTest(Map args) {

  println "Running Helm test"
  sh "helm test ${args.name} --cleanup"

}

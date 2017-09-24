/*
 * Wrapper around sh to invoke all commands via kubectl
*/
def ksh(command) {
  if (env.CONTAINER_NAME) {
    if ((command instanceof String) || (command instanceof GString)) {
      command = kubectl(command)
    }
    
    if (command instanceof LinkedHashMap) {
      command["script"] = kubectl(command["script"])
    }
  }
  
  sh(command)
}

def kubectl(command) {
  "kubectl exec -i ${env.HOSTNAME} -c ${env.CONTAINER_NAME} -- /bin/sh -c 'cd ${env.WORKSPACE} && ${command}'"
}

/*
 * Set environment variable CONTAINER_NAME in the current context
*/
def customContainer(String name, Closure body) {
  withEnv(["CONTAINER_NAME=$name"]) {
    body()
  }
}

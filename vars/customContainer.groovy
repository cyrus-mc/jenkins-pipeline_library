/*
 * Set environment variable CONTAINER_NAME in the current context
*/
def call(String name, Closure body) {
  withEnv(["CONTAINER_NAME=$name"]) {
    body()
  }
}

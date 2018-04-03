#!/usr/bin/groovy

/*
  This function generates AWS credentials for use during pipelines.
*/
def call(vaultAddr, role) {
  def response;
  def jsonObj;

  // query EC2 metadata service for AWS instance identity document
  response = httpRequest "http://169.254.169.254/latest/dynamic/instance-identity/pkcs7"
  identity = "${response.content.replaceAll("[\n\r]", "")}"

  // now authenticate to vault using the above identity
  def body = """
    {
      "role": "jenkins",
      "pkcs7": "${identity}",
      "nonce": "123456789"
    }
  """
  response = httpRequest requestBody: body, httpMode: 'POST', url: "http://${vaultAddr}/v1/auth/aws/login"
  jsonObj = readJSON text: response.content
  client_token = jsonObj.auth.client_token

  // finally generate aws/sts secret
  response = httpRequest customHeaders: [[ name: 'x-vault-token', value: "${client_token}" ]], httpMode: 'POST', url: "http://${vaultAddr}/v1/aws/sts/${role}"
  jsonObj = readJSON text: response.content

  // return aws security credentials
  [ access_key: jsonObj.data.access_key, secret_key: jsonObj.data.secret_key, security_token: jsonObj.data.security_token  ]
}
~

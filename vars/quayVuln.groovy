import groovy.json.JsonSlurperClassic;

def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  println config.repo
  println config.tag

  url = "https://quay.io/api/v1/repository/${config.repo}/tag/?specificTag=${config.tag}"
  token = 'dOuPjxXvBsGMGenSCID6wWfZgHYPhxJ7hUEYG6Qc'

  text = url.toURL().getText(requestProperties: [Authorization: "Bearer ${token}"])
  json = new JsonSlurperClassic().parseText(text)

  id = json.tags.docker_image_id[0]

  url = "https://quay.io/api/v1/repository/smarsh/archiveapi/image/${id}/security?vulnerabilities=true"

  text = url.toURL().getText(requestProperties: [Authorization: "Bearer ${token}"])
  json = new JsonSlurperClassic().parseText(text)

  def list = []

  json.data.Layer.Features.each {
    if (it.Vulnerabilities) {
      it.Vulnerabilities.each {
        list.add(it.Severity)
      }
    }
  }

  vulnHigh =  list.count('High')
  vulnMed =  list.count('Medium')
  vulnLow =  list.count('Low')
  vulnNeg =  list.count('Negligible')
  vulnUnk =  list.count('Unknown')

  println vulnHigh
  println vulnMed
  println vulnLow
  println vulnNeg
  println vulnUnk

  vulnerabilities = [High:vulnHigh, Medium:vulnMed, Low:vulnLow, Negligible:vulnNeg, Unkown:vulnUnk]

  println vulnerabilities

  return vulnerabilities

}

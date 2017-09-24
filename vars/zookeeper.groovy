@NonCPS
def parseXml(text) {
    def lazyMap = new XmlParser().parseText(text)
    lazyMap.Applications.Application.AppSettings.AppSetting.each {
      if (it.@name == 'ApplicationVersion') {
        xmlVersion = it.text()
      }
    }

    return xmlVersion
}

def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  node {
    checkout scm
    sh 'git rev-parse --short HEAD > commit'
    def commit = readFile('commit').trim()
    sh "git diff-tree --no-commit-id --name-only -r ${commit} > changeList"
    def files = readFile('changeList').trim()
    def list =  files.tokenize()

    println list

    for (i = 0; i < list.size(); i++) {
      file = list[i]
      println file
      if (file == 'Jenkinsfile') {
        println "doing nothing for JenkinsFile"
      } else if (file =~ /.*\.xml/) {

        text = readFile(file).trim()
        println parseXml(text)


        //def xml=new XmlSlurper().parse(file)
        //println "${file} is xml"
        //sh 'pwd > pwd'
        //pwd = readFile('pwd').trim()
        //def parser = new XmlParser()
        //text = readFile(file).trim()
        //def xml = XmlParser().parseText(text)
        //println xml
      } else if (file =~ /txt/) {
        text = readFile(file).trim()
        lines = text.readLines()
        for (i = 0; i < lines.size(); i++) {
          line = lines[i]
          if(line.contains('ApplicationVersion')){
            txtVersion = line.tokenize()[1]
            zkVersionPath = line.tokenize()[0]
          }
        }
        app = file.tokenize('/')[0]

        zkServers = 'qa-zookeeper-01.smarshqa.com'
        sh "zookeepercli -servers ${zkServers} -c get ${zkVersionPath} > zkVersion"
        def zkVersion = readFile('zkVersion').trim()

        println app
        println txtVersion
        println zkVersionPath
        println "${BRANCH_NAME}"
        println zkVersion

      } else {
        println "${file} is not a txt or xml file"
      }
    }
  }
}

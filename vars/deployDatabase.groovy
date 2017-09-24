/*
*  Check the last executed DB job matching the job name supplied to ensure
*   (1) Success and (2) that the version is compatible with the required version
*/
def checkDeployedVersion(String jobname, String version) {

	def map, versionExp;

	versionExp = version.replaceAll(/(\d+)\.(\d+)\.(\d+)/) { fullVersion, major, minor, revision ->
	  return "${major}.${minor}.*"
	}

	// get DB deployment status
	println "checking for deployed database (${jobname}):${version}"
	sh "kubectl get job ${jobname} -o yaml > ${jobname}-status.yml"

	Yaml yaml = new Yaml()
	map = yaml.load(streamFileFromWorkspace('${jobname}-status.yml'))

	return map['status']['succeeded'] == 1 && checkVersionCompatibility(version, map['labels']['version']);
}

def deployDatabase(String jobname, String version) {
	// TODO: download correct version artifact to workspace
	sh "kubectl create -f ${jobname}.yml"
}

def cleanupJob(String jobname) {
	sh "kubectl delete job ${jobname}"
}

/*
*  Check major & minor version compatibility
* 
*  Example
*  ---------------
*  checkVersionCompatibility("1.2.456", "1.1.232") == FALSE;​​
*  checkVersionCompatibility("1.2.456", "1.2.232") == TRUE;​​
*/
def checkVersionCompatibility(expected, actual) {
	def versionExp, matched;

	println("Expected version: ${expected}");
	println("Actual version: ${actual}");

	versionExp = expected.replaceAll(/(\d+)\.(\d+)\.(\d+)/) { fullVersion, major, minor, revision ->
	  return "${major}.${minor}"
	}

	println("versionExp: ${versionExp}");

	matched = (actual ==~ /${versionExp}\.(\d+)/);
	println("versionFound: ${matched}");

	return matched;
}
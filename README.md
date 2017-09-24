# jenkinsBuildLibs
https://jenkins.io/doc/book/pipeline/shared-libraries/

Based on [Jenkins Shared Libraries](https://jenkins.io/doc/book/pipeline/shared-libraries/)

## Technical Debt

* [ ] Need to review pipeline after consolidation

* Version is determined different for every type of build

* smarshClean.groovy uses imageName for PostgreSQL builds, but we have no concept
of building imageName yet. Currently just passes null and does nothing.

## Branching Strategry

All build pipelines are built around the following Git workflow :

  * http://scottchacon.com/2011/08/31/github-flow.html

The basic break down of this work flow is as follows

  * anything in master branch is deployable
  * create descriptive branches off of master
  * push to named branches constantly
  * open a pull request at any time
  * merge only after pull request review
  * deploy immediately after review


Our "descriptive" branch names should be of the form BEAC-#### where #### is the Story number.
This is a requirement as the Story # is used when generating a SNAPSHOT artifact for upload to
our internal archiva snapshot repository.

### Sample Workflow

A developer is assigned a Story to add Feature X to project Y.

The developer first checks out project Y repository.

    git clone git@github.com:Smarsh/projectY.git
    cd projectY

The first thing the developer does is create a named branch under which their development will
be done.

    # create branch and switch over to it
    git checkout -b BEAC-21054

    # push branch to GitHub (at this point in time the branch is identical to master)
    git push origin BEAC-21054

The developer should then go into GitHub and open a Pull Request for the branch they just
created. All about pull requests is documented on GitHub Help pages (https://help.github.com/articles/about-pull-requestss)

From there the developer is free to start their development. Best practices are to keep your commits
small and frequent. This allows you and others to track your development and train of thought
during the development process.

    # commit latest changes
    git commit -a -m "Some description of what changed"

    # push the changes to GitHub
    git push origin BEAC-21054

Repeat the above process until development is complete. At this point the Pull Request should be
reviewed and approved and then merged into the master branch.

## Java Library Pipeline

Java library projects (ex: AdminAPIClient) are built using the following pipeline.

### Branch

  * checkout branch
  * build project
  * execute UNIT tests
  * execute SonarScan
  * upload artifact

During the upload artifact, the resulting output of the build (usually a JAR file) is uploaded to
our internal SNAPSHOT repository.

To allow testing against this SNAPSHOT library the versioning is as such: X.Y.ISSUE#-SNAPSHOT
(where X is the major number, Y is the minor number, ISSUE# is the JIRA issue number as listed in
the branch name (BEAC-ISSUE#)). This versioning scheme allows consumers of this library to test
against current development by updating their pom to reference the above version.

### Master

Executed after a merge from a development branch into master.

  * checkout master
  * build project
  * execute UNIT tests
  * execute SonarScan
  * upload artifact

During the upload artifact, the resulting output of the build (usually a JAR file) is uploaded to
our internal repository. The versioning scheme will be X.Y.Z where X is the major, Y is the minor
and Z is the patch. The developer controls X and Y in accordance with semantic versioning, where Z
is controlled by the Jenkins build server itself.

## Postgres Database Pipeline

Postgres projects (ex: AdminDB) are built using the following pipeline.

### Branch

  * checkout branch
  * create temporary environment (namespace)
  * install postgres
  * run full migration
  * reset database
  * run incremental migration
  * upload artifact

During the upload artifact stage, the resulting output of the build (JAR file) is uploaded to
our internal SNAPSHOT repository.

To allow testing against this SNAPSHOT library the versioning is as such: X.Y.ISSUE#-SNAPSHOT
(where X is the major number, Y is the minor number, ISSUE# is the JIRA issue number as listed in
the branch name (BEAC-ISSUE#)). This versioning scheme allows consumers of this library to test
against current development by updating their pom to reference the above version.

### Master

Executed after a merge from a development branch into master.

  * checkout master
  * create temporary environment (namespace)
  * install postgres
  * run full migration
  * upload artifact

During the upload artifact, the resulting output of the build (JAR file) is uploaded to
our internal repository. The versioning scheme will be X.Y.Z where X is the major, Y is the minor
and Z is the patch. The developer controls X and Y in accordance with semantic versioning, where Z
is controlled by the Jenkins build server itself.

## Java API Service (Docker container)

Java API Services (ex: AdminAPI) are built using the following pipeline.

### Branch

Executed for every push to a branch.

  * checkout branch
  * build project
  * execute UNIT tests
  * execute Contract Test
  * execute Integration Test (??)
  * execute SonarScan

There will be no artifact uploaded as the result of this pipeline.

#### Master

Executed after a merge from a development branch into master.

  * checkout branch
  * build project
  * execute UNIT tests
  * execute Contract Tet
  * execute Integration Test (??)
  * execute SonarScan
  * build docker image
  * tag
  * deploy (container + db)

During the creation of the docker image and tag steps the repository will be verisoned using
the following scheme, X.Y.Z where X is the major, Y is the minor and Z is the patch. The
developer controls X and Y in accordance with semantic versioning and Z is controlled by the
Jenkins build server itself.

The deploy stage requires some further explanation. The DB pipelines have been developed in
such a way that the DB code can be linked to the API through the same mechanisms that you
do for shared libraries (dependency directive in pom.xml). It is up to the developer of
both the component and the database to coordinate the version dependency.

During deploy, the database artifact will be retrieved from our repository and a migration
ran against the database. Once that completes, the API component will be updated.

## smarshPipeline

smarshPipeline is the main entrypoint for the Jenkins Pipeline via Jenkinsfile.

It contains 8 stages, described in other groovy files:

| Stage | Corresponding File     |
| :------------- | :------------- |
| Pre-Build Cleanup | `smarshClean.groovy`|
| Code Checkout | `smarshCheckout.groovy`|
| Build |`smarshBuild.groovy`|
| Unit Test| `smarshUnitTest.groovy` |
| SonarQube| `smarshSonarQube.groovy` |
| Integration Test| `smarshIntegrationTest.groovy` |
| Upload Artifact| `smarshUpload.groovy` |
| Post-Build Cleanup| `smarshClean.groovy` |

It additionally utilizes the following:
| File | Utility |
|:--|:--|
| `smarshBuildEmails.groovy` | Determines how to send e-mails for builds |
|`smarshPublishToS3.groovy` | Determines how to publish artifacts to S3 |

### Getting Started

To use the pipeline, create a file named Jenkinsfile at the base of the git
repository and enter the following:

Simple Java Example:

```groovy
@Library('jenkinsBuildLibs') _
smarshPipeline {
  type = 'java'
}
```

Java Library that publishes artifacts to S3 and reports status to MS Teams:

```groovy
@Library('jenkinsBuildLibs') _
smarshPipeline {
  type = 'java_library',
  channel_url = 'https://outlook.office.com/webhook/c9379b1e/JenkinsCI/25d32/f127f2ba-a51',
  publish_build = true
}
```

I want to run only a specific piece of the Pipeline
```groovy
@Library('jenkinsBuildLibs') _
checkout scm
# Any other Jenkins Tasks here
smarshUnitTest('java','pom.xml','custom-test','myapp',env.BUILD_NUMBER)
```

#### Containers
| Container | Description | Image  |
|:---|:---|:---|
| docker | main docker image.| N/A | |
| maven-java | Docker Image designed for Java and Java Libraries | [`quay.io/smarsh/docker-build-maven`]( https://quay.io/smarsh/docker-build-maven) |
| maven-dotnet | Docker Image designed for .NET Core and .NET Librares | [`quay.io/smarsh/docker-build-maven:apt`](https://quay.io/smarsh/docker-build-maven:apt)|
| kubectl | Docker image designed for interracting with Kubernetes Cluster | [`quay.io/smarsh/jnlp-slave`](https://quay.io/smarsh/jnlp-slave)|

### Parameters
| Parameter | Description | Valid Options |
|-|-|-|
| `type` | The type of the build. This determines the way the Jenkins pipeline functions for the git repository.  |java, dotnet, dotnetcore, dotnet\_library, java\_library |
| `channel_url` | (Optional) Office 365 Webhook for Sending Build Status updates to.| String containing a Webhook URL |
| `publish_build` | Option to publish all artifacts to Amazon S3.| True or False (Boolean) |

### Variables
|Name|Description|
|-|-|
|`name`|Retrieves Job Name from Jenkins|
|`ID`|Tokenized Name|
|`podName`|Name of Kubernetes pod - ID-branch-buildnumber|
|`nameSpace`|Kubernetes Namespace - podName in all lower case|
|`pomFile`|Location of pom.xml|

## smarshClean.groovy

This stage cleans up the workspace Jenkins runs in

### Parameters
|Name|Description|
|-|-|
| `type` |  Type of Build |
| `imageName` |  Name of Docker Image. (This param is currently
unused, with plans to) |
| `nameSpace` |  Name of Kubernetes namespace |
| `stage` |  Declares if cleanup is pre or post build. |

## smarshCheckout.groovy

This stage checks out code and determines a version for the projet

### Parameters
|Name|Description|
|-|-|
| `type` |  Type of Build |
| `pomFile` |  Location of pom.xml |

## smarshBuild.groovy

Performs build actions to compile code or create packages

### Parameters
|Name|Description|
|-|-|
| `type` |  Type of Build |
| `pomFile` |  Location of pom.xml |
| `ID` |  Tokenized Name |
| `nameSpace` |  Kubernetes Namespace |
| `BUILD\_NUMBER` |  Jenkins Build Number |

## smarshUnitTest.groovy

Performs unit tests of code

## Parameters
|Name|Description|
|-|-|
| `type` |  Type of Build |
| `pomFile` |  Location of pom.xml |
| `ID` |  Tokenized Name |
| `nameSpace` |  Kubernetes Namespace |
| `BUILD\_NUMBER` |  Jenkins Build Number |

## smarshSonarQube.groovy

Runs SonarQube against the code for vulnerability scans

### Parameters
|Name|Description|
|-|-|
| `type` |  Type of Build |
| `branch` |  Git Branch |

## smarshIntegrationTest.groovy

Currently a framework, and doesn't do anything yet

### Parameters

## smarshUpload.groovy

Uploads the artifact to the artifact repository

### Parameters
|Name|Description|
|-|-|
| `type` |  Type of Build |
| `pomFile` |  Location of pomFile |

# Legacy Pipeline

Below this is the legacy pipeline being replaced by the above pipeline.

## vars - Global Variables accessible from Pipeline

### Currently Used Jenkins Environment Variables Reference
| Envvar | Description |
|---|---|
| `env.BRANCH_NAME`|  Git Branch as pulled by Jenkins |
| `env.BUILD_NUMBER`|  Jenkins Build Number. Increments at each run of a job. |
| `env.JOB_NAME`|  Jenkins Job Name. |

### database.groovy

#### Use

##### Jenkinsfile
```groovy
@Library('jenkinsBuildLibs') _
database {
  jobName = "<Name of Jenkins Job>"
  databaseName = "<Name of Database inside of Image>"
  imageName = "<Name of Docker Image>"
}
```

##### Job.yaml

A job.yaml should be placed at /src/main/fabric8/job.yaml to facilitate the
Jenkins build. The YAML should have following content:

***NOTE In the future, should consider baking this directly into the Jenkins
job***

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: db-migrate-${project.artifactId}
spec:
  template:
    metadata:
      name: db-migrate-${project.artifactId}
    spec:
      containers:
      - name: smarsh-${project.artifactId}
        image: localhost:5000/${project.artifactId}:${commitHash}
        command:
        - 'flyway'
        - '-url=jdbc:postgresql://${fabric8.postgresServer}:${postgres.port}/${postgres.db}'
        - '-user=${postgres.user}'
        - '-password=${postgres.password}'
        - 'migrate'
      restartPolicy: Never
      parallelism: 1
      completions: 1
```

##### pom.xml

The pom will need the following properties at a minimum:

```xml
  <properties>
    <postgres.server></postgres.server>
    <postgres.port>5432</postgres.port>
    <postgres.db>solrinvdb</postgres.db>
    <postgres.user>flyway</postgres.user>
    <postgres.password>flyway</postgres.password>
    <commitHash>1</commitHash>
  </properties>
```

#### Parameters

Jenkinsfile
|Name|Description|
|-|-|
| `jobName` |  Name of Jenkins Job. Described in Jenkinsfile. |
| `databaseName` |  Name of Database to be tested. Described in Jenkinsfile |
| `imageName` |  Name of Docker Image to be ran during job. Described in Jenkinsfile |

#### Variables
|Name|Description|Example Value|
|---|---|---|
| `name` |  Job name. | "my/Job" |
| `ID` |  Tokenized Job name. | "myJob" |
| `podName`|  Combination of ID, env.BRANCH_NAME and env.BUILD_NUMBER. |"myJob-master-42" |
| `nameSpace` |  podName lowercased. | "myjob-master-42" |

#### Containers

All images are found at quay.io/smarsh, have TTY enabled and pull the image on each run.

* maven: docker-build-maven apt
* kubectl: jnlp-slave

#### Stages Overview

##### Cleanup Old Images

Container: Maven

Input: `imageName` (Jenkinsfile).

This stage removes all images named `imagename`, as defined in the Jenkinsfile

##### Checkout

Container: Maven

This stage checks out the full repository from GitHub

##### Build

Container: Maven

Input: `ID` (database.groovy), `nameSpace` (database.groovy), `env.BUILD_NUMBER`
(Jenkins).

This stage builds the postgres server using fabric8.

Output: ${nameSpace}-postgresql-master server

##### Create Kubernetes Namespace

Container: kubectl

Input: `nameSpace` (database.groovy).

Creates a namespace with kubectl

##### Configure Helm

Container: kubectl

Adds the smarsh-charts Helm repository to the kubectl container

##### Install PostgreSQL Chart

Container: kubectl

Input: `nameSpace` (database.groovy), `config.databaseName` (Jenkinsfile)

Installs the PostgreSQL chart into the job, and installs the database with the
name `config.databaseName`.

This stage will stall until it sees ${nameSpace}-postgresql-0 running on the
cluster.

##### Flyway

Container: Maven

Input: `nameSpace` (database.groovy), `config.imageName` (Jenkinsfile),
`env.BUILD_NUMBER` (Jenkins), `config.jobName` (Jenkinsfile)

This stage performs a fabric8 apply on ${config.imageName}:${env.BUILD\_NUMBER}
in the kubernetes ${nameSpace} in the Maven container using the build number as
a commit hash.

This stage will then search for pods with the jobName that are labeled completed
for 1 minute.

Should the container not come up as Completed, the stage will print the logs of
any pod with the job name and the status Error.

BUG: Jenkins is not printing out the error in this stage as the main error. We
are instead printing it as the final line in the stage.

##### Publish to S3

Container: Kubectl

Input: `ID` (database.groovy), `env.BRANCH\_NAME` (Jenkins), `env.BUILD\_NUMBER`
(Jenkins).

Places all artifacts from the build into:
`bucket: "beacon-jenkins/builds/${ID}-${env.BRANCH\_NAME}-${env.BUILD\_NUMBER}"`

##### Cleanup

Input: `nameSpace` (database.groovy)

Deletes the pods using helm and the namespace using kubectl

## dockerBuildTemplate

This template will build a Docker container from a github source repo, and then
deploy the container to the Quay.io repo.  It looks for the Dockerfile in the
root directory of the source repo, and if it doesn't exist, it will then look in
the docker folder for the Dockerfile.  

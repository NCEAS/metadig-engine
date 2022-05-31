
# MetaDIG Metadata Assessment Engine (metadig-engine)

The MetaDIG (Metadata Improvement and Guidance) project has been funded for metadata improvement advocacy, the development of metadata evaluation criteria and the development of metadata assessment tools. This repository contains a metadata assessment tool, the MetaDIG engine, aka metadig-engine. The metadig-engine assesses a metadata document to see how closely it adheres to a suite of checks. Each assessment check inspects the contents of one or more elements from the metadata document. From this assessment process, a report is generated that describes if each check's criteria are met or not.

## metadig-engine dependancies Dependancies

The following components are required for the metadig-engine build or runtime.

### metadig-r

metadig-engine assessment checks that are written in the R programming language can use the [metadig-r](https://github.com/NCEAS/metadig-r) R package, which contains functions that perform common assessment tasks. The metadig-r package is made available to metadig-engine via an R source package, for example 'metadig_0.2.0.tar.gz`. This file is used during the building of the metadig-worker Docker image, which is described later in this document. Additional information on building and using the metadig-r R package is available from the [github repo](https://github.com/NCEAS/metadig-r).

The tar file build from this component is required for the metadig-engine build.

metadig-r can produce a distribution tar file that will be included in metadig-engine builds. This R package hasn't been published to CRAN so the distribution tar file must be created on a local 
github repo:

```
git clone https://github.com/NCEAS/metadig-r
R CMD build metadig-r
cp metadig_0.2.0.tar.gz metadig-engine/Docker/metadig-scorer
cp metadig_0.2.0.tar.gz metadig-engine/Docker/metadig-worker
```

This distribution file will be incorporated into the metadig-engine Docker containers when they are built.

### metadig-py

The metadig-py component is a Python package that assists in the authoring of metadig-engine checks written in Python. metadig-py is made available to metadig-engine by building the Python package from the [metadig-py](https://github.com/NCEAS/metadig-py) repository, then placing the `metadig-py` directory in the metadig-engine working directory, which is described in [Running metadig-engine](#running)

The Python package built by this package is required for meatdig-engine at runtime.

Currently this file has to be manually installed to /opt/local/metadig/metadig-py (also available from /mnt/k8ssubvol/metadig/metadig-py).

See https://github.com/NCEAS/metadig-py for details on building and deploying metadig-py.

### metadig-checks

Metadata assessment checks assoicated with this project can be found at https://github.com/NCEAS/metadig-checks. The metadig-checks distribution is built from that repository, then deployed to the metadig-engine working directory, as described in that repository.

Currently a metadig-checks release must be deployed manually to /opt/local/metadig (also available from /mnt/k8ssubvol/metadig).

See https://github.com/NCEAS/metadig-checks for details on building and deploying metadig-checks.

The metadig-engine suites and checks are required by metadig-engine at runtime.

### RabbitMQ

RabbitMQ is an open source messge brokering system that can be used to queue assessment requests for metadig-engine. 

MetaDIG engine uses RabbitMQ to communicate between metadig-controller and metadig-worker, metadig-scorer.

A RabbitMQ distribution is provided by Bitnami and installed via helm. See the *operations-manual* for details.

### Solr

A Solr Index search engine is used by metadig-engine at runtime.

A Solr distribution is provided by Bitnami and installed via helm. See the *operations-manual* for details.

## Building metadig-engine

The following Maven command performas a complete build of metadig engine. Each Maven target will be explained separately

```
mvn clean package install
```

If running tests is not desired, the package can be build with the command:

```
mvn clean package install -Dmaven.test.skip=true
```

- mvn clean

The previous build is removed in preparation for a new build.

- mvn package

All source code is compiled and the metadig-engine jar file is built.

- mvn install

The metadig-engine deliverables are copied to the local Maven repository (i.e. ~/.m2/repository). This step makes the deliverables available to other packages that require them. For example, the [metadig-webapp](https://github.com/NCEAS/metadig-webapp) repository requires metadig-engine for builds and can obtain them from the local Mavin repository. The metadig-engine repo builds and published the metadig-postgres, metadig-worker, metadig-scorer and metadig-scheduler Docker image. (The metadig-webapp repository builds and published the metadig-controller Docker image).

## Building and Publishing metadig-engine Docker images

Docker images are built for metadig-engine services and posted to https://hub.docker.com/r/metadig. Once the
software has been build, the Docker images can be build and distributed using the following command:

```
mvn docker:build docker:push

```

It is necessary to use Docker login credentials in order to push images to Docker Hub. The username and
password for the Docker Hub account are available in the NCEAS security repo at ./security/nceas/metadig-maven-settings.gpg.
This file should be decrypted and copied to ~/.m2/settings.xml. When maven runs, it will read the credentials from this file and send them to Docker hub to enable the publishing of Docker images.

After the maven publish step, Docker images are publicly available, for example, for metadig-controller, image tags and publishing dates can be viewed at
```
https://hub.docker.com/r/metadig/metadig-controller
```

## Components dependant on metadig-engine

### metadig-webapp

The [metadig-webapp repository](https://github.com/NCEAS/metadig-webapp) builds the metadig-controller distribution and Docker image. This image contains a distribution of the Tomcat servlet engine that metadig-controller runs inside of. The build process obtains the metadig-engine distribution from the local maven repository (~/.m2) after metadig-engine has been built.

Note that the pom.xml file must be updated so that the appropriate version of metadig-engine distribution is obtained, for example: https://github.com/NCEAS/metadig-webapp/blob/master/pom.xml#L10.

### Building metadig-webapp

Once metdig-engine has been built and installed to the local maven repository, metadig-webapp can be built.

The metadig-webapp component is included in the repo mentioned above, however, for convenience the metadig-webapp build process will be described here.

First, a local copy of the github repo should be created:

```
git clone https://github.com/NCEAS/metadig-webapp
cd metadig-webapp
```

The following Maven command performas a complete build of metadig engine. Each Maven target will be explained separately

```
mvn clean mvn package install
```

If running tests is not desired, the package can be build with the command:

```
mvn clean mvn package install -Dmaven.test.skip=true
```

The maven arguments for these commands are described below:

- mvn clean
The previous build is removed in preparation for a new build.

- mvn package
All source code is compiled and the metadig-engine jar file is built.

- mvn install

The metadig-engine deliverables are copied to the local Maven repository (i.e. ~/.m2/repository). This step makes the deliverables available to other packages that require them. For example, the [metadig-webapp](https://github.com/NCEAS/metadig-webapp) repository requires metadig-engine for builds and can obtain them from the local Mavin repository. The metadig-engine repo builds and published the metadig-postgres, metadig-worker, metadig-scorer and metadig-scheduler Docker image. (The metadig-webapp repository builds and published the metadig-controller Docker image).

## Environments supported by metadig-engine

metadig-engine can be run in the following environments, each of which can facilitate different levels of testing and development.

### Running Locally

In order to quickly test an updated an assessment suite, the metadig-engine assessment program can be called directly, without using metadig-controller and metadig-worker. This is done by calling the metadig-engine main Java class from the metadig-engine development working directory:

```
workDir=$PWD
doc="${workDir}/src/test/resources/test-docs/doi:10.18739_A2W08WG3R.xml"
sysmeta="${workDir}/src/test/resources/test-docs/doi:10.18739_A2W08WG3R.sm"
suite=/opt/local/metadig/suites/arctic-data-center.xml
java -cp ./target/metadig-engine-${version}.jar edu.ucsb.nceas.mdqengine.MDQEngine $suite $doc $sysmeta

```

Note that the metadig-check distribution can be installed locally into your local /opt/local/metadig directory so that this type of testing can be performed.
See the [metadig-webapp](https://github.com/NCEAS/metadig-webapp) for directions.

### Running Locally with RabbitMQ Message Queuing

In the k8s environment, RabbitMQ is used for communication between metadig-controller and other metadig services (metadig-worker, ...). Because building and publishing metadig-engine Docker iamges can be very time consuming, it is possible to test RabbitMQ messaging and Solr indexing on a local development machine.
It is required to install and start all dependencies listed in the `metadig-engine Dependencies` section.


The use of RabbitMQ with metadig-engine is intended to be run on a k8s deployment, however, RabbitMQ can be used on the local machine for development testing.

RabbitMQ can be installed easily on MacOS, for example, using [Homebrew](https://www.rabbitmq.com/install-homebrew.html). 

In addition, a local Solr server can be installed via homebrew so that indexing of assessment scores can be tested.

Depending on which programs require testing, metadig-controller, metadig-worker, metadig-scorer and metadig-scheduler can be started as background processes. Then sampe quality assessment or scoring request can be sent to metadig-controller (running in "test" mode).

- Install RabbitMQ
- Install Solr and create a "quality" core using the "solrconfig.xml" and "schema.xml" files in the metadig-engine repo
- copy metadig.properties to /opt/local/metadig
- copy log4j.properties to /opt/local/metadig/config
- copy required metadig-checks suite to /opt/local/metadig
- set the desired logging levels in /opt/local/metadig/config/log4j.properties

For a test of communication between metadig-controller and metadig-worker:
- start metadig-controller in "test" mode in one terminal window
  - bin/startController.sh &
- start metadig-worker in another terminal window
  - bin/startWorker.sh &
- send a sample request to metadig-controller
  - bin/sendAssessmentTest.py

View the log files in each terminal window. An example run is shown below.
In the metadig-controller window
```
$ ./bin/startController.sh 33000 &
20220509-20:42:36: [DEBUG]: Creating new controller instance [edu.ucsb.nceas.mdqengine.Controller]
20220509-20:42:36: [DEBUG]: Set RabbitMQ host to: localhost [edu.ucsb.nceas.mdqengine.Controller]
20220509-20:42:36: [DEBUG]: Set RabbitMQ port to: 5672 [edu.ucsb.nceas.mdqengine.Controller]
20220509-20:42:36: [INFO]: Connected to RabbitMQ queue quality [edu.ucsb.nceas.mdqengine.Controller]
20220509-20:42:36: [INFO]: Connected to RabbitMQ queue scorer [edu.ucsb.nceas.mdqengine.Controller]
20220509-20:42:36: [INFO]: Connected to RabbitMQ queue completed [edu.ucsb.nceas.mdqengine.Controller]
20220509-20:42:36: [DEBUG]: Controller is started [edu.ucsb.nceas.mdqengine.Controller]
20220509-20:42:36: [INFO]: The controller has been started [edu.ucsb.nceas.mdqengine.Controller]
20220509-20:42:36: [INFO]: # of arguments: 1 [edu.ucsb.nceas.mdqengine.Controller]
20220509-20:42:36: [INFO]: Controller test mode is enabled. [edu.ucsb.nceas.mdqengine.Controller]
20220509-20:42:36: [INFO]: Controller listening on port 33000for filenames to submit [edu.ucsb.nceas.mdqengine.Controller]
20220509-20:42:36: [INFO]: Waiting to establish connection to test client: [edu.ucsb.nceas.mdqengine.Controller]
```

Start metadig-worker in another terminal window, to separate controller and worker messages between the two windows.
```
$ ./bin/startWorker.sh &
[1]+ bin/startController.sh &
avatar:metadig-engine-develop slaughter$ bin/startWorker.sh
20220523-15:32:49: [INFO]: Set RabbitMQ host to: localhost [edu.ucsb.nceas.mdqengine.Worker:404]
20220523-15:32:49: [INFO]: Set RabbitMQ port to: 5672 [edu.ucsb.nceas.mdqengine.Worker:405]
20220523-15:32:50: [INFO]: Connected to RabbitMQ queue quality [edu.ucsb.nceas.mdqengine.Worker:418]
20220523-15:32:50: [INFO]: Waiting for messages. To exit press CTRL+C [edu.ucsb.nceas.mdqengine.Worker:419]
20220523-15:32:50: [INFO]: Connected to RabbitMQ queue completed [edu.ucsb.nceas.mdqengine.Worker:427]
20220523-15:32:50: [INFO]: Calling basicConsume [edu.ucsb.nceas.mdqengine.Worker:316]
```

Send a metadata document and system metadata to metadig-controller that is running in test mode. Because a parameter containing a port
number is passed by the script, metadig-controller will enter 'test' mode, so that it will read input from the port number. The default, non-test
behaviour of metadig-controller is to run inside a servlet engine.
```
$ bin/sendAssessmentTest.py
Number of arguments:  1
host: localhost
port: 33000
doi:10.18739_A2W08WG3R,./src/test/resources/test-docs/doi:10.18739_A2W08WG3R.sm,./src/test/resources/test-docs/doi:10.18739_A2W08WG3R.sm,arctic.data.center.suite.1,urn:node:ARCTIC
20220523-15:37:08: [INFO]: Running suite 'arctic.data.center.suite.1' for metadata pid doi:10.18739_A2W08WG3R [edu.ucsb.nceas.mdqengine.Worker:454]
20220523-15:37:08: [INFO]: Reading suites from: file:///opt/local/metadig/suites [edu.ucsb.nceas.mdqengine.store.InMemoryStore:73]
...

```

### Running on Kubernetes

See the *MetaDIG Engine Kubernetes Operations Manual* for information on how to deploy metadig-engine on Kubernetes.


## Testing metadig-engine {#running}

metadig-engine reads assessment suites, checks, configuration and data files from the metadig-engine working directory. The metadig-engine working directory is always located at /opt/local/metadig. When running metadig-engine is run locally, this directory is simply /opt/local/metadig on the local system. When running metadig-engine inside a Docker container, this directory is typically mapped to an external persistent volume. For example, /opt/local/metadig inside the container might be mapped to /data/metadig on the maching running Docker.

### Junit Tests

To run the metadig-engine unit tests, type the following command:

```
mvn surefire:test
```

## Troubleshooting metadig-engine

### Queue A Test Assessment Request

### Queue A Test Scorer Request

### k8s metadig-engine

For information regarding monitoring and troubleshooting metadig-engine running in the k8s environment, se"e Troubleshooting MetaDIG Engine Services" in the 
`MetaDIG Kubernetes Operations Manual`.

## metadig-engine PostgreSQL Database

Access to the metadig-postres database service is available to any pod running in the metadig k8s namespace. For debugging
purposes, the database can be accessed locally from a host in the k8s cluster (i.e. k8s-ctrl-1.dataone.org), if the appropriate kubectl context is set.
Access is not available from outside the k8s cluster. 

For example, if on a k8s cluster node, it is possible to use the PostgreSQL client program (if installed on the k8s host) to connect directly to the postgres 
pod/container that runs within the metadig-postgres pod:

```
addr=`kubectl get service metadig-postgres --namespace=metadig -o wide | grep 'metadig-postgres' | awk '{print $3}'`
psql -h $addr -p 5432 -U metadig metadig
```

Once connected, it is possible to inspect metadig database tables for debugging purposes.

In the same manner, the pgbouncer container:
```
addr=`kubectl get service metadig-postgres --namespace=metadig -o wide | grep 'metadig-postgres' | awk '{print $3}'`
psql -h $addr -p 6432 -U postgres pgbouncer
```

Once connected, pgbouncer commands can be used to inspect connection information regarding metadig-postgres client connections.

See https://www.pgbouncer.org/ for more information

### identifiers table

The `identifiers` table contains a record for every DataONE pid that has been processed by metadig-engine. Below is a sample of the
records in this table:

```
metadig=> select * from identifiers limit 5;
                  metadata_id                  |   data_source
-----------------------------------------------+------------------
 doi:10.18739/A2GM3V                           | urn:node:ARCTIC
 urn:uuid:97502dc9-0ede-4b38-aabe-f00e6be70d54 | urn:node:ARCTIC
 69f058e0edb7c3d63c861420f6cfce53              | urn:node:PANGAEA
 1f952192145a5dd031e05f80ad0a7c23              | urn:node:PANGAEA
 urn:uuid:a94edbe2-b19a-4e7c-8459-1b46c5a271bd | urn:node:KNB
(5 rows)
```

### runs table

The `runs` table is linked to the `identifiers` table and creates an entry for every assessment of a pid/suite combination.

```
metadig=> select metadata_id,suite_id,timestamp,error,status,sequence_id from runs limit 1;
           metadata_id            |     suite_id     |         timestamp          | results | error   | status  |           sequence_id
----------------------------------+------------------+----------------------------+---------+---------+----------------------------------
 062be19cc13c8ba9266530b55f63bc90 | FAIR-suite-0.3.0 | 2020-04-20 11:49:15.194+00 | ...     | success | 3e1bc7ba69d4e099467f0d4d7d8cedd2
(1 row)
```

### node_harvest table

The `node_harvest` table contains an entry for each harvest performed for a suite.


```
metadig=> select * from node_harvest;
       task_name        | task_type |         node_id         |  last_harvest_datetime
------------------------+-----------+-------------------------+--------------------------
 quality-dataone-fair   | quality   | urn:node:METAGRIL       | 2021-07-11T19:23:22.090Z
 quality-dataone-fair   | quality   | urn:node:PPBIO          | 2022-05-03T18:06:59.953Z
 quality-dataone-fair   | quality   | urn:node:CA_OPC         | 2022-04-27T02:12:00.593Z
 quality-dataone-fair   | quality   | urn:node:PISCO          | 2022-03-31T23:40:34.734Z
 portal-KNB-FAIR        | score     | urn:node:KNB            | 2022-04-11T16:45:52.041Z
 quality-dataone-fair   | quality   | urn:node:RW             | 2022-04-12T22:03:52.776Z
 portal-mnUCSB1-FAIR    | score     | urn:node:mnUCSB1        | 2022-05-03T17:56:22.346Z
 quality-dataone-fair   | quality   | urn:node:IEDA_USAP      | 2021-01-15T18:45:32.297Z
 quality-dataone-fair   | quality   | urn:node:IEDA_EARTHCHEM | 2021-01-18T00:40:57.404Z
 quality-dataone-fair   | quality   | urn:node:IEDA_MGDL      | 2021-01-18T04:36:48.839Z
 quality-dataone-fair   | quality   | urn:node:ARCTIC         | 2022-05-09T20:44:54.223Z
 quality-dataone-fair   | quality   | urn:node:ESS_DIVE       | 2022-05-10T02:05:12.775Z
 quality-ess-dive       | quality   | urn:node:ESS_DIVE       | 2022-02-24T18:52:07.764Z
 quality-dataone-fair   | quality   | urn:node:TFRI           | 2022-04-07T09:28:26.664Z
 quality-dataone-fair   | quality   | urn:node:ARM            | 2021-11-20T00:00:00.753Z
 quality-dataone-fair   | quality   | urn:node:ESA            | 2015-01-06T12:37:08.039Z
 quality-dataone-fair   | quality   | urn:node:SANPARKS       | 2015-01-06T12:58:51.157Z
 quality-dataone-fair   | quality   | urn:node:ONEShare       | 2014-06-24T23:00:08.961Z
 quality-dataone-fair   | quality   | urn:node:GOA            | 2019-03-01T00:11:14.007Z
 quality-dataone-fair   | quality   | urn:node:LTER_EUROPE    | 2016-01-14T09:06:57.814Z
 quality-dataone-fair   | quality   | urn:node:IOE            | 2016-06-15T21:35:12.872Z
 quality-dataone-fair   | quality   | urn:node:TERN           | 2019-06-21T07:13:58.711Z
 quality-dataone-fair   | quality   | urn:node:NKN            | 2016-06-22T15:02:50.462Z
 quality-dataone-fair   | quality   | urn:node:NRDC           | 2018-04-06T18:37:01.89Z
 quality-dataone-fair   | quality   | urn:node:NCEI           | 2018-05-10T03:28:11.936Z
 quality-dataone-fair   | quality   | urn:node:NEON           | 2019-12-01T19:38:06.575Z
 quality-dataone-fair   | quality   | urn:node:GRIIDC         | 2020-02-28T03:06:02.436Z
 quality-dataone-fair   | quality   | urn:node:R2R            | 2017-01-26T02:21:39.358Z
 quality-dataone-fair   | quality   | urn:node:CAS_CERN       | 2018-10-25T03:06:22.3Z
 quality-dataone-fair   | quality   | urn:node:PNDB           | 2021-05-25T11:17:02.989Z
 quality-knb            | quality   | urn:node:KNB            | 2022-05-07T00:01:15.052Z
 quality-dataone-fair   | quality   | urn:node:KNB            | 2022-05-07T00:01:15.052Z
 quality-dataone-fair   | quality   | urn:node:LTER           | 2022-05-09T14:45:08.956Z
 portal-ARCTIC-FAIR     | score     | urn:node:ARCTIC         | 2022-05-09T20:48:49.465Z
 quality-dataone-fair   | quality   | urn:node:FEMC           | 2022-04-30T03:00:07.033Z
 quality-dataone-fair   | quality   | urn:node:EDI            | 2022-05-03T03:45:16.425Z
 quality-ess-dive-1.1.0 | quality   | urn:node:ESS_DIVE       | 2022-05-07T06:00:22.705Z
 quality-arctic         | quality   | urn:node:ARCTIC         | 2022-05-09T20:44:54.223Z
(38 rows)

```

### nodes table

The `nodes` table is derived from the DataONE CN `listNodes` service. This table contains recent information about every node
that is registered with the DataONE CN.



```
metadig=> select * from nodes limit 5;
 identifier     | name                     | type | state | synchronize | last_harvest             | baseurl
 ---------------+--------------------------+------+-------+-------------+--------------------------+-----------------------------------------
 urn:node:KNB   | KNB Data Repository      | MN   | UP    | t           | 2022-05-09T17:11:38.103Z | https://knb.ecoinformatics.org/knb/d1/mn
 urn:node:LTER  | U.S. LTER Network        | MN   | UP    | t           | 2022-05-09T14:45:11.228Z | https://gmn.lternet.edu/mn
 urn:node:CDL   | UC3 Merritt              | MN   | UP    | t           | 2014-12-11T23:12:03.884Z | https://merritt-aws.cdlib.org:8084/mn
 urn:node:PISCO | PISCO MN                 | MN   | UP    | t           | 2022-03-31T23:41:08.365Z | https://data.piscoweb.org/catalog/d1/mn
 urn:node:DRYAD | Dryad Digital Repository | MN   | DOWN  | t           | 2018-09-18T03:54:10.492Z | https://datadryad.org/mn
(5 rows)
```

### filestore table

```
metadig=> select file_id,pid,suite_id,storage_type,media_type,alt_filename from filestore limit 5;
               file_id                  | pid                   |     suite_id     | storage_type | media_type  | alt_filename
----------------------------------------+-----------------------+------------------+--------------+-------------+----------------------------------
 ac6e4e1a-fff5-4f27-aed7-42d4fdf4c674   |                       |                  | code         | text/x-rsrc | graph_cumulative_quality_scores.R
 dfcac5cb-945b-4bdd-b25f-8c5140e626cb   | urn:uuid:35f4c58e-... | FAIR-suite-0.3.1 | graph        | image/png   |
 2f02381e-fda1-48cd-9ba1-9beb45e280e2   | urn:uuid:35f4c58e-... | FAIR-suite-0.3.1 | data         | text/csv    |
 1a54e6ea-991b-44a8-89d0-ef92411198fa   | urn:uuid:77fc14cc-... | FAIR-suite-0.3.1 | graph        | image/png   |
 dfb6f42a-72e2-4a5a-ae1f-3:wcf23456f06e | urn:uuid:77fc14cc-... | FAIR-suite-0.3.1 | data         | text/csv    |
(5 rows)
```

## metadig-engine Solr index

Here is an example row returned from the metadig-engine Solr index in responds to a Solr query. Currently this index is used internally by metadig-engine, but is not available outside of k8s. It may be used in the future by client programs to retrieve data and disply graphics.

```
 /usr/bin/curl 'http://10.96.136.9:8983/solr/quality/select?q=suiteId:FAIR-suite-0.3.1+datasource:urn\:node\:ARCTIC&q.op=AND&sort=timestamp+desc&rows=1'
{
  "responseHeader":{
    "status":0,
    "QTime":1,
    "params":{
      "q":"suiteId:FAIR-suite-0.3.1 datasource:urn\\:node\\:ARCTIC",
      "q.op":"AND",
      "sort":"timestamp desc",
      "rows":"1"}},
  "response":{"numFound":31157,"start":0,"numFoundExact":true,"docs":[
      {
        "metadataId":"urn:uuid:b12d7a2e-f9be-49ee-8048-b2fb11d6f9bf",
        "formatId":"https://nceas.ucsb.edu/mdqe/v1",
        "runId":"670a86f0-8d25-40c2-8d8c-22218090075d",
        "suiteId":"FAIR-suite-0.3.1",
        "timestamp":"2022-05-26T13:22:21.725Z",
        "datasource":"urn:node:ARCTIC",
        "metadataFormatId":"https://eml.ecoinformatics.org/eml-2.2.0",
        "dateUploaded":"2022-05-26T13:18:02.874Z",
        "obsoletes":"urn:uuid:6211cdaa-4a77-4438-ab49-2daf5a7b8658",
        "rightsHolder":"http://orcid.org/0000-0001-5398-4478",
        "checksPassed":35,
        "checksWarned":9,
        "checksFailed":7,
        "checksInfo":0,
        "checksErrored":0,
        "checkCount":51,
        "scoreOverall":0.8333333,
        "scoreByType_Interoperable_f":1.0,
        "scoreByType_Reusable_f":0.64,
        "scoreByType_Accessible_f":0.62,
        "scoreByType_Findable_f":1.0,
        "_version_":1733895223714512896}]
  }}

```

The associated Solr schema file can be found at ./helm/metadig-solr/quality/conf/schema.xml in the metadig-engine repository.

Note that the Solr fields `scoreByType*` are dynamic, so these field names will vary based on the assessment check categories of each assessment suite.

## metadig-scheduler operation

### MN metadata harvesting

Several DataONE MNs have custom assessment suites, that are different than the default suite running on the CN. THe current suites in use are:


| node id  | suite id    |
|----------|-------------|
| KNB      - knb.suite.1 |
| ARCTIC   | arctic.data.center.suite.1 |
| ESS_DIVE | ess-dive.data.center.suite-1.1.0 |
| CN       | FAIR-suite-0.3.1 |

Because all DataONE content is assessed via the CN harvesting task (for supported metadata document formats), it is only necessary to add an
MN harvesting task to the metadig-scheduler task file if a different suite needs to be assessed for the content of that MN. For example, the
task for the Arctic Data Center suite is shown below:

```
quality,quality-arctic,metadig,5 0/1 * * * ?,"^eml.*|^http.*eml.*;arctic.data.center.suite.1;urn:node:ARCTIC;2022-04-01T00:00:00.00Z;1;1000"
```
This tasks causes harvesting to be performed every minute (5 seconds after the start), for metadata with sysmeta formatIds matching the regular expression 
`^eml.*|^http.*eml.*``.

See the `operations-manual.md` for more information about the metadig-scheduler task list file.

### CN metadata harvesting

The information from the `nodes` table is used for the `CN` metadig metadata harvest to determine the queries that are
performed against the CN `object` store. A query is performed for each node that is currently in the `UP` state, and has a `last_harvest`
date within the last month.:w


The production CN metadaig scheduler task entry is:

```
quality,quality-dataone-fair,metadig,10 0/1 * * * ?,"^eml.*|^http.*eml.*|.*www.isotc211.org.*;FAIR-suite-0.3.1;urn:node:CN;2010-00-01T00:00:00.00Z;1;1000"
```

This task entry tells metadig-scheduler to perform a CN harvest 10 seconds after the start of every minute. Metadata records with formatIds that match the
regular expression `^eml.*|^http.*eml.*|.*www.isotc211.org.*` are selected for assessment. For each MN in the `nodes` table:

- if the node is 'UP' and has a recent `last_harvest` it is queried
- if the node has an entry in the `node_harvest` table, 
  - the query begin date from `last_harvest_datetime` is used
  - the query end date is the current time
- else if the the node does not have an entry in the `node_harvest` table
    - the harvest start time from the metadig-scheduler task list is used 
- all pids with a system modified date within the specified date range are selected (from the CN `listObjects` service)
- pids that do not match the desired formatIds are filtered out
- all matching pids are queued for assessment
- when the assessment is complete, an entry is made in the `node_harvest` entry with the `last_harvest_datetime` set to most recent system metadata modified datetime from all the pids processed

## metadig-engine Assessment Algorithm

Here is an explaination of the 'total score' algorithm (formula).

These are the possible 'level' values: INFO, METADATA, OPTIONAL, REQUIRED
These are the possible 'status' values: SUCCESS, FAILURE, ERROR (SKIP is not longer used)

For the algorithm:
    Tpass = is the count of all checks with SUCCESS status (includes REQUIRED and OPTIONAL, NOT INFO or METADATA)
    Rpass = is the count of REQUIRED checks with SUCCESS status
    Opass = is the count of OPTIONAL checks with SUCCESS status
    Ofail = is the count of OPTIONAL checks with FAILURE or ERROR status
    Rfail = is the count of REQUIRED checks with FAILURE or ERROR status

In documentation and papers regarding MetaDIG, FAIR, etc, the formula that is shown:

         Rpass + Opass
     ---------------------
     Rpass + Rfail + Opass

Here is the algorithm that is actually used by MetaDIG, shown by the bean 'mdq.scores.overal',
which is essentially:

         Tpass
     -------------
     Tpass + Rfail

Note that these two formulas are equivalent, and that implicitly Ofail is not counted, as it does not
appear in the denominator. Therefore, the scoring counts optional checks that pass, but doesn't count
optional checks that fail.
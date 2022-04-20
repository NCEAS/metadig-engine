
# Metadata Assessment

The MetaDIG (Metadata Improvement and Guidance) project was funded for metadata improvement advocacy, the development of metadata evaluation criteria and the development of metadata assessment tools. This repository contains a metadata assessment tool, the MetaDIG engine, aka metadig-engine. The metadig-engine assesses a metadata document to see how closely it adheres to a suite of checks. Each assessment check inspects the contents of one or more elements from the metadata document. From this assessment process, a report is generated that describes if each check's criteria are met or not.

## MetaDIG Assessment Engine (metadig-engine)

## Building metadig-engine

The following Maven command performas a complete build of metadig engine. Each Maven target will be explained separately

```
mvn clean package docker:build docker:push
```

- mvn clean
The previous build is removed in preparation for a new build.

- mvn package
All source code is compiled and the metadig-engine jar file is built.

- mvn install
The metadig-engine deliverables are copied to the local Maven repository (i.e. ~/.m2/repository). This step makes the deliverables available to other packages that may require them. For example, the [metadig-webapp](https://github.com/NCEAS/metadig-webapp) componenet requires metadig-engine for builds and can obtain them from the local Mavin repository.

## metadig-engine Dependancies
### metadig-r

metadig-engine assessment checks that are written in the R programming language can use the [metadig-r](https://github.com/NCEAS/metadig-r) R package, which contains functions that perform common assessment tasks. The metadig-r package is made available to metadig-engine via an R source package, for example 'metadig_0.2.0.tar.gz`. This file is used during the building of the metadig-worker Docker image, which is described later in this document. Additional information on building and using the metadig-r R package is available from the [github repo](https://github.com/NCEAS/metadig-r).

### metadig-py

The metadig-py component is a Python package that assists in the authoring of metadig-engine checks written in Python. metadig-py is made available to metadig-engine by building the Python package from the [metadig-py](https://github.com/NCEAS/metadig-py) repository, then placing the `metadig-py` directory in the metadig-engine working directory, which is described in [Running metadig-engine](#running)

### metadig-checks

Metadata assessment checks assoicated with this project can be found at https://github.com/NCEAS/metadig-checks. The metadig-checks distribution is built from that repository, then deployed to the metadig-engine working directory, as described in that repository.

### RabbitMQ

## Deploying metadig-engine as Web Service

metadig-engine can be run 

## Building metadig-engine Docker images

Docker images are built for metadig-engine services and posted to https://hub.docker.com/r/metadig. Once the
software has been build, the Docker images can be build and distributed using the following command:

```
mvn docker:build docker:push

```

It is necessary to use Docker login credentials in order to push images to Docker Hub. The username and
password for the Docker Hub account are available in the NCEAS Keybase system.

Writing checks

using subselectors
- for each main selector, the subselector is evaluated
- if multiple values returned, they will be returned as a list
- if a subselector value is not returned for the corresponding selector, then that array value is set to None

## Running metadig-engine {#running}

metadig-engine reads assessment suites, checks, configuration and data files from the metadig-engine working directory. The metadig-engine working directory is always located at /opt/local/metadig. When running metadig-engine is run locally, this directory is simply /opt/local/metadig on the local system. When running metadig-engine inside a Docker container, this directory is typically mapped to an external persistent volume. For example, /opt/local/metadig inside the container might be mapped to /data/metadig on the maching running Docker.

### Running Locally

The metadig-engine assessment function can be called directly, without using metadig-controller and metadig-worker. This is 
done by calling the metadig-engine main Java class from the metadig-engine development working directory:

doc="${workDir}/test/ADC/doi:10.18739_A2W08WG3R.xml"
sysmeta="${workDir}/test/ADC/doi:10.18739_A2W08WG3R.sm"

java -cp ./target/metadig-engine-${version}.jar edu.ucsb.nceas.mdqengine.MDQEngine $suite $doc $sysmeta

Edit metadig.properties

### Running Locally with RabbitMQ queing

The RabbitMQ is an open source messge brokering system that can be used to queue assessment requests for metadig-engine. The use of RabbitMQ with metadig-engine is intended to be run on a k8s deployment, however, RabbitMQ can be used on the local machine for development testing.

RabbitMQ can be installed easily on MacOS, for example, using [Homebrew](https://www.rabbitmq.com/install-homebrew.html). 

### Running on Kubernetes

See the *MetaDIG Engine Kubernetes Operations Manual* for information on how to deploy metadig-engine on Kubernetes.

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



# Use an OpenJDK runtime as a parent image
FROM eclipse-temurin:8u372-b07-jre-jammy
# 9-jre does not run with .Worker
#FROM openjdk:9-jre

LABEL org.opencontainers.image.source="https://github.com/nceas/metadig-engine"
LABEL org.opencontainers.image.title="metadig-engine worker"
LABEL org.opencontainers.image.version="1.0.1"
LABEL maintainer="Jeanette Clark jclark@nceas.ucsb.edu"

# Set the working directory 
WORKDIR /var/lib/metadig

ARG TAG=2.0.0
ENV TAG=${TAG}

# This file was created from the https://github.com/NCEAS/metadig-r repo
# and contains R functions that assist in writing R based quality checks.
COPY Docker/metadig-worker/metadig_0.2.0.tar.gz metadig.tar.gz
#COPY log4j.properties .
# The most recently built jar file is copied from the maven build directory to this dir by maven, so that
# it can be copyied to the image.
COPY target/metadig-engine-$TAG.jar metadig-engine.jar
# For some reason, the DataONE indexer software can't find these files unless they are put in
# directory and included in the CLASSPATH (see java CMD). They are in the source tree under 'main/resources',
# but maybe further action needs to be taken for java/dataone to be able to find them without having to
# do this extra step.
COPY Docker/metadig-worker/solr .

# Add R runtime and install packges required by the quality suites
COPY Docker/metadig-worker/r-cmds.txt .

RUN apt-get update
RUN apt-get install -y g++ r-base r-base-dev r-base-core r-doc-html libc6-dev libssl-dev libxml2 libxml2-dev

RUN echo 'options(repos = c(CRAN = "http://cran.rstudio.com"))' >> /usr/lib/R/etc/Rprofile.site
RUN Rscript --vanilla r-cmds.txt

# DataONE indexer prints copious error msgs if these files don't exist
RUN mkdir -p /etc/dataone/index && touch /etc/dataone/index/d1client.properties && touch /etc/dataone/node.properties && touch /etc/dataone/index/cn-index-processor.properties

# Run the Worker process
# Note: docker --buile-arg only allows one argument (one token only, multiple tokens inside quotes doesn't work, so have
#   to specify java options directly on command line.
# Set classpath to include /opt/local/metadig/log4j.properties, if it exists, so that logging can be changed without
# having to rebuild the container. Note that on k8s, this dir is mapped to the persistent volume, so will be /data/metadig/log4j.properties
CMD java -Dlog4j2.formatMsgNoLookups=true -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:+UseSerialGC -cp /opt/local/metadig/config:./metadig-engine.jar:./solr -Dpython.path=/opt/local/metadig/metadig-py edu.ucsb.nceas.mdqengine.Worker


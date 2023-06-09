# Use an OpenJDK runtime as a parent image
FROM openjdk:8-jre-alpine 

LABEL org.opencontainers.image.source="https://github.com/nceas/metadig-engine"
LABEL org.opencontainers.image.title="metadig-engine scheduler"
LABEL org.opencontainers.image.version="2.4.1"
LABEL maintainer="Jeanette Clark jclark@nceas.ucsb.edu"

ARG TAG=2.0.0
ENV TAG=${TAG}

# Set the working directory 
WORKDIR /var/lib/metadig

# The most recently built jar file is copied from the maven build directory to this dir by maven, so that
# it can be copied to the image.
COPY target/metadig-engine-$TAG.jar .

# Set classpath to include /opt/local/metadig/log4j.properties, if it exists, so that logging can be changed without
# having to rebuild the container. Note that on k8s, this dir is mapped to the persistent volume, so will be /data/metadig/log4j.properties
CMD java -Dlog4j2.formatMsgNoLookups=true -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:+UseSerialGC -cp /opt/local/metadig/config:./metadig-engine.jar: edu.ucsb.nceas.mdqengine.scheduler.JobScheduler

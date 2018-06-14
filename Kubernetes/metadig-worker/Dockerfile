# Use an OpenJDK runtime as a parent image
#FROM openjdk:8
# 8-jre-alpine runs mdqengine.Worker correctly
FROM openjdk:8-jre-alpine 
# 9-jre does not run with .Worker
#FROM openjdk:9-jre

MAINTAINER slaughter@nceas.ucsb.edu

#ARG JAVA_OPTIONS "-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap"

# Set the working directory 
WORKDIR /var/lib/metadig

COPY metadig-engine.jar .
COPY log4j.properties .
COPY solr ./solr
# This file was created from the https://github.com/NCEAS/metadig-r repo
# and contains R functions that assist in writing R based quality checks.
COPY metadig_0.2.0.tar.gz ./metadig.tar.gz

# DataONE indexer prints copious error msgs if these files don't exist
RUN mkdir -p /etc/dataone/index && touch /etc/dataone/index/d1client.properties && touch /etc/dataone/node.properties

# Add R runtime and install packges required by the quality suites
COPY ./r-cmds.txt r-cmds.txt
RUN apk update
RUN apk add g++ R R-dev R-doc libc-dev openssl-dev libxml2 libxml2-dev
RUN echo 'options(repos = c(CRAN = "https://cran.cnr.berkeley.edu/"))' >> /usr/lib/R/etc/Rprofile.site
RUN Rscript --vanilla r-cmds.txt

# Run the Worker process
# Note: docker --buile-arg only allows one argument (one token only, multiple tokens inside quotes doesn't work, so have
#   to specify java options directly on command line.
CMD java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:+UseSerialGC -cp ./metadig-engine.jar:./solr edu.ucsb.nceas.mdqengine.Worker
#CMD java -Xms128m -Xmx256m -Dlog4j.configuration=log4j.properties -cp ./metadig-engine.jar:./solr edu.ucsb.nceas.mdqengine.Worker
#CMD java ${JAVA_OPTIONS} -cp ./metadig-engine.jar edu.ucsb.nceas.mdqengine.Worker


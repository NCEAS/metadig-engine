# Use an OpenJDK runtime as a parent image
FROM openjdk:8
#FROM openjdk:8-jre-alpine 

MAINTAINER slaughter@nceas.ucsb.edu

#ARG JAVA_OPTIONS "-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap"

# Set the working directory 
WORKDIR /var/lib/metadig

ADD metadig-engine.jar .
ADD log4j.properties .
ADD solr ./solr

# Run the Worker process
# Note: docker --buile-arg only allows one argument (one token only, multiple tokens inside quotes doesn't work, so have
#   to specify java options directly on command line.
#CMD java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -cp ./metadig-engine.jar edu.ucsb.nceas.mdqengine.Worker
CMD java -Xms128m -Xmx256m -Dlog4j.configuration=log4j.properties -cp ./metadig-engine.jar:./solr edu.ucsb.nceas.mdqengine.Worker
#CMD java ${JAVA_OPTIONS} -cp ./metadig-engine.jar edu.ucsb.nceas.mdqengine.Worker

#java -cp ./target/metadig-engine-1.1.3-SNAPSHOT.jar:./target/classes/solr edu.ucsb.nceas.mdqengine.Worker


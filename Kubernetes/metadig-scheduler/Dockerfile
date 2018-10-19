# Use an OpenJDK runtime as a parent image
FROM openjdk:8-jre-alpine 

MAINTAINER slaughter@nceas.ucsb.edu

# Set the working directory 
WORKDIR /var/lib/metadig

COPY log4j.properties .
# The most recently built jar file is copied from the maven build directory to this dir by maven, so that
# it can be copyied to the image.
COPY metadig-engine.jar metadig-engine.jar
COPY metadig.properties /etc/metadig/metadig.properties
COPY taskList.csv /etc/metadig/taskList.csv

# Run the MetaDIG Engine Scheduler process
CMD java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:+UseSerialGC -cp ./metadig-engine.jar:./solr edu.ucsb.nceas.mdqengine.scheduler.JobScheduler


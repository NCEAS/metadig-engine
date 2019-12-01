# Use an OpenJDK runtime as a parent image
FROM openjdk:8-jre-alpine 

MAINTAINER slaughter@nceas.ucsb.edu

# Set the working directory 
WORKDIR /var/lib/metadig

COPY log4j.properties .
# The most recently built jar file is copied from the maven build directory to this dir by maven, so that
# it can be copied to the image.
COPY metadig-engine.jar metadig-engine.jar

#COPY metadig.properties /etc/metadig/metadig.properties
#COPY taskList.csv /etc/metadig/taskList.csv
COPY log4j.properties .

#COPY run.sh run.sh
# The 'run.sh' script copies config files that should be available from persistent volume to the standard location where the software
# will access them, and then starts the Java 'JobScheduler' class.

# Run the MetaDIG Engine Scheduler process
#CMD java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:+UseSerialGC -cp ./metadig-engine.jar:./solr edu.ucsb.nceas.mdqengine.scheduler.JobScheduler

#CMD [ "./run.sh" ]
CMD java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:+UseSerialGC -cp ./metadig-engine.jar: edu.ucsb.nceas.mdqengine.scheduler.JobScheduler

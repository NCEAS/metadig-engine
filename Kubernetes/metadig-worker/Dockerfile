# Use an OpenJDK runtime as a parent image
FROM openjdk:8

MAINTAINER slaughter@nceas.ucsb.edu

# Set the working directory to /app
WORKDIR /

ADD metadig-engine.jar .

# Run the Worker process
CMD java -cp ./metadig-engine.jar edu.ucsb.nceas.mdqengine.Worker


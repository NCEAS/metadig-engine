# Use an OpenJDK runtime as a parent image
FROM openjdk:8

# Set the working directory to /app
WORKDIR /

ADD metadig-engine.jar .

# Copy the current directory contents into the container at /app
ADD metadig.properties config/metadig.properties

# Run the Worker process
CMD java -cp ./metadig-engine.jar edu.ucsb.nceas.mdqengine.Worker


# Use an OpenJDK runtime as a parent image
FROM openjdk:8

# Set the working directory to /app
WORKDIR /

ADD metadig-engine-1.1.3-SNAPSHOT.jar .

# Copy the current directory contents into the container at /app
ADD metadig.properties config/metadig.properties

# Run the Worker process
CMD java -cp ./metadig-engine-1.1.3-SNAPSHOT.jar edu.ucsb.nceas.mdqengine.Worker


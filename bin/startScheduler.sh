#!/bin/bash

# Start a local copy of metadig-scheduler for RabbitMQ based development testing.

${JAVA_HOME}/bin/java -cp /opt/local/metadig/config:./target/metadig-engine-2.4.0.jar edu.ucsb.nceas.mdqengine.scheduler.JobScheduler



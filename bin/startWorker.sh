#!/bin/bash

# Start a local copy of metadig-worker for RabbitMQ based development testing.
version=3.0.0-SNAPSHOT

java -cp /opt/local/metadig/config:./target/metadig-engine-${version}.jar:./target/classes/solr edu.ucsb.nceas.mdqengine.Worker


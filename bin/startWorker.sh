#!/bin/bash

# Start a local copy of metadig-worker for RabbitMQ based development testing.
version=2.4.1

# Include the MetaDIG python library
export JYTHONPATH=/opt/local/metadig/metadig-py

java -cp /opt/local/metadig/config:./target/metadig-engine-${version}.jar:./target/classes/solr -Dpython.path=JYTHONPATH edu.ucsb.nceas.mdqengine.Worker


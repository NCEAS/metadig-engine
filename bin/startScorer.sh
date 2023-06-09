#!/bin/bash


# Start a local copy of metadig-scorer for RabbitMQ based development testing.
version=2.4.1-SNAPSHOT

# Include the MetaDIG python library
#export JYTHONPATH=/Users/slaughter/git/NCEAS/metadig-py

java -cp /opt/local/metadig/config:./target/metadig-engine-${version}.jar:./target/classes/solr edu.ucsb.nceas.mdqengine.scorer.Scorer


#!/bin/bash

#mvn exec:java -Dexec.mainClass="edu.ucsb.nceas.metadig.Worker" -Dexec.args="hi there"

java -cp ./target/metadig-engine-1.1.3-SNAPSHOT.jar:./target/classes/solr edu.ucsb.nceas.mdqengine.Worker

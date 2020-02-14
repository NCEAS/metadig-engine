#!/bin/bash

version=2.2.0
#mvn exec:java -Dexec.mainClass="edu.ucsb.nceas.metadig.Controller" -Dexec.args="hi there"

# Use first argument if sending jobs via port, for testing, for example:
# java -cp ./target/metadig-engine-1.1.3-SNAPSHOT.jar edu.ucsb.nceas.mdqengine.Controller 3300*
java -cp ./target/metadig-engine-${version}.jar edu.ucsb.nceas.mdqengine.Controller $*


#!/bin/bash

version=2.1.0
#mvn exec:java -Dexec.mainClass="edu.ucsb.nceas.metadig.Worker" -Dexec.args="hi there"

# Include the MetaDIG python library
export JYTHONPATH=/Users/slaughter/git/NCEAS/metadig-py

java -cp ./target/metadig-engine-${version}.jar:./target/classes/solr edu.ucsb.nceas.mdqengine.Worker

#echo "Running Worker with profiler"

# Profile without stack telemetry
#java -cp ./target/metadig-engine-2.0.2.jar:./target/classes/solr \
#-agentpath:/Applications/YourKit-Java-Profiler-2019.1.app/Contents/Resources/bin/mac/libyjpagent.jnilib=disablestacktelemetry,exceptions=disable,delay=10000 \
#edu.ucsb.nceas.mdqengine.Worker

# profile with stack telemetry
#java -cp ./target/metadig-engine-2.0.2.jar:./target/classes/solr \
#-agentpath:/Applications/YourKit-Java-Profiler-2019.1.app/Contents/Resources/bin/mac/libyjpagent.jnilib=exceptions=disable,delay=10000 \
#edu.ucsb.nceas.mdqengine.Worker


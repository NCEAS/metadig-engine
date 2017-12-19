#!/bin/bash

#mvn exec:java -Dexec.mainClass="edu.ucsb.nceas.metadig.Worker" -Dexec.args="hi there"

java -cp ./target/mdqengine-1.1.2-SNAPSHOT.jar edu.ucsb.nceas.mdqengine.Worker

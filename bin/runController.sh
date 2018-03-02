#!/bin/bash

#mvn exec:java -Dexec.mainClass="edu.ucsb.nceas.metadig.Controller" -Dexec.args="hi there"

java -cp ./target/metadig-engine-1.1.3-SNAPSHOT.jar edu.ucsb.nceas.mdqengine.Controller

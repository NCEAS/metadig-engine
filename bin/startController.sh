#!/bin/bash

version=3.0.0-SNAPSHOT

# Use first argument if sending jobs via port, for testing, for example:
java -cp /opt/local/metadig/config:./target/metadig-engine-${version}.jar edu.ucsb.nceas.mdqengine.Controller 33000


#!/bin/bash

version=2.5.0-SNAPSHOT

# Use first argument if sending jobs via port, for testing, for example:
java -cp /opt/local/metadig/config:./target/metadig-engine-${version}.jar edu.ucsb.nceas.mdqengine.Controller 33000


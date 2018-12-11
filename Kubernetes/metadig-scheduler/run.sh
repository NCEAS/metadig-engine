#!/bin/sh

mkdir -p /etc/metadig
cp /opt/local/metadig/metadig.properties /etc/metadig
cp /opt/local/metadig/taskList.csv /etc/metadig
touch /tmp/done

java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:+UseSerialGC -cp ./metadig-engine.jar:./solr edu.ucsb.nceas.mdqengine.scheduler.JobScheduler

#!/bin/bash


# Upgrade a lucense 7.3 core to 8.11.0
java -cp /usr/local/solr-8.11.1/server/solr-webapp/webapp/WEB-INF/lib/lucene-core-8.11.1.jar:/usr/local/solr-8.11.1/server/solr-webapp/webapp/WEB-INF/lib/lucene-backward-codecs-8.11.1.jar org.apache.lucene.index.IndexUpgrader \
  -delete-prior-commits \
  -verbose \
  ./data/quality/data/index



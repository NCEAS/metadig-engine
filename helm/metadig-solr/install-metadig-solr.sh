#!/bin/bash

# From https://github.com/bitnami/charts/tree/master/bitnami/solr/#installing-the-chart

#helm repo add bitnami https://charts.bitnami.com/bitnami

helm upgrade metadig-solr bitnami/solr \
  --version=9.6.8 \
  --namespace metadig \
  -f solr-values.yaml
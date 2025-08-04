#!/bin/bash

# From https://github.com/bitnami/charts/tree/master/bitnami/solr/#installing-the-chart

#helm repo add bitnami https://charts.bitnami.com/bitnami

helm install metadig-solr bitnamilegacy/solr \
  --version=3.0.3 \
  --namespace metadig \
  -f solr-values.yaml
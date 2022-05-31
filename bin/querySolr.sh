#!/bin/bash

# This script sends a Solr query directly to the metadig-solr pod. Therefore, it must be run locally on a k8s node.
# Currently the metadig-solr service does not have an ingress to route traffic to/from the k8s internal network.

addr=`kubectl get svc metadig-solr --namespace=metadig -o wide | grep metadig-solr | awk '{print $3}'`

# Retrieve Solr document for ESS_DIVE pids
/usr/bin/curl "http://${addr}:8983/solr/quality/select?q=suiteId:FAIR-suite-0.3.1+datasource:urn\:node\:ESS_DIVE&q.op=AND&rows=10&sort=timestamp+desc"

#
# Check runs with "errored" (internal errors) checks
# /usr/bin/curl "http://${addr}:8983/solr/quality/select?q=suiteId:ess-dive.data.center.suite-1.1.0+datasource:urn\:node\:ESS_DIVE+checksErrored:\[1+TO+*\]&fl=metadataId,timestamp&q.op=AND&sort=timestamp+desc&rows=15"
#/usr/bin/curl "http://${addr}:8983/solr/quality/select?q=suiteId:FAIR-suite-0.3.1+datasource:urn\:node\:ESS_DIVE+checksErrored:\[1+TO+*\]&q.op=AND&sort=timestamp+desc"

# Check runs with no "errored" checks
#/usr/bin/curl "http://${addr}:8983/solr/quality/select?q=suiteId:ess-dive.data.center.suite-1.1.0+datasource:urn\:node\:ESS_DIVE+checksErrored:0&q.op=AND&sort=timestamp+desc"

#/usr/bin/curl "http://${addr}:8983/solr/quality/select?q=suiteId:ess-dive.data.center.suite-1.1.0&fl=scoreOverall,scoreFindable,scoreAccessible,scoreInteroperable,scoreReusable"

# Specific pids
#/usr/bin/curl "http://${addr}:8983/solr/quality/select?q=metadataId:ess-dive-f71af758b74e12a-20220503T124325067+suiteId:ess-dive.data.center.suite-1.1.0&q.op=AND&sort=timestamp+desc"

# When auth is enabled, (authUser and authPassword (helm) is set), credentials must be included for all requests, even queries
#/usr/bin/curl -u metadig:quality "http://${addr}:8983/solr/quality/select?q=*:*"

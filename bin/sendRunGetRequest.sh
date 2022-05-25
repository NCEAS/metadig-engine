#!/bin/bash

trap 'error_exit' ERR

error_exit() {
  echo "Exit due to an error."
  exit 1
}

pid=doi:10.18739/A2W08WG3R
#qualitySuite='FAIR-suite-0.3.1'
#qualitySuite="knb.suite.1"
qualitySuite="arctic.data.center.suite.1"

qualitySvc=https://api.test.dataone.org/quality/runs/${qualitySuite}/${pid}

echo "Using quality service endpoint ${qualitySvc}."

# Send the request to the metadig REST API
echo "curl -X GET --insecure $qualitySvc"
curl -X GET --insecure -H "Accept: application/xml" $qualitySvc

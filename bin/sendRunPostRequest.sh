
#!/bin/bash

trap 'error_exit' ERR

error_exit() {
  echo "Exit due to an error."
  exit 1
}

#qualitySuite='FAIR-suite-0.3.1'
#qualitySuite="knb.suite.1"
qualitySuite="arctic.data.center.suite.1"

#qualitySvc=https://localhost:8080/quality/suites/${qualitySuite}/run
qualitySvc=https://api.test.dataone.org/quality/suites/${qualitySuite}/run

echo "Using quality service endpoint ${qualitySvc}."

dataDir=./src/test/resources/test-docs/
pid=doi:10.18739/A2W08WG3R
# Transform illegal pidFile chars '/()#' to _ so that the dest pidFile is legal
fn=`echo $pid | sed 's#[/\(\")]#_#g'`
objFn=${dataDir}/${fn}.xml
metaFn=${dataDir}/${fn}.sm

echo "obj: $objFn"
echo "meta: $metaFn"

# Send the request to the metadig REST API
echo curl -X POST --insecure -F "priority=low" -F "document=@${objFn};type=application/xml" -F "systemMetadata=@${metaFn};type=application/xml" $qualitySvc
curl -X POST --insecure -F "priority=low" -F "document=@${objFn};type=application/xml" -F "systemMetadata=@${metaFn};type=application/xml" $qualitySvc

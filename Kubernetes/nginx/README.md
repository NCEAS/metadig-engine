# Nginx Ingress Controller Installation

One strategy for exposing k8s services outside of the cluster, i.e. make them available from http://docker-ucsb-1.test.dataone.org, is to use the Nginx ingress controller. The capabilities of Nginx are detailed [here](https://github.com/kubernetes/ingress-nginx). 

The instructions for deploying Nginx are [here](https://github.com/kubernetes/ingress-nginx/tree/master/deploy). These instructions handle several deployment environment (AWS, GCE, 'Bare meta', ...), with the current deployment strategy for `docker-ucsb-1.test.dataone.org` as 'bare meta' (i.e. locally, not on a hosted k8s platform).

The `download.sh` script creates a local copy of the manifest files, in case they need to be customized for the NCEAS cluster.

The `deploy.sh` script creates all the k8s resources from the local manifest files.

In addition, the deployment instructions don't include a manifest file to create the ingress resource associated with the Nginx ingress controller, so the file `ingress.yaml` is used to create this resource.
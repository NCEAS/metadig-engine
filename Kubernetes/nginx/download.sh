#!/bin/bash
# from https://github.com/kubernetes/ingress-nginx/blob/master/deploy/README.md.

wget https://raw.githubusercontent.com/kubernetes/ingress-nginx/master/deploy/namespace.yaml 
wget https://raw.githubusercontent.com/kubernetes/ingress-nginx/master/deploy/default-backend.yaml 
wget https://raw.githubusercontent.com/kubernetes/ingress-nginx/master/deploy/configmap.yaml 
wget https://raw.githubusercontent.com/kubernetes/ingress-nginx/master/deploy/tcp-services-configmap.yaml 
wget https://raw.githubusercontent.com/kubernetes/ingress-nginx/master/deploy/udp-services-configmap.yaml

wget https://raw.githubusercontent.com/kubernetes/ingress-nginx/master/deploy/rbac.yaml
wget https://raw.githubusercontent.com/kubernetes/ingress-nginx/master/deploy/with-rbac.yaml 

wget https://raw.githubusercontent.com/kubernetes/ingress-nginx/master/deploy/provider/baremetal/service-nodeport.yaml

wget https://raw.githubusercontent.com/kubernetes/ingress-nginx/master/docs/examples/http-svc.yaml

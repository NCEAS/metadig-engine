#!/bin/bash

# run initialConfig.sh before this script, but only once

kubectl apply -f metadig-http-backend.yaml
kubectl apply -f tcp-services-configmap.yaml
kubectl apply -f udp-services-configmap.yaml
kubectl apply -f metadig-ingress-controller-deploy.yaml
kubectl apply -f metadig-nginx-service.yaml
kubectl apply -f ingress.yaml

#!/bin/bash

# 'metadig' namespace already exists
#kubectl apply -f namespace.yaml
kubectl apply -f metadig-http-backend.yaml
kubectl apply -f configmap.yaml
kubectl apply -f tcp-services-configmap.yaml
kubectl apply -f udp-services-configmap.yaml
# 'metadig' rbac already setup
#kubectl apply -f rbac.yaml
#kubectl apply -f with-rbac.yaml
kubectl apply -f metadig-ingress-controller-deploy.yaml

kubectl apply -f metadig-nginx-service.yaml
kubectl apply -f ingress.yaml



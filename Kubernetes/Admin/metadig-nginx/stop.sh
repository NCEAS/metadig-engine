#!/bin/bash

# 'metadig' namespace already exists
kubectl delete -f metadig-http-backend.yaml
kubectl delete -f tcp-services-configmap.yaml
kubectl delete -f udp-services-configmap.yaml
kubectl delete -f metadig-ingress-controller-deploy.yaml
kubectl delete -f metadig-nginx-service.yaml
kubectl delete -f ingress.yaml

#!/bin/bash 
kubectl apply -f namespace.yaml
kubectl apply -f default-backend.yaml
kubectl apply -f configmap.yaml
kubectl apply -f tcp-services-configmap.yaml
kubectl apply -f udp-services-configmap.yaml

kubectl apply -f rbac.yaml 
kubectl apply -f with-rbac.yaml

kubectl apply -f service-nodeport.yaml
kubectl apply -f http-svc.yaml
kubectl apply -f ingress.yaml

#kubectl apply -f nginx-service.yaml

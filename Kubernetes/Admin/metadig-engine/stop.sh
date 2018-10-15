#!/bin/bash

kubectl delete -f metadig-worker.yaml
kubectl delete -f metadig-controller.yaml
kubectl delete -f rabbitmq.yaml

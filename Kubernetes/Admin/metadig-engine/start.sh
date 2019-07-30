#!/bin/bash

kubectl apply -f rabbitmq.yaml
# RabbitMQ should be started before the webapp or workers, as the
# controller in metadig-webapp/metadig-tomcat and metadig-worker both
# need to connect to RabbitMQ
sleep 20
kubectl apply -f metadig-worker.yaml
kubectl apply -f metadig-controller.yaml
#kubectl apply -f metadig-scheduler.yaml


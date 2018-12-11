#!/bin/bash
kubectl delete role metadig -n metadig
kubectl delete roleBinding metadig-binding -n metadig
kubectl config delete-context metadig-context

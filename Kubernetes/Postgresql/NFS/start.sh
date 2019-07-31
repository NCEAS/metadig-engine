#!/bin/bash

#pushd ../../PersistentVolume/NFS ; ./start.sh ; popd
kubectl create -f postgresql.yaml

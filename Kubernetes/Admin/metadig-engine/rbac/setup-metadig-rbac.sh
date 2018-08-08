#!/bin/bash
export CA_LOCATION=/etc/kubernetes/pki
kubectl create namespace metadig
kubectl create -f metadig-role.yaml 
kubectl create -f metadig-role-binding.yaml 

openssl genrsa -out metadig.key 2048
sudo openssl req -new -key metadig.key -out metadig.csr -subj "/CN=metadig/O=nceas"
sudo openssl x509 -req -in metadig.csr -CA ${CA_LOCATION}/ca.crt -CAkey ${CA_LOCATION}/ca.key -CAcreateserial -out metadig.crt -days 500

mkdir ~/.certs
cp metadig.crt ~/.certs
cp metadig.key ~/.certs 

kubectl config set-credentials metadig --client-certificate=~/.certs/metadig.crt  --client-key=~/.certs/metadig.key
kubectl config set-context metadig-context --cluster=kubernetes --namespace=metadig --user=metadig

kubectl auth can-i create pods -n metadig --as="metadig"
kubectl auth can-i delete pods -n metadig --as="metadig"
kubectl auth can-i expose service -n metadig --as="metadig"

#!/bin/sh

# Start the Docker registry to be used to store local images for reuse
# This command uses a local volume to store registry data
# And it uses Let's Encrypt to provide a secure connection for other hosts

# Add these when ready to configure Lets Encrypt
#docker run -d -p 443:5000 --name registry \
  #-e REGISTRY_HTTP_TLS_LETSENCRYPT_CACHEFILE=/etc/docker/registry/letsencrypt.json \
  #-e REGISTRY_HTTP_TLS_LETSENCRYPT_EMAIL=jones@nceas.ucsb.edu \
  #-e REGISTRY_HTTP_ADDR=0.0.0.0:5000 \
  #-e REGISTRY_HTTP_HOST=https://docker-ucsb-1.test.dataone.org \
docker run -d -p 5000:5000 --name registry \
  --restart=always \
  -v registry:/var/lib/registry \
  registry:2

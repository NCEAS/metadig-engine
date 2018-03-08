# Docker Registry

To efficiently build and deploy images for metadig across a large number of nodes, we
need an image registry to store the built images.  The `start-registry.sh` script in this directory
provides a command for starting an instance of the Docker image registry.

It is currently deployed only on localhost, and so can only be used on the host on which
it is deployed.  For production, we will want to deploy it over SSL on an accessible host to all
of our nodes.  Notes for setting up the registry this way with Let's Encrypt are included.

# Common commands

## Push an image to the repository

Grab an ubuntu image from DockerHub, retag it for localhost, and then push it to localhost:

```
docker pull ubuntu:16.04
docker image tag ubuntu localhost:5000/ubuntu:16.04
docker push localhost:5000/ubuntu:16.04
```

## Pull an image from the repository

Assuming you don't have an image yet, pull it from thre registry using:

```
docker push localhost:5000/ubuntu:16.04
```


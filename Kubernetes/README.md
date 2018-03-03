# Create a Local Kubernetes Cluster to Run MetaDIG Engine

The MetaDIG Quality Engine can be run inside a local Kubernetes cluster using the `minikube` facility. Running the Quality Engine in this manner can be useful for development and debugging as a local cluster can be easily started and accessed.

Instructions for installing `minikube` are [here](https://kubernetes.io/docs/tasks/tools/install-minikube)

The `metadig` Kubernetes application has the following structure

metadig app

- metadig-controller service
    - metadig-controller deployment
        - metadig-http container
        - metadig-tomcat container
        - rabbitmq servier container
- metadig-worker service
    - metadig-worker deployment
        - metadig-worker container

## Start a Local Cluster 

```/bin/bash
minikube start
```

## Create the required Docker containers
The `make` command will build all the required Docker images for the `metadig` app. Currently, these images are pushed to a private repository. Until the `NCEAS` Docker Hub repository is setup, `Makefile` can be edited and an appropriate Docker repository can be substituted for the value of `DOCKER_REPO`.

<font color="red">Note: building the required images is dependent on local git repositories for `NCEAS/metadig-webapp` and `NCEAS/metadig-engine` github repos. This will be improved in the future when development versions of the Java jar files for these projects are made available, or changing the configuration to use released versions of these jar files is made possible.
</font>

Use `make` to build all necessary images:

```/bin/bash
make
```
##  Create the required deployments and services

```/bin/bash
$ kubectl apply -f metadig-controller-claim0-pv.yaml
$ kubectl apply -f metadig-controller-deployment.yaml
$ kubectl apply -f metadig-controller-service.yaml
$ kubectl apply -f metadig-worker-deployment.yaml
$ kubectl apply -f metadig-worker-service.yaml
```


## Check status of the cluster
First check that all the `metadig` services have started:

```/bin/bash
$ kubectl get services -o wide
NAME                 TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)        AGE       SELECTOR
kubernetes           ClusterIP   10.96.0.1        <none>        443/TCP        2d        <none>
metadig-controller   NodePort    10.99.100.113    <none>        80:31761/TCP   2h        app=metadig-controller,tier=frontend
metadig-worker       ClusterIP   10.110.110.143   <none>        80/TCP         1h        app=metadig-worker,tier=backend
```

Ensure that `metadig-controlle` and `metadig-worker` appear.

Now check that all necessary pods are deployed:

```/bin/bash
$ kubectl get pods -o wide
NAME                                  READY     STATUS          RESTARTS   AGE       IP           NODE
metadig-controller-685b44cd6b-psltb   3/3       Running         0          1h        172.17.0.2   minikube
metadig-worker-57cd9f9f4-h9cwd        1/1       Running         0         53m       172.17.0.4   minikube
```

Both `metadig-controller*` and `metadig-worker*` pods should have a status of `Running`.

## Connect to the metadig-controller service via a browser
The `minikube` facility can proxy a connection to the `metadig-controller` service. To obtain the external url for the connection:

```/bin/bash
$ minikube service metadig-controller --url
http://192.168.99.103:30364
http://192.168.99.103:30625
http://192.168.99.103:32363
```

The `metadig-controller` service contains a pod composed of `metadig-httpd`, `metadig-tomcat` and `rabbitmq` (type `kubectl describe pod metadig-controller` for details). 

The first of these urls is for the connection to `metadig-httpd`. Enter this url in a browser to connect to the metadig-engine web application.

## Debugging the Cluster Configuration

```/bin/bash
$ kubectl get pods -o wide
NAME                                  READY     STATUS    RESTARTS   AGE       IP           NODE
metadig-controller-685b44cd6b-psltb   3/3       Running   0          2h        172.17.0.2   minikube
metadig-worker-57cd9f9f4-h9cwd        1/1       Running   20         1h        172.17.0.4   minikube
```
The command output shows that `metadig-controller-685b44cd6b-psltb` has all three of it's pods started, and `metadig-worker-57cd9f9f4-h9cwd` has it's one pod started.

To login to a container, enter the pod name, and optionally the container name as in the following command. (If the pod has multiple containers, then use the `-c` flag to login to the appropriate one:

```/bin/bash
$ kubectl exec -it metadig-controller-685b44cd6b-psltb -c rabbitmq -- /bin/bash
root@metadig-controller-5d769687b8-dw8tz:/# ps -aef
UID        PID  PPID  C STIME TTY          TIME CMD
rabbitmq     1     0  0 20:34 ?        00:00:00 /bin/sh /usr/lib/rabbitmq/bin/rabbitmq-server
...
```

To view logs for a particular pod:

```/bin/bash
kubectl logs metadig-worker-57cd9f9f4-h9cwd
```



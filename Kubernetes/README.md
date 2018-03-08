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
$ kubectl logs metadig-worker-57cd9f9f4-h9cwd
```

## Scale number of workers

The `metadig-worker` pod is in a separate k8s deployment so that it can be scaled independently from `metadig-controller`. To scale the number of pods up so that more report generation containers can be running simultaneously, use the command:

```/bin/bash
kubectl scale --replicas=3 deployment/metadig-worker
```

To view the results:

```/bin/bash
$ kubectl get pods
NAME                                  READY     STATUS             RESTARTS   AGE       IP           NODE
metadig-controller-7b9b5c788d-hjnx5   3/3       Running            0          1d        172.17.0.4   minikube
metadig-worker-86679b74bc-52l5z       1/1       Running            0          1d        172.17.0.5   minikube
metadig-worker-86679b74bc-gsp9v       1/1       Running            0          1d        172.17.0.7   minikube
metadig-worker-86679b74bc-kq47c       1/1       Running            0          1d        172.17.0.6   minikube
```


# Alternate approach to set up a kubernetes cluster with `kubeadm`

kubeadm is a utility for bootstrapping a fully configured k8s cluster.
Installation instructions:  https://kubernetes.io/docs/setup/independent/install-kubeadm/

## Prereqs: docker

From the docs:

    On each of your machines, install Docker. Version v1.12 is recommended,
    but v1.11, v1.13 and 17.03 are known to work as well. Versions 17.06+
    might work, but have not yet been tested and verified by the
    Kubernetes node team.

We've got `docker 17.12`, so hopefully it will work.

## Prereqs: firewall

```
for port in `echo "6443 2379 2380 10250 10251 10252 10255"`
do
        echo $port
        #ufw route allow from 128.111.54.64/26
done
```

## First install kubeadm and other tools

As root:

```
apt-get update && apt-get install -y apt-transport-https
curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add -
cat <<EOF >/etc/apt/sources.list.d/kubernetes.list
deb http://apt.kubernetes.io/ kubernetes-xenial main
EOF
apt-get update
apt-get install -y kubelet kubeadm kubectl
```

- be sure the machine is not using swap, as k8s isn't happy with that.
- See: https://serverfault.com/questions/881517/why-disable-swap-on-kubernetes

```
swapoff -a
sudo sed -i '/ swap / s/^\(.*\)$/#\1/g' /etc/fstab
```

## Then configure the cluster

As a regular user:

```
$ sudo kubeadm init --pod-network-cidr=192.168.0.0/16
[init] Using Kubernetes version: v1.9.3
[init] Using Authorization modes: [Node RBAC]
[preflight] Running pre-flight checks.
	[WARNING SystemVerification]: docker version is greater than the most recently validated version. Docker version: 17.12.1-ce. Max validated version: 17.03
	[WARNING FileExisting-crictl]: crictl not found in system path
[preflight] Starting the kubelet service
[certificates] Generated ca certificate and key.
[certificates] Generated apiserver certificate and key.
[certificates] apiserver serving cert is signed for DNS names [docker-ucsb-1 kubernetes kubernetes.default kubernetes.default.svc kubernetes.default.svc.cluster.local] and IPs [10.96.0.1 128.111.54.69]
[certificates] Generated apiserver-kubelet-client certificate and key.
[certificates] Generated sa key and public key.
[certificates] Generated front-proxy-ca certificate and key.
[certificates] Generated front-proxy-client certificate and key.
[certificates] Valid certificates and keys now exist in "/etc/kubernetes/pki"
[kubeconfig] Wrote KubeConfig file to disk: "admin.conf"
[kubeconfig] Wrote KubeConfig file to disk: "kubelet.conf"
[kubeconfig] Wrote KubeConfig file to disk: "controller-manager.conf"
[kubeconfig] Wrote KubeConfig file to disk: "scheduler.conf"
[controlplane] Wrote Static Pod manifest for component kube-apiserver to "/etc/kubernetes/manifests/kube-apiserver.yaml"
[controlplane] Wrote Static Pod manifest for component kube-controller-manager to "/etc/kubernetes/manifests/kube-controller-manager.yaml"
[controlplane] Wrote Static Pod manifest for component kube-scheduler to "/etc/kubernetes/manifests/kube-scheduler.yaml"
[etcd] Wrote Static Pod manifest for a local etcd instance to "/etc/kubernetes/manifests/etcd.yaml"
[init] Waiting for the kubelet to boot up the control plane as Static Pods from directory "/etc/kubernetes/manifests".
[init] This might take a minute or longer if the control plane images have to be pulled.
[apiclient] All control plane components are healthy after 29.003756 seconds
[uploadconfig] Storing the configuration used in ConfigMap "kubeadm-config" in the "kube-system" Namespace
[markmaster] Will mark node docker-ucsb-1 as master by adding a label and a taint
[markmaster] Master docker-ucsb-1 tainted and labelled with key/value: node-role.kubernetes.io/master=""
[bootstraptoken] Using token: dd97a9.2868c79262f0905a
[bootstraptoken] Configured RBAC rules to allow Node Bootstrap tokens to post CSRs in order for nodes to get long term certificate credentials
[bootstraptoken] Configured RBAC rules to allow the csrapprover controller automatically approve CSRs from a Node Bootstrap Token
[bootstraptoken] Configured RBAC rules to allow certificate rotation for all node client certificates in the cluster
[bootstraptoken] Creating the "cluster-info" ConfigMap in the "kube-public" namespace
[addons] Applied essential addon: kube-dns
[addons] Applied essential addon: kube-proxy

Your Kubernetes master has initialized successfully!

To start using your cluster, you need to run the following as a regular user:

  mkdir -p $HOME/.kube
  sudo cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
  sudo chown $(id -u):$(id -g) $HOME/.kube/config

You should now deploy a pod network to the cluster.
Run "kubectl apply -f [podnetwork].yaml" with one of the options listed at:
  https://kubernetes.io/docs/concepts/cluster-administration/addons/

You can now join any number of machines by running the following on each node
as root:

  kubeadm join --token <token> 128.111.54.69:6443 --discovery-token-ca-cert-hash sha256:<hash>
```

- now do what it says above and set up the non-root user environments

## Next, choose and install a POD Network

The install instructions are incredibly vague about which network to choose.
This blog was illuminating:  https://chrislovecnm.com/kubernetes/cni/choosing-a-cni-provider/
He has a nice summary table at the bottom.  But he also says, unless you need
CNI, just use `kubenet`.   The [networking guide](https://kubernetes.io/docs/concepts/cluster-administration/network-plugins/) for k8s
has other good info.

After reading through it, I went with Calico, which provided a nice set of
bootstrap instructions: https://docs.projectcalico.org/v3.0/getting-started/kubernetes/

```
$ kubectl apply -f \
https://docs.projectcalico.org/v3.0/getting-started/kubernetes/installation/hosted/kubeadm/1.7/calico.yaml
configmap "calico-config" created
daemonset "calico-etcd" created
service "calico-etcd" created
daemonset "calico-node" created
deployment "calico-kube-controllers" created
clusterrolebinding "calico-cni-plugin" created
clusterrole "calico-cni-plugin" created
serviceaccount "calico-cni-plugin" created
clusterrolebinding "calico-kube-controllers" created
clusterrole "calico-kube-controllers" created
serviceaccount "calico-kube-controllers" created
```

## Remove the restrictions (taints) on master so we can run pods here on a single node

```
kubectl taint nodes --all node-role.kubernetes.io/master-
```

## Apparently, we now have a working cluster with one node!

```
$ kubectl get nodes -o wide
NAME            STATUS    ROLES     AGE       VERSION   EXTERNAL-IP   OS-IMAGE             KERNEL-VERSION      CONTAINER-RUNTIME
docker-ucsb-1   Ready     master    6m        v1.9.3    <none>        Ubuntu 16.04.4 LTS   4.4.0-116-generic   docker://17.12.1-ce
```

## Adding additional nodes

Use the command in the instructions above with its embedded token and has to
add other nodes to the cluster.

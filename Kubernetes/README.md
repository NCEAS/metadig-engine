# Install a kubernetes cluster with `kubeadm`

kubeadm is a utility for bootstrapping a fully configured k8s cluster.
Installation instructions:  https://kubernetes.io/docs/setup/independent/install-kubeadm/

## Prereqs: docker

From the docs:

    On each of your machines, install Docker. Version v1.12 is recommended,
    but v1.11, v1.13 and 17.03 are known to work as well. Versions 17.06+
    might work, but have not yet been tested and verified by the
    Kubernetes node team.

Docker version 17.03-ce was installed with these commands:

```sudo apt-get update
sudo apt-get install -y \
    apt-transport-https \
    ca-certificates \
    curl \
    software-properties-common
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
sudo add-apt-repository \
   "deb https://download.docker.com/linux/$(. /etc/os-release; echo "$ID") \
   $(lsb_release -cs) \
   stable"
sudo apt-get update && sudo apt-get install -y docker-ce=$(apt-cache madison docker-ce | grep 17.03 | head -1 | awk '{print $3}')
```

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

Check that `kubelet` is running, ensuring that the status is `active`:
```
slaughter@docker-ucsb-1:~$ systemctl status kubelet
● kubelet.service - kubelet: The Kubernetes Node Agent
   Loaded: loaded (/lib/systemd/system/kubelet.service; enabled; vendor preset: enabled)
  Drop-In: /etc/systemd/system/kubelet.service.d
           └─10-kubeadm.conf
   Active: active (running) since Mon 2018-03-12 18:29:52 PDT; 17h ago
   ...
```
If it is not running, read the log in order to determine the cause and potentially restart or reinstall it.

## Then configure the cluster

As a regular user:

```
$ kubeadm init --pod-network-cidr=192.168.0.0/16
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

## Install monitoring software

The following commands install `grafana` monitoring software with `heapster` 
and `influxdb`:

```
$ git clone https://github.com/kubernetes/heapster.git
$ cd heapster
$ kubectl create -f deploy/kube-config/influxdb/
$ kubectl create -f deploy/kube-config/rbac/heapster-rbac.yaml
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

After installing docker, kubeadm, kubectl, kubelet on a worker node, i.e. `docker-ucsb-2.test.dataone.org` use the command in the instructions above with its embedded token and has to add other nodes to the cluster. 

Note that after the join token expires, a new token, along with the complete join command (to be entered at a new node) can be obtained with the command:
```
sudo kubeadm token create --print-join-command
```

Here is the join command that was printed from `kubeadm init`:

```
$ kubeadm join --token <token> 128.111.54.69:6443 --discovery-token-ca-cert-hash sha256:<hash>
sha256:b3944dd0f303e43bba579810381cd553a6e2b6448b94802c3ead3029080c9fd4
[preflight] Running pre-flight checks.
    [WARNING FileExisting-crictl]: crictl not found in system path
[preflight] Starting the kubelet service
[discovery] Trying to connect to API Server "128.111.54.69:6443"
[discovery] Created cluster-info discovery client, requesting info from "https://128.111.54.69:6443"
[discovery] Requesting info from "https://128.111.54.69:6443" again to validate TLS against the pinned public key
[discovery] Cluster info signature and contents are valid and TLS certificate validates against pinned roots, will use API Server "128.111.54.69:6443"
[discovery] Successfully established connection with API Server "128.111.54.69:6443"

This node has joined the cluster:
* Certificate signing request was sent to master and a response
  was received.
* The Kubelet was informed of the new secure connection details.

Run 'kubectl get nodes' on the master to see this node join the cluster.

```

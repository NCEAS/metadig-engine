# Setup disk storage accessible to all cluster nodes

GlusterFS is being used by the MetaDIG Kubernetes cluster to provide a shared filesystem to all cluster nodes. From the GlusterFS documentation "The GlusterFS is a scalable, distributed file system that aggregates disk storage resources from multiple servers into a single global namespace."

Instructions to install and configure GlusterFS were following from https://www.itzgeek.com/how-tos/linux/ubuntu-how-tos/install-and-configure-glusterfs-on-ubuntu-16-04-debian-8.html. An abbreviated version of these instructions is listed here:

```
- sudo apt-get install -y software-properties-common
- sudo add-apt-repository ppa:gluster/glusterfs-3.8
- sudo apt-get update
- sudo apt-get install -y glusterfs-server
- sudo service glusterfs-server start
- commands on docker-1, docker-3, not docker-2
    - sudo fdisk /dev/vdb
        - n
        - p
        - w
    - sudo mkdir -p /data/gluster
    - sudo mkfs.ext4 /dev/vdb1
- 
    - sudo mount /dev/vdb1 /data/gluster
    - echo "/dev/vdb1  /data/gluster ext4 defaults 0 0" | sudo tee --append /etc/fstab
- cluster conf
- sudo gluster peer probe docker-ucsb-2.test.dataone.org
    - enter cmd on docker-1, after gluster installed, started on docker-2
- sudo gluster peer probe docker-ucsb-3.test.dataone.org
- sudo gluster peer status
- sudo gluster pool list
- commands entered on docker-1 only
    - sudo gluster volume create gvol0 replica 2 docker-ucsb-1.test.dataone.org:/data/gluster/gvol0 docker-ucsb-3.test.dataone.org:/data/gluster/gvol0
    - sudo gluster volume start gvol0
    - sudo gluster volume info gvol0
- apt-get install -y glusterfs-client
- sudo mkdir -p /mnt/glusterfs
- docker-1 only
    - sudo mount -t glusterfs docker-ucsb-1.test.dataone.org:/gvol0 /mnt/glusterfs
- docker-2 only
    - sudo mount -t glusterfs docker-ucsb-2.test.dataone.org:/gvol0 /mnt/glusterfs
- docker-3 only
    - sudo mount -t glusterfs docker-ucsb-3.test.dataone.org:/gvol0 /mnt/glusterfs
- df -hP /mnt/glusterfs
- sudo chmod 1777 /mnt/glusterfs
```

Once the gluster volume is available, the shared filesystem can be made available to k8s via a persistent volume (PV) and then allocated to a pod via a persistent volume claim (PVC).

Here is a sample PV, which uses a static configuration, and has not been setup to allow k8s to modify the volume.

```
kind: PersistentVolume
apiVersion: v1
metadata:
  name: gluster-volume
  labels:
    type: local
spec:
  storageClassName: manual
  capacity:
    storage: 450Gi
  accessModes:
    - ReadWriteMany
  hostPath:
    path: "/mnt/glusterfs"
```

Here is the PVC used to allocate the PV:

```
kind: PersistentVolumeClaim
apiVersion: v1
metadata:
  name: task-pv-claim
  namespace: metadig
spec:
  storageClassName: manual
  accessModes:
    - ReadWriteMany
  selector:
    matchLabels:
      type: local
  resources:
    requests:
      storage: 5Gi
```

Now one or more pods can access this PVC with the `volumeMounts' and 'volume' section of this manifest:

```
apiVersion: apps/v1beta1
kind: Deployment
metadata:
  name: metadig-pv-test
  namespace: metadig
spec:
  selector:
    matchLabels:
      app: metadig-pv-test
      tier: backend
  replicas: 10
  template:
    metadata:
      labels:
        app: metadig-pv-test
        tier: backend
    spec:
      serviceAccountName: metadig-serviceaccount
      containers:
        - name: metadig-worker
          image: docker.io/sbpcs59/metadig-worker:dev
          imagePullPolicy: Always
          volumeMounts:
          - mountPath: "/usr/share/cluster"
            name: mydisk
      volumes:
        - name: mydisk
          persistentVolumeClaim:
            claimName: task-pv-claim
```

Note that the configuration for the PV and PVC is quite simple, such that when a pod ends and gives up it's PVC, k8s (kubelet) doesn't remove files that were created during the running of the pod.

# create a secret if you want to use Ceph secret instead of secret file
kubectl create -f ceph-secret.yaml
kubectl create -f cephfs-with-secret.yaml
kubectl get pods
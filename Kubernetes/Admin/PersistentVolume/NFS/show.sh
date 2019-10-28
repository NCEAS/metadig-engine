kubectl get pv
echo ""
kubectl get pvc -n metadig

kubectl describe pv nfs-pv
echo ""
kubectl describe pvc nfs-pvc -n metadig

# From https://bitnami.com/stack/rabbitmq/helm

# https://github.com/bitnami/charts/tree/master/bitnami/rabbitmq#installing-the-chart

# helm repo add bitnami https://charts.bitnami.com/bitnami

helm upgrade metadig-rabbitmq bitnami/rabbitmq \
--version=12.8.2 \
--namespace metadig \
--set image.registry=docker.io \
--set image.repository=bitnami/rabbitmq \
--set auth.username=metadig \
--set auth.password=quality \
--set replicaCount=3 \
--set podSecurityContext.enabled=false \
--set podSecurityContext.fsGroup=1001 \
--set podManagementPolicy=Parallel \
--set service.type=ClusterIP \
--set service.externalTrafficPolicy=Cluster \
--set ingress.enabled=false \
--set ingress.tls=false \
--set ingress.ingressClassName="nginx" \
--set persistence.enabled=true \
--set persistence.storageClass=csi-rbd-sc \
--set persistence.size=10Gi \
--set volumePermissions.enabled=false \
--set volumePermissions.containerSecurityContext.runAsUser=1001 \
--set serviceAccount.create=false \
--set serviceAccount.name="metadig" \
--set tls.enabled=false

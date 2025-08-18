# From https://bitnami.com/stack/rabbitmq/helm

# https://github.com/bitnami/charts/tree/master/bitnami/rabbitmq#installing-the-chart

# helm repo add bitnami https://charts.bitnami.com/bitnami
helm upgrade metadig-rabbitmq bitnamilegacy/rabbitmq \
  --version=12.8.2 \
  --namespace metadig \
  -f rabbitmq-values.yaml


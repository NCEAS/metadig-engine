# MetaDIG Kubernetes Operations Manual

The MetaDIG Metadata Assessment services can be configured and installed on the DataONE Kubernetes (k8s) cluster. This documentation describes the installation and configuration of these services for k8s.

For information regarding building MetaDIG software, please refer to the MetaDIG Developer Guide.

# Deploying MetaDIG Assessment Services on k8s

## MetaDIG Service Dependencies

The MetaDIG Assessment services are dependant on the following components which must be configured and installed before any of the services can be installed.

### Metadig Ingress

A k8s ingress resource provides network traffic routing from outside the k8s cluster network to a k8s service.

The metadig-engine ingress definition can be found in ./k8s/ingress-metadig.yaml. This k8s resource only needs to be created once, and at this time is
not included in the Helm install, but could be if desired. Note that every time the ingress resource is deleted and recreated, as would happen with a 'helm delete' and 'helm install',
the cert-manager sends a new request to the Let's Encrypt service to the Let's Encrypt service. Cert-manager is described in the DataONE k8s-cluster documentation [here](https://github.com/DataONEorg/k8s-cluster/blob/main/authentication/LetsEncrypt.md).

### Role and Rolebinding definitions

The k8s Role Based Access Control (RBAC) defintions for metadig-engine are provided at ./k8s/metadig-application-access.yaml.

The *metadig* Role and RoleBinding are similar to those for any other DataONE k8s application, providing all k8s access to a single k8s namespace. 
 
Metadig-engine also requires the additional Role and RoleBinding 'kube-system-reader'. This is necessary so that metadig services can define a specific DNS configuration to resolve aissue that is unique to metadig-engine. The details are providied in the github issue https://github.com/NCEAS/metadig-engine/issues/312.

Note that the appropriate `~/.kube/config` must be installed on the local system to enable modifying the `metadig` namespace. Once this file is installed, the following command must be issued
to enable authorization to the `metadig` namespace:
```
kubectl config use-context prod-metadig
```

### Persistent Storage

The Ceph-csi facility is used to provide persistent storage for metadig-engine k8s pods. Ceph-csi installation and configuration is describe here https://github.com/DataONEorg/k8s-cluster/blob/main#ceph-csi.

The following persistent volume claim (PVC) used by MetaDIG can be seen by entering the following commands:
```
$ kubectl config use-content prod-metadig
$ kubectl get pvc -n metadig
NAME                 STATUS   VOLUME                                     CAPACITY   ACCESS MODES   STORAGECLASS   AGE
cephfs-metadig-pvc   Bound    cephfs-metadig-pv                          200Gi      RWX                           106d
```

The PVC and associated persistent volume (PV) use a CephFS subvolume. The PV and PVC were initially created with these k8s manifests from the metadig-engine repository using these commands:

```
# From a local copy of the metadig-engine github repository:

$ kubectl config use-ontent prod-metadig
$ kubectl create -f ./k8s/cephfs-metadig-pvc.yaml
$ kubectl create -f ./k8s/cephfs-metadig-pv.yaml
```

These commands only need to be entered once, and as they have been run for the current DataONE production and development k8s clusters, do not need to be run again.
The PV that metadig-engine services uses is manually created. See a description of CephFS subvolumes used with k8s services described [here](https://github.com/DataONEorg/k8s-cluster/blob/main/storage/Ceph/Ceph-CSI-CephFS.md).

### PostgreSQL

The MetaDIG PostgreSQL stores assessment reports, DataONE persistent Identifier (pid) information, and information about the DataONE Member Nodes and Coordinating Node from which metadata is harvested.

PostgreSQL must be started before any MetaDIG service is started. The PostgreSQL server can be started from a local copy of the metadig-engine github repository using the [Helm package manager](https://helm.sh). For example:

```
cd git/NCEAS/metadig-engine/helm
kubectl config use-context prod-metadig
helm install postgres ./metadig-postgres --namespace metadig --version=1.0.0
```

The metadig-postgres service uses the CephFS Persistent Volume 'cephfs-metadig-pv'. Communication between the metadig-postgres pod and the PV is provided by [ceph-csi](https://github.com/DataONEorg/k8s-cluster/blob/main/storage/Ceph/Ceph-CSI.md).

In addition, the PV has been made available to the Linux command line on the production control node `k8s-ctrl-1.dataone.org` via a volume mount. This volume mount
was manually created and is available at `/mnt/k8ssubvol`. The subdirectory `postgresql` contains the metadig-postgres database files.

Two containers run inside the metadig-postgres pod, named `postgres` and `pgbouncer`. 

The `pgbouncer` container provides connection caching between postgres and client pods (metadig-controller, metadig-worker, metadig-scorer, metadig-scheduler).

*Note that the volume mount `/mnt/k8ssubvol` available to the Linux command line is provided for convienence and debugging purposes. This volume mount is not required
for any metadig-engine services, as access to the Ceph Storage Cluster is provided through ceph-csi.*

### RabbitMQ

RabbitMQ is used to queue assement requests that are created by the metadig-scheduler service.

First the Bitnami repository that contains the Helm chart must be added:

```
helm repo add bitnami https://charts.bitnami.com/bitnami
```

Then RabbitMQ can be installed with the commands:

```
helm install metadig-rabbitmq bitnami/rabbitmq \
--version=8.29.0 \
--namespace metadig \
--set image.registry=docker.io \
--set image.repository=bitnami/rabbitmq \
--set image.tag=8.11.1-debian-10-r14 \
--set kubeVersion=v1.23.3 \
--set auth.username=<username> \
--set auth.password=<password> \
--set replicaCount=3 \
--set podSecurityContext.enabled=false \
--set podSecurityContext.fsGroup=1001 \
--set podManagementPolicy=Parallel \
--set service.type=NodePort \
--set service.externalTrafficPolicy=Cluster \
--set ingress.enabled=false \
--set ingress.tls=false \
--set ingress.ingressClassName="nginx" \
--set persistence.enabled=true \
--set persistence.storageClass=csi-rbd-sc \
--set persistence.size=20Gi \
--set volumePermissions.enabled=false \
--set volumePermissions.containerSecurityContext.runAsUser=1001 \
--set serviceAccount.create=false \
--set serviceAccount.name="metadig" \
--set tls.enabled=false
```

See `metadig-rabbitmq/install-metadig-rabbitmq.sh`.

*Note that the correct username and password must be substituted for '<username>' and '<password>'.*
These values area available from the security repo at ./k8s/metadigPWs.gpg.

The Bitnami Helm chart is described [here](https://bitnami.com/stack/rabbitmq/helm). 

Chart options and additional information is described [here](https://github.com/bitnami/charts/tree/master/bitnami/rabbitmq#installing-the-chart)

Note that when metadig-controller starts, it will create RabbitMQ queues if they don't already exist.

The Metadig RabbitMQ Server can be uninstalled with the command:

```
helm delete metadig-rabbitmq -n metadig
```

### Solr

After metadig-worker creates a metadata assessments, a summary of that information is written to an Apache Solr server for each PID/Assessment Suite combination. 

Currently the Metadig Solr server runs in standalone mode (not SolrCloud) and is not available outside of the local Kubernetes network. 
This could be upgraded in the future by adding SolrCloud support if and when the Metadig Solr Server is made available to client applications.

The Metadig Solr Server uses the Bitnami Helm chart and Docker image. 

First the repository that contains the Helm chart must be added:

```
helm repo add bitnami https://charts.bitnami.com/bitnami
```

Then Solr can be started with the command:

```
helm install metadig-solr bitnami/solr \
--version=3.0.3 \
--namespace metadig \
--set auth.enabled=false \
--set coreNames="quality" \
--set collection="true" \
--set cloudEnabled=false \
--set image.tag=8.11.1-debian-10-r14 \
--set collectionShards=1 \
--set collectionReplicas=1 \
--set podSecurityContext.enabled=true \
--set podSecurityContext.fsGroup=1001 \
--set containerSecurityContext.enabled=true \
--set containerSecurityContext.runAsUser=1001 \
--set replicaCount=1 \
--set service.type=NodePort \
--set service.externalTrafficPolicy=Cluster \
--set ingress.enabled=false \
--set ingress.tls=false \
--set ingress.ingressClassName="nginx" \
--set ingress.hostname="solr.local" \
--set persistence.enabled=true \
--set persistence.mountPath=/bitnami/solr \
--set persistence.storageClass=csi-rbd-sc \
--set persistence.size=20Gi \
--set persistence.mountPath=/bitnami/solr \
--set volumePermissions.enabled=false \
--set volumePermissions.containerSecurityContext.runAsUser=1001 \
--set serviceAccount.create=false \
--set serviceAccount.name="metadig" \
--set tls.enabled=false \
--set tls.autoGenerated=false \
--set tls.certificatesSecretName="ingress-nginx-tls-cert-jks" \
--set tls.passwordsSecretName="metadig-solr/jks-password-secret" \
--set tls.keystorePassword="metadig" \
--set tls.truststorePassword="metadig" \
--set zookeeper.enabled=false \
--set zookeeper.persistence.enabled=false \
--set zookeeper.persistence.storageClass=""
```

See `./helm/metadig-solr/install-metadig-solr.sh`

Note that the *image.tag* parameter specifies an *immutable* tag, not a *rolling* release tag. See https://docs.bitnami.com/kubernetes/apps/drupal/configuration/understand-rolling-immutable-tags/.
A *rolling* tag represents a container that may be updated by the provider, so is not suitable for a produciton k8s environment.

The Solr Helm chart is described [here](https://bitnami.com/stack/solr/helm).

Installation options and other information can be found [here](https://github.com/bitnami/charts/tree/master/bitnami/solr/#installing-the-chart).

The Metadig Solr Server can be uninstalled with the command:

```
helm delete metadig-solr -n metadig
```

### DataONE token

A DataONE authorization token is used by MetaDIG services and it's usage is described in the NCEAS secure repository (see ./k8s/*-DataONE-token.txt).
file. This token needs to be created before metadig-scheduler and metadig-scorer are started.

## MetaDIG Assessment Services

The metadata assessment engine (metadig-engine) components are installed and updated on the DataONE Kubernetes cluster using helm. Helm charts for metadig-engine servers
are in the metadig-engine repo `./helm` directory.

The following sections describe the commands needed configure, install and update metadig-engine services.

### metadig-controller

All requests from clients (via the MetaDIG REST API) are routed to metadig-controller. Metadig-controller determines
what action needs occur to fulfil a request and will either complete that request itself, or send a request
to one of the quality services.

The metadig-controller service can be started with the commands:

```
cd ./helm
helm install metadig-controller ./metadig-controller --namespace metadig --version=1.0.0
```

The metadig-controller helm chart contains the Java property file ("metadig.properties") that is used by all metadig k8s applications. If you
wish to change any of the configuration values, this property file can be edited locally and then restarted with helm. Since all of the metadig 
applications mount this configuration file as a k8s volume, they will need to be restarted after any configuration change.

This same techique can be used to update the configuration file "log4j.properties", which is used to control the logging level of metadig applications.

This service can be uninstalled with the command:

```
helm delete metadig-controller -n metadig
```

### metadig-scheduler

The metadig-scheduler facility manages all harvesting tasks performed by the quality engine. Tasks are added to the list of schedule
tasks by entering them in the taskList.csv (e.g. /opt/local/metadig/taskList.csv).

The taskList.csv file is described [here](#taskList.csv),

The metadig-scheduler service can be started with the command:

```
helm install metadig-scheduler ./metadig-scheduler --namespace metadig --version=1.0.0
```

This service can be uninstalled with the command:

```
helm delete metadig-scheduler -n metadig
```

### metadig-scorer

The metadig-scorer service creates summary graphics for DataONE portals and member nodes.

metadig-scorer is started with the command:
```
helm install metadig-scorer ./metadig-scorer --namespace metadig --version=1.0.0 --set image.pullPolicy=Always
```
    
Work done by metadig-scorer is scheduled via metadig-scheduler, which sends requests to metadig-controller that forwards them to metadig-scorer determined by the task list.


### metadig-worker

The metadig-worker service creates metadata assessments for a metadata document and associated DataONE system metadata. 

The metadig-worker service is started with the command:
```
helm install metadig-worker ./metadig-worker --namespace metadig --version=1.0.0 --set replicaCount=1
```

This service can be uninstalled with the command:

```
helm delete metadig-worker -n metadig
```

Modifications can be made to a Helm chart while it is running, for example, to increase or decrease the number of metadig-worker replicas that are running. To 
modify the replica count, for example, specify a different count number with the command:

```
helm upgrade metadig-worker ./metadig-worker --namespace metadig --version=1.0.0 --set replicaCount=20
```

## Configuring Metadata Quality Engine Services

Metadig-engine services are configured by editing a few configuration files including the the metadig-engine helm charts. The following
sections describe the configuration files and associated services that use them.

### metadig-engine Properties File

The 'metadig.properties' file is read by all quality services and contains high level configuration information, such as dabase
configuration infomation. This file is included with the metadig-controller helm chart at ./helm/metadig-controller/config/metadig.properties.

### metadig-scheduler Task List

Recurring tasks that perform quality operations are scheduled by creating an entry in the taskList.csv file, typically localed
at /data/metadig/taskList.csv. The metadig-sheduler facility reads this file and schedules operations for each entry in the file.

The format of the task list file includes these CSV fields:

    task-type,task-name,task-group,cron-schedule,params
   
where

- 'task-type' indicates the type of task that will be run, currently one of ("quality" or "score", or "filestore")
- 'task-name': is a descriptive name to identify the task, for example "ARCTIC-MN-FAIR"
- 'task-group': currently this is always 'metadig', but may include other values in the future, such as 'dataone', etc.
- 'cron-schedule: this entry controls when and how often the task is scheduled, and follows the Java Quartz cron format, 
   similiar to the Linxu crontab facility. The individual fields in this entry are:
   
       seconds, minutes, hours, day of month, month, day of week, year
       
  for example, the entry "0 10 5 * * ?" would cause the task to be run at 10 minutes after 5:00AM every day.
  
- 'params': these are task specific parameters that may vary depending on the task type. The individual fields can be:
  - formatId filter (regex): This is a list of wildcards that will match records with these formatIds to harvest, delimeted by '|
  - suite id: the metadig suite id
  - node id: a DataONE node URN - data will be filtered using this (DataONE sysmeta "datasource")
  - D1 node base url: the base service URL for an MN or CN that will be used to query for pids to be processed
  - harvest begin date: 
    - the date to use to select pids to process. Pids that have a sysmeta 'dataSystemMetadatModified' date on or 
      before this date will be selected. Note that this date is only used for the first scheduled run. After the first
      run, the sysmeta dateSysmetadataModified date for the last pid processed is retained in the metadig-engine 
      'tasks' table and that will be used as the starting pid date for the next run. This ensures that a 
      'rolling window' is used to harvest the most recent pids.
  - harvest increment (days): increment (days): the time span for each search
  - requestCount: the number of itmes to request from DataONE listObjects
  - requestType: for score tasks, determine type of portal processing ("portal" or "node")

The fields in the task file are separated by colons (CSV), however, the parameters for the 'params' entry (the fifth CSV field) are separated by semi-colons.
Note that the combination of 'task-type' and 'task-name' must be unique amoung all tasks.

There are currently five types of tasks that can be scheduled:
- *quality*
- *score* (graphing) task for portals
- *score* (graphing) task for repositories
- *filestore* ingest task
- *download* task

The current *taskList.csv* file is included in the Appendix.

#### Quality task entry
There are two types of quality task entries: MN and CN. 
Metadata from the production DataONE CN is assessed with the FAIR Assessment Suite. 

Here is an example CN task entry:
```
quality,quality-dataone-fair,metadig,10 0/1 * * * ?,"^eml.*|^http.*eml.*|.*www.isotc211.org.*;FAIR-suite-0.3.1;urn:node:CN;2010-01-01T00:00:00.00Z;1;1000"
```

All metadata from all DataONE registered MNs that match the formatIds in the CN task entry are processed. 
Once a dataset has been assessest, the assessment report is available from the dataset landing page from https://search.dataone.org, for example:

```
https://search.dataone.org/quality/https%3A%2F%2Fpasta.lternet.edu%2Fpackage%2Fmetadata%2Feml%2Fknb-lter-vcr%2F355%2F2
```

Metadata from Member Nodes can be processed with an MN specific assessment suite if desired.
Here is an example MN task entry:
```
quality,quality-arctic,metadig,5 0/1 * * * ?,"^eml.*|^http.*eml.*;arctic.data.center.suite.1;urn:node:ARCTIC;2022-04-01T00:00:00.00Z;1;1000"
```

All metadata that has been uploaded to the MN that matches the formatIds in the MN task entry are processed.
Once a dataset has been assessest, the assessment report is available from the dataset landing page the MN browser, for example:

```
https://arcticdata.io/catalog/quality/urn%3Auuid%3Ac4e33a9c-f886-476b-880d-ee9fa9539b61
```

#### Scorer task entry for portals

This task queries the Solr server on a member node for the `collectionQuery` field associated with each portal. For each portal, the
`collectionQuery` is issued to determine the set of DataONE datasets selected by the portal's query filters. Then the assessment
scores for each dataset pid are retrieved from the metadig-engine Solr server. These scores are then written to a .CSV file
which is used to create a time series graph of those scores, for the requested suite. Both of these files are then saved to the
metadig-engine filestore, ready to be retrieved and displayed by client programs. Here is a sample task entry for portals on
the Arctic Data Center MN:

```
score,portal-ARCTIC-FAIR,metadig,10 0/1 * * * ?,"*portals*;FAIR-suite-0.3.1;urn:node:ARCTIC;2022-05-01T00:00:00.00Z;1;100;portal"
```
A sample graphic created by this task can be viewed at https://search.dataone.org/portals/tnc_dangermond/Metrics

#### Scorer task entry for repositories

To MetacatUI, each DataONE MN is considered a portal. As there is no entry in Solr for MN portals, the `score repository` tasks are
used to create graphics for assessment scores for all pids on an MN. The following
```
score,mn-portal-ARCTIC-FAIR,metadig,0 0 2 * * ?,";FAIR-suite-0.3.1;urn:node:ARCTIC;2022-05-01T00:00:00.00Z;1;1000;node"
```

For example, the graphic created from this task can be viewed on the metrics page for the Arctic Data Center at
https://search.dataone.org/portals/ARCTIC

#### filestore ingest task

### filestore

The `filestore` task is started by metadig-scheduler.

The filestore is used to store graphs and data files that are generated by metadig engine, for example,
the graphs created by metadig-scorer.

A file can be added to the filestore by copying the file to the filestore staging
area, /opt/local/metadig/store/stage. 

A file is copied to one of the subdirectories, then the metadig-scheduler task
will ingest it into the metadig engine system, by moving the file to a permanent location and creating a database
entry for it in the 'filestore' table. The directory in the staging area is used to determine the 'storage_type' column
value for the entry, for example 'code', 'data' or 'graph'.

```
cp src/main/resources/code/graph_monthly_quality_scores.R /mnt/k8ssubvol/metadig/store/stage/code/
```

When the metadig-scheduler `filestore` task runs, the file will be moved from `./store/stage/code` to `./store/code`. Then an entry for this
file will be added to the metadig database `filestore` table, so that it can be easily retrieved by programs such as metadig-scorer.

This method is how R scripts that are run by metadig-engine are added to the system.

### Auxilary Data File Download Task

The `download` task is responsible for acquiring web resources that are required by the MetaDIG assessment checks. 
Resources to acquire are entered in the `helm/metadig-scheduler/config/downloadsList.csv` file, which has the format:

```
source, destination, parameters, comments
```

For example:

```
https://mule.ess-dive.lbl.gov/api/v1/project/,/opt/local/metadig/data/ess-dive-project-list-v2.json,"newer;application/json","ESS-DIVE Project List v2"
```

In this example, the ESS-DIVE projects list is downloaded from the ESS-DIVE projects API, and saved to disk. The `parameters` column specifies 'newer' which will cause the local file to only be updated if the web resource is newer that the local file. The next parameter is the IANA mediaType, which is used during the download process.

### log4j.properties

All the assessment services are written in the Java language, and use the log4j logging facility to control how much information is 
printing when the serive runs. This information indicates what operations the service has performed, and any problems that were
encountered. This information can be used to troubleshoot problems and analyze service operation in order to monitor performance.

The log4j.properties file is available to k8s pods locally at /opt/local/metadig/config/log4j.properties. This file is provided by the metadig-controller Helm chart.

The quality service containers read this file when they start, and it takes precedence over any other log4j.properties file
that would be available to the services, such as a log4j.properties file that is contained in the metadig-engine-x.x.x.jar file.

# Portal Assessment Graphics 

The metadig-scorer facility creates the metrics graphics for portals and member nodes. In order to manually retrieve a graphic,
the portal document seriesId or the member nodeId needs to be specified.

To manually trigger the creation a POST request can be sent to the 'scores' service:

```
curl -X POST --insecure -H "Accept: */*" "https://api.dataone.org/quality/scores?id=${pid}&suite=${qualitySuite}&node=urn:node:ARCTIC"
```

Once the scores have been processed, they can be retrieved with a GET request:

```
$ curl --insecure -H "Accept: image/png" "https://api.dataone.org/quality/scores?id=${pid}&suite=${qualitySuite}&node=urn:node:ARCTIC"

$ curl --insecure -H "Accept: text/csv" "https://api.dataone.org/quality/scores?id=${pid}&suite=${qualitySuite}
```

# Upgrading metadig-engine services on k8s

Services are upgraded using the [`helm upgrade`](https://helm.sh/docs/helm/helm_upgrade/) command. 

Once you have released a new verion of metadig-engine and published new Docker images, you can modify `values.xml` file in each metadig-engine service to update `image.tag` value.
Also update the `Chart.yaml` file to upgrade the `version` and `appVersion` values. Once that is done, the new chart can be installed with the command:

```
helm upgrade <service> ./<service directory>
```

for each service to be upgraded. For example:

```
helm upgrade metadig-controller ./metadig-controller
```

# Adding a Member Node to the Assessment Harvest

To add an MN to the assessment harvest:
- add an entry to the metadig-scheduler task file ./helm/metadig-scheduler/config/taskList.csv: 

```
quality,quality-arctic,metadig,5 0/1 * * * ?,"^eml.*|^http.*eml.*;arctic.data.center.suite.1;urn:node:ARCTIC;2022-04-01T00:00:00.00Z;1;1000"
```

- add entries to the metadig-controller properties file ./helm/metadig-controller/config/metadig.properties:

```
ARCTIC.subjectId = CN=urn:node:ARCTIC,DC=dataone,DC=org
ARCTIC.serviceUrl = https://arcticdata.io/metacat/d1/mn
```

Note that this information could be obtained using CN DataONE API calls, but this method reduces the calls made to the CN during metadig-scheduler operation.

- Restart metadig-controller
   - see operations-manual.md
- Restart metadig-scheduler
   - see operations-manual.md
- inspect the log files of metadig-controller and metadig-scheduler to ensure the the harvest and assessments are running
- inspect a quality report
  - for example: https://cerp-sfwmd.dataone.org/quality/dmarley.1124.11

## Troubleshooting MetaDIG Engine Services

### Inspect Log Files

- increase log level 
- assessment and scorer task queing request and returned status are logged to metadig-controller

### Increase Logging Level For MetaDIG Services

- update the logging levels in ./helm/metadig-controller/config/log4j.properties and perform a 'helm upgrade' on metadig-controller and any other necessary metadig-engine service.

The log4j.properties file is shown below with logging set to `DEBUG` for metadig-worker:

```
# set the log level to WARN and the log should be printed to stdout.
log4j.rootLogger=DEBUG, stderr
#log4j.threshold=FATAL, ERROR, WARN, INFO


### LOGGING TO CONSOLE #########################################################
log4j.appender.stderr=org.apache.log4j.ConsoleAppender
log4j.appender.stderr.layout=org.apache.log4j.PatternLayout

# define the pattern to be used in the logs...
log4j.appender.stderr.layout.ConversionPattern=%d{yyyyMMdd-HH:mm:ss}: [%p]: %m [%c:%L]%n

# %p -> priority level of the event - (e.g. WARN)
# %m -> message to be printed
# %c -> category name ... in this case name of the class
# %d -> Used to output the date of the logging event. example, %d{HH:mm:ss,SSS} or %d{dd MMM yyyy HH:mm:ss,SSS}. Default format is ISO8601 format
# %M -> print the method name where the event was generated ... can be extremely slow.
# %L -> print the line number of the event generated ... can be extremely slow.
# %t -> Used to output the name of the thread that generated the log event
# %n -> carriage return

################################################################################
# EXAMPLE: Print only messages of level WARN or above in the package com.foo:
log4j.logger.edu.ucsb.nceas.mdqengine=INFO
log4j.logger.edu.ucsb.nceas.mdqengine.worker=DEBUG # this line is usually commented out or not included
log4j.logger.com.hp.hpl.jena=WARN
log4j.logger.org.dataone=OFF
log4j.logger.org.apache.commons=WARN
log4j.logger.org.apache.http=WARN
log4j.logger.org.dataone.mimemultipart=ERROR
log4j.logger.org.springframework=ERROR
log4j.logger.org.quartz=INFO
log4j.logger.org.apache.solr=WARN
log4j.logger.org.python=WARN
```

### Checking Privileges

If access to a k8s resource is encountered, privileges of a kubectl context can be checked:

```

kubectl auth can-i create deployments -n metadig --as=system:serviceaccount:metadig:metadig
kubectl auth can-i get pvc -n metadig --as=system:serviceaccount:metadig:metadig
kubectl auth can-i get secrets -n metadig --as=system:serviceaccount:metadig:metadig

```
### Debugging RabbitMQ

The Bitnami Helm installation used by metadig-engine contains a management component (rabbitmq_management) that can be queried to obtain information about queues that are created
and managed by metadig-engine services. In this example, a request is sent to the local network IP address of the RabbitMQ server to get the length of the `quality` queue.

```
    # Get the local IP address of the RabbitMQ server
    addr=`kubectl get service metadig-rabbitmq --namespace=metadig -o wide | grep metadig-rabbitmq | awk '{print $3}'`
    curl -s -u metadig:quality http://${addr}:15672/api/queues/%2F/quality | python3 -c "import sys, json; print(json.load(sys.stdin)['messages'])"
```


Note also that Bitnami installs a `rabbitmq_prometheus` component that can be used by [Grafana](https://grafana.com/grafana/dashboards/4279) to graph usage metrics.

## Inspecting metadig-engine PostgreSQL tables

See the `developer-guide.md` under `metadig-engine PostgreSQL Database` for information on how to connect to the database.
Information about the structure and usage of these tables by metadig-engine is described in that document.

## Kubernetes Software

The Linux system components that comprise the Kubernetes software is described in the DataONE k8s-cluster Github Repository [here](https://github.com/DataONEorg/k8s-cluster/blob/main/control-plane/control-plane.md).

 
## Appendix A: Example configuration files

### metadig-scheduler Task List

The metadig-scheduler Task List file is provided in the metadig-engine repo at ./helm/metadig-scheduler/config/taskList.csv.

The production Task List file is shown below:

```
task-type,task-name,task-group,cron-schedule,params
# task type, task name, task group, cron schedule, "formatId filter (regex); suite id; node id; D1 node base url; harvest begin date; harvest increment (days);requestCount"
# - task type:
# - task name:
# - task group:
# - cron schedule:
#   - seconds, minutes, hours, day of month, month, day of week, year
# - params
#   - formatId filter (regex): This is a list of wildcards that will match records with these formatIds to harvest, delimeted by '|
#   - suite id: the metadig suite id
#   - node id: a DataONE node URN - data will be filtered using this (DataONE sysmeta "datasource")
#   - D1 node base url: the base service URL for an MN or CN that will be used to query for pids to be processed
#   - harvest begin date: begin date: the first date to use for the DataONE 'listObjects' service
#   - harvest increment (days): increment (days): the time span for each search
#   - requestCount: the number of itmes to request from DataONE listObjects
#   - requestType: for score tasks, determine type of portal processing ("portal" or "node")
#
# Dataset quality scoring tasks
quality,quality-knb,metadig,0 0/1 * * * ?,"^eml.*|^http.*eml.*;knb.suite.1;urn:node:KNB;2020-08-24T00:00:00.00Z;1;1000"
quality,quality-arctic,metadig,5 0/1 * * * ?,"^eml.*|^http.*eml.*;arctic.data.center.suite.1;urn:node:ARCTIC;2022-04-01T00:00:00.00Z;1;1000"
quality,quality-dataone-fair,metadig,10 0/1 * * * ?,"^eml.*|^http.*eml.*|.*www.isotc211.org.*;FAIR-suite-0.3.1;urn:node:CN;2010-01-01T00:00:00.00Z;1;1000"
quality,quality-ess-dive,metadig,15 0/1 * * * ?,"^eml.*|^http.*eml.*;ess-dive.data.center.suite-1.1.0;urn:node:ESS_DIVE;2022-05-10T00:00:00.00Z;1;1000;"
#
# Portal scoring tasks
score,portal-KNB-FAIR,metadig,5 0/1 * * * ?,"*portals*;FAIR-suite-0.3.1;urn:node:KNB;2020-08-10T00:00:00.00Z;1;100;portal"
score,portal-ARCTIC-FAIR,metadig,10 0/1 * * * ?,"*portals*;FAIR-suite-0.3.1;urn:node:ARCTIC;2022-05-01T00:00:00.00Z;1;100;portal"
score,portal-mnUCSB1-FAIR,metadig,15 0/1 * * * ?,"*portals*;FAIR-suite-0.3.1;urn:node:mnUCSB1;2020-08-24T00:00:00.00Z;1;100;portal"
#
# Note: Portal harvesting for DataONE portals created on search.dataone.org will be performed on mnUCSB1, as MetacatUI sends create and
#       update requests performed on search.dataone.org to this host. We want to harvest them as soon as they are created, and not have to wait for mnUCSB1 to
#      sync to the CN, and then the CN index it, so the following entry is obsolete, and no longer used.
# #score,portal-CN-FAIR,metadig,35 0/1 * * * ?,"*portals*;FAIR.suite-0.3.1;urn:node:CN;2020-08-24T00:00:00.00Z;1;100;portal"
#
# Task for creating member node metadata assessment graphs
score,mn-portal-ARCTIC-FAIR,metadig,0 0 2 * * ?,";FAIR-suite-0.3.1;urn:node:ARCTIC;2022-05-01T00:00:00.00Z;1;1000;node"
score,mn-portal-KNB-FAIR,metadig,0 10 2 * * ?,";FAIR-suite-0.3.1;urn:node:KNB;2020-08-24T00:00:00.00Z;1;1000;node"
score,mn-portal-ESS-DIVE-FAIR,metadig,0 45 2 * * ?,";FAIR-suite-0.3.1;urn:node:ESS_DIVE;2020-08-24T00:00:00.00Z;1;1000;node"
score,mn-portal-DataONE-FAIR,metadig,0 25 2 * * ?,";FAIR-suite-0.3.1;urn:node:CN;2020-08-24T00:00:00.00Z;1;1000;node"
# Task for ingesting files into the file store from /data/metadig/store/stage/{code,data,graph,metadata}
filestore,ingest,metadig,0 0/1 * * * ?,"stage;;*.*;README.txt;filestore-ingest.log"
#
# Admin NOTE: it appears that DataONE HttpMultipartRestClient can't handle two clients being created at the same time, even if they are by different threads. This needs to be
#      investigated further and potentially a bug needs to be logged in github for this. Until then, an easy workaround is to ensure that no two tasks are started
#      at the same time, so adjust the cron schedule accordingly.
#
# Node list from DataONE - run every hour, at the beginning of the hour
nodelist,MN-NODE-LIST,metadig,0 45 * * * ?,"urn:node:CN"
#
# Acquire data files that are used by assessment checks
# This task runs every hour on the half hour
downloads,downloads,metadig,0 30 0/1 * *  ?,"no params"
```

### Metadig engine properties file 

The metadig engine properties file `metadig.properties` contains configuration and runtime settings needed by all metadig applications.

This file is deployed from `./helm/metadig-controller/config/metadig.properties`.

The production properties file is shown below:

```
DataONE.authToken = not-set in config - see github ./k8s/k8s-secret-for-DataONE-token.txt.gpg
CN.subjectId = CN=urn:node:CN,DC=dataone,DC=org
CN.serviceUrl = https://cn.dataone.org/cn
# The subjectId is used during harvesting of system metadata and metadata from this DataONE member node. THis is
# required in order to read non-public content.
KNB.subjectId = CN=urn:node:KNB,DC=dataone,DC=org
KNB.serviceUrl = https://knb.ecoinformatics.org/knb/d1/mn
ARCTIC.subjectId = CN=urn:node:ARCTIC,DC=dataone,DC=org
ARCTIC.serviceUrl = https://arcticdata.io/metacat/d1/mn
ESS_DIVE.subjectId = CN=urn:node:ESS-DIVE,DC=dataone,DC=org
ESS_DIVE.serviceUrl = https://data.ess-dive.lbl.gov/catalog/d1/mn
# For scorer jobs, the configured host (e.g. mnUCSB1) is used to harvest portal entries, but they will be stored in
# the filestore as the 'proxiedNodeId' hostid, and this hostid will be used by clients when retrieving by hostid
mnUCSB1.subjectId = CN=urn:node:mnUCSB1,DC=dataone,DC=org
mnUCSB1.serviceUrl = https://mn-ucsb-1.dataone.org/knb/d1/mn
# This node is no longer supported
CA_OPC.subjectId = CN=urn:node:CA_OPC,DC=dataone,DC=org
CA_OPC.serviceUrl = https://opc.dataone.org/metacat/d1/mn
mnStageUCSB2.subjectId = CN=urn:node:mnStageUCSB2,DC=dataone,DC=org
mnStageUCSB2.serviceUrl = https://mn-stage-ucsb-2.test.dataone.org/metacat/d1/mn
# The RabbitMQ connecction information
RabbitMQ.host = metadig-rabbitmq.metadig.svc.cluster.local
RabbitMQ.port = 5672
RabbitMQ.username = <see ./security/k8s/metadigPWs.gpg>
RabbitMQ.password = <see ./security/k8s/metadigPWs.gpg>
solr.location = http://metadig-solr.metadig.svc.cluster.local:8983/solr
# PostgreSQL connection information
postgres.user = metadig
postgres.passwd = metadig
jdbc.url = jdbc:postgresql://metadig-postgres.metadig.svc.cluster.local:6432/metadig
# The metadig-scheduler task file
task.file = /opt/local/metadig/taskList.csv
# The metadig-scheduler service does not use the public MetaDIG URL, but instead
# connects directly to the k8s service.
quality.serviceUrl = http://metadig-controller.metadig.svc.cluster.local:8080/quality
metadig.base.directory = /opt/local/metadig
metadig.store.directory = /opt/local/metadig/store
metadig.data.dir = /opt/local/metadig/data
#index.latest = false
metadig.data.dir = /opt/local/metadig/data
bookkeeper.enabled = false
# DataONE bookkeeper service info.
bookkeeper.authToken =
bookkeeper.url = http://bookkeeper.bookkeeper.svc.cluster.local:8080/bookkeeper/v1
downloadsList = ${metadig.data.dir}/downloadsList.csv
```

## Appendix B: Helm Chart Installation, Update, Uninstallation

Here is a list of all the Helm commands required to run all MetaDIG services. Note that longer Helm commands, especially for non-NCEAS charts have been 
included in Bash shell scripts for convienence.

### Installing packages on the k8s production cluster

The default helm setup for metadig-engine charts authored by NCEAS (not bitnami) is to use configuration files for the k8s production cluster. As 
k8s production is the default, it is not necesary to set a helm parameter to select the appropriate metadig-engine configuration files. Therefor
the commands below can be used to install metadig-engine services to k8s production.

```
#
# Set the appropriate kubectl context so that helm installation are sent to k8s production cluster
#
kubectl config use-context prod-metadig
# Always confirm the current context is correct
kubectl config get-contexts
cd ./helm
helm install metadig-postgres ./metadig-postgres --namespace metadig --version=1.0.0

# Bitnami rabbitmq
./metadig-rabbitmq/install-metadig-rabbitmq.sh

# Bitnami Solr
./metadig-solr/install-metadig-solr.sh

helm install metadig-controller ./metadig-controller --namespace metadig --version=1.0.0  --set image.pullPolicy=Always

helm install metadig-worker ./metadig-worker --namespace metadig --version=1.0.0 --set replicaCount=10 --set image.pullPolicy=Always
helm install metadig-scorer ./metadig-scorer --namespace metadig --version=1.0.0 --set image.pullPolicy=Always
helm install metadig-scheduler ./metadig-scheduler --namespace metadig --version=1.0.0 --set image.pullPolicy=Always
```

### Installing packages on the k8s development cluster

To install metadig-engine services to the k8s development cluster, the following commands can be used. Note that additional helm parameters are
required for the metadig-controller and metadig-scheduler installations, so that approriate configuration files are selected for k8s development.

```
#
# Set the appropriate kubectl context so that helm installation are sent to k8s development cluster
#
kubectl config use-context dev-metadig
# Always confirm the current context is correct
kubectl config get-contexts
cd ./helm
helm install metadig-postgres ./metadig-postgres --namespace metadig --version=1.0.0 --set k8s.cluster=dev

# Bitnami rabbitmq
./metadig-rabbitmq/install-metadig-rabbitmq.sh

# Bitnami Solr
./metadig-solr/install-metadig-solr.sh

helm install metadig-controller ./metadig-controller --namespace metadig --version=1.0.0  --set image.pullPolicy=Always --set k8s.cluster=dev
helm install metadig-worker ./metadig-worker --namespace metadig --version=1.0.0 --set replicaCount=1 --set image.pullPolicy=Always --set k8s.cluster=dev
helm install metadig-scorer ./metadig-scorer --namespace metadig --version=1.0.0 --set image.pullPolicy=Always --set k8s.cluster=dev
helm install metadig-scheduler ./metadig-scheduler --namespace metadig --version=1.0.0 --set image.pullPolicy=Always --set k8s.cluster=dev
```

### Uninstalling packages

The metadig-engine services can be uninstalled from either of the k8s clusters with the commands:

```
# Always confirm the current context is correct
kubectl config get-contexts

helm delete metadig-scheduler -n metadig
helm delete metadig-scorer -n metadig
helm delete metadig-worker -n metadig
helm delete metadig-controller -n metadig

helm delete metadig-rabbitmq -n metadig
helm delete metadig-postgres -n metadig
helm delete metadig-solr -n metadig
```

### Debugging helm charts

Note that helm can be used in a debugging mode, so that the .yaml files that it generates can be inspected. When the helm `--dry-run` argument is used,
the .yaml files that it generates are not send to k8s, but are printed out to the console instead. For example:

```
helm install --dry-run --debug metadig-controller ./metadig-controller --namespace metadig --version=1.0.0  --set dns.config=false
```

Note that metadig-engine charts have the special argument `dns.config=false` that must be used when using the helm debug arguments. This
is related to the `dns` section of `./templates/deployment.yaml` and the helm `lookup` function which is not supported when using `--dry-run`.
Therefor this section must be skipped when using `--dry-run`. See https://github.com/helm/helm-www/issues/635 for details.


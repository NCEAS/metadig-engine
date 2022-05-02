# MetaDIG Kubernetes Operations manual

The MetaDIG Metadata Assessment services (MAS) can be configured and installed on the DataONE Kubernetes (k8s) cluster. This documentation describes the installation and configuration of these services for k8s.

For information regarding building MES software, please refer to the MetaDIG Developer Guide.

# Deploying MetaDIG Assessment Services on k8s

## MetaDIG Service Dependencies

The MetaDIG Assessment Services (MAS) are dependant on the following components which must be configured and installed before any MAS can be installed.

### persistent storage

The Ceph-csi facility is used to provide persistent storage for metadig-engine k8s pods. Ceph-csi installation and configuration is describe here https://github.com/DataONEorg/k8s-cluster/blob/main#ceph-csi.

The following persistent volume claim (PVC) used by MetaDIG can be seen by entering the following commands:
```
$ kubectl use content metadig
$ kubectl get pvc -n metadig
NAME                 STATUS   VOLUME                                     CAPACITY   ACCESS MODES   STORAGECLASS   AGE
cephfs-metadig-pvc   Bound    cephfs-metadig-pv                          200Gi      RWX                           106d
```

The PVC and associated persistent volume (PV) use a CephFS subvolume. The PV and PVC were initially created with these k8s manifests from the metadig-engine repository using these commands:
```
# From a local copy of the metadig-engine github repository:
$ kubectl use content metadig
$ kubectl create -f ./deployments/metadig-engine/cephfs-metadig-pvc.yaml
$ kubectl create -f ./deployments/metadig-engine/cephfs-metadig-pv.yaml
```


### PostgreSQL
The MetaDIG PostgreSQL stores assessment reports, PID information, and information about the DataONE Member Nodes and Coordinating Node from which metadata is harvested.

PostgreSQL must be started before any MetaDIG service is started. The PostgreSQL server can be started from a local copy of the metadig-engine github repository, for example:
```
cd git/NCEAS/metadig-engine/helm
kubectl config use-context metadig
helm install postgres ./postgres --namespace metadig --version=1.0.0
```

### RabbitMQ

RabbitMQ is used to queue assement requests that are created by the metadig-scheduler service.

The RabbitMQ deployment can be installed with the commands:

```
helm install metadig-rabbitmq bitnami/rabbitmq \
--version=8.29.0 \
--namespace metadig \
--set image.registry=docker.io \
--set image.repository=bitnami/rabbitmq \
--set kubeVersion=v1.23.3 \
--set auth.username=metadig \
--set auth.password=quality \
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
--set persistence.size=10Gi \
--set volumePermissions.enabled=false \
--set volumePermissions.containerSecurityContext.runAsUser=1001 \
--set serviceAccount.create=false \
--set serviceAccount.name="metadig" \
--set tls.enabled=false
```

Note that when metadig-controller starts, it will create RabbitMQ queues if they don't already exist.

### Solr

Assessment scores are stored in the MetaDIG Solr Server for each PID/Assessment Suite combination.

The Solr Server must be started before MetaDIG services are started. Currently a Solr server that is started on the k8s controller host system is used.
In the near future, a k8s based deployment of Solr will be used.

### DataONE token

A DataONE authorization token is used by MetaDIG services and it's usage is described in the NCEAS secure repository (./k8s).

## MetaDIG Assessment Services

The metadata assessment engine (metadig-engine) components are installed and updated on the DataONE Kubernetes cluster using the Helm package manager https://helm.sh.

The following sections describe the commands needed configure, install and update metadig-engine services.

### metadig-controller

All requests from clients (via the MetaDIG REST API) are routed to metadig-controller. Metadig-controller determines
what action needs occur to fulfil a request and will either complete that request itself, or send a request
to one of the quality services.

helm install metadig-controller ./metadig-controller --namespace metadig --version=1.0.0

The metadig-controller helm chart contains the Java property file ("metadig.properties") that is used by all metadig k8s applications. If you
wish to change any of the configuration values, this property file can be edited locally and then restarted with helm. Since all of the metadig 
applications mount this configuration file as a k8s volume, they will need to be restarted after any configuration change.

This same techique can be used to update the configuration file "log4j.properties", which is used to control the logging level of metadig applications.

### metadig-scheduler
The metadig-scheduler facility manages all harvesting tasks performed by the quality engine. Tasks are added to the list of schedule
tasks by entering them in the taskList.csv (e.g. /data/metadig/config/taskList.csv).

The taskList.csv file is described [here](#taskList.csv),

helm install metadig-scheduler ./metadig-scheduler --namespace metadig --version=1.0.0

### metadig-scorer

metadig-scorer is started with the command
    kubectl apply -f metadig-scorer.yaml
    
Work done by metadig-scorer is scheduled via metadig-scheduler, which sends requests to metadig-controller that forwards them to metadig-scorer determined by the task list.

helm install metadig-scorer ./metadig-scorer --namespace metadig --version=1.0.0

### metadig-worker

The metadig-worker service is started with the command:

    helm install metadig-worker ./metadig-worker --namespace metadig --version=1.0.0 --set replicaCount=1

### filestore

The filestore is used to store graphs and data files that are generated by metadig engine, for example,
the graphs created by metadig-scorer.

A file can be added to the filestore manually by copying the file to the filestore staging
area, /opt/local/metadig/store/stage. A file is copied to one of the subdirectories, then the metadig-scheduler task
will ingest it into the metadig engine system, by moving the file to a permanent location and creating a databas
entry for it in the 'filestore' table. The directory in the staging area is used to determine the 'storage_type' column
value for the entry, for example 'code', 'data' or 'graph'.

cp src/main/resources/code/graph_monthly_quality_scores.R /data/metadig/store/stage/code/

Then add a 'filestore' entry into the "task file" as shown below.

# Managing Metadata Quality Engine Services

## Configuration Quality Engine services
### Runtime configuration
#### metadig.properties

The 'metadig.properties' file is read by all quality services and contains high level configuration information, such as dabase
configuration infomation.

Note that currently quality services do not dynamically read the configuration file whenever it is updated. Services need be
restarted when configuration parameters have been changed, for those changes to take effect.

#### MetaDIG Scheduler Task List

Recurring tasks that perform quality operations are scheduled by creating an entry in the taskList.csv file, typically localed
at /data/metadig/taskList.csv. The metadig-sheduler facility reads this file and schedules operations for each entry in the file.

The format of the task list file is:

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

There are currently four types of tasks that can be scheduled:
- quality task
- scorer (graphing) task for portals
- scorer (graphing) task for repositories
- filestore ingest task


filestore,ingest,metadig,0 0/1 * * * ?,"stage;;*.*;README.txt;filestore-ingest.log"

#### MetaDIG Configuration File

The main MetaDIG configuration file is available to k8s services at /opt/local/metadig/metadig.properties. Note that this file
is installed with the metadig-controller Helm chart.

#### log4j.properties

All the assessment services are written in the Java language, and use the log4j logging facility to control how much information is 
printing when the serive runs. This information indicates what operations the service has performed, and any problems that were
encountered. This information can be used to troubleshoot problems and analyze service operation in order to monitor performance.

The log4j.properties file is available to k8s pods locally at /opt/local/metadig/config/log4j.properties. This file is provided by the metadig-controller Helm chart.

The quality service containers read this file when they start, and it takes precedence over any other log4j.properties file
that would be available to the services.

# Troubleshooting MetaDIG Engine Services

# Portal Assessment Graphics 

The metadig-scorer facility creates the metrics graphics for portals and member nodes. In order to manually retrieve a graphic,
the portal document seriesId or the member nodeId needs to be specified.

To manually trigger the creation a POST request can be sent to the 'scores' service
```
curl -X POST --insecure -H "Accept: */*" "https://api.dataone.org:30443/quality/scores?id=${pid}&suite=${qualitySuite}&node=urn:node:ARCTIC"
```

Once the scores have been processed, they can be retrieved with a GET request:

```
curl --insecure -H "Accept: image/png" "https://api.dataone.org:30443/quality/scores?id=${pid}&suite=${qualitySuite}&node=urn:node:ARCTIC"```
```
curl --insecure -H "Accept: text/csv" "https://api.dataone.org:30443/quality/scores?id=${pid}&suite=${qualitySuite}```

# Solr server

Metadata assessments are written to an Apache Solr server. Currently this server runs standalone on the main k8s node and is started with the standard Ubuntu system services:

    service solr start

Currently this Solr server is only available to the local network.

 


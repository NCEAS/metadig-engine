# MetaDIG Architecture Notes

This document outlines the system architecture for generating
metadata quality reports for holdings in DataONE Member Nodes.

## Design goals
* Scale number of workers so that member nodes are not overloaded
* Know the state of engine processing tasks
* Scalability to handle many parallel processing requests
* Deployable at MN or CN
* Admin can configure which suites are run
* Robust to failures and restarts of master and/or workers

## MetaDIG system components
* Metadig-controller: a controller for managing a pool of MetaDIG engines and a task 
queue
    * dependencies
        * metadig-engine
        * RabbitMQ
        * Tier 3 MN implementation supporting SOLR query engine (e.g., Metacat)
        * Docker Container Manager
            * Rancher: (https://rancher.com/)
            * Docker Kubernetes: https://www.docker.com/kubernetes
    * API
        * /tasks/{task_id}
        * API fronting RabbitMQ, allows external clients to push jobs into queue
        * Also need a monitor daemon that periodically pulls jobs from the MN list
        * /config/{param}
            * List of MN ID to be serviced, maybe baseURL too
            * List of suites to execute per formatID (or wildcard?)
    * State
        * RabbitMQ taskqueue, persistent across reboots
            * List of MetaDIGTask
        * Configuration state
        * tasklist.csv -- data structure describing a task, including
            * Metadata pid
            * Metadata formatid
            * Metadata access info (url, or other pointer like filesystem location)
            * Sysmeta access info (url, or other pointer like filesystem location)
            * Data access info ( (url, or other pointer like filesystem location)
            * Resource map and its sysmeta
    * metadig-r
        * R package used to author and test R quality tests
    * metadig-py
        * Python module used to author and test quality tests written in python
    * mdq-webapp:
        * The Apache Tomcat based webapp that accepts MetaDIG service requests and forwards
          them to metadig-controller

* metadig-scorer: creates graphs and CSV of aggregated quality scores and writes them to disk
	* creates graphs and CSV of aggregated quality scores and writes them to disk
	* dependencies
		* metadig-controller
	* API
		* POST
		    * requests that a graph is created for the specified aggregation type
		    * syntax: /graph?project=<id>&node=<nodeId>&suite=<name>&dialect=<name>
		        * example: /graph?project=urn:uuid:dddaa020-1038-4c34-a270-67a5a16e2f23
		    * parameters
		        * project: the project (collection) to create the graph for
		            * not required, no default
		        * node: the DataONE CN or MN id to obtain data from
		            * required, default: urn:node:CN
		        * suite: the quality suite id, e.g. "FAIR.suite.1"
		            * if not specified, default: FAIR.suite.1
		        * dialect: the metadata format family names, e.g. "iso19115, eml"
		            * required, no default
		    * Note that specifying a project causes the pids that are associated with that
		      project to be the set of pids included in the aggregation graph. If a project is
		      not specified, then all pids for a node are included, but filtered by dialect
		* GET
		    * retrieves a pre-generated graph
		    * syntax: /graph?project=<id>&node=<nodeId>&suite=<name>&dialect=<name>
		        * example: /graph?project=urn:uri:1234-4567
		    * parameters - same as POST
	* The following sequence diagram shows the events that occur during the generation of an
	accumulated metadata assessment graphic ![MetaDIG Graph Generator](https://github.com/NCEAS/metadig-engine/blob/master/docs/images/generate-metadata-assessment-graph.png "MetaDIG Engine Grapher")

* metadig-scheduler: handles scheduling various jobs
    * dependencies:
        - metadig-engine
    * takes the items in taskList.csv and schedules by task type using quartz jobs running on cron triggers
    * task types
        - quality: Dataset quality scoring
        - score: Portal and member node scoring tasks
        - filestore: Task for ingesting files into the file store from /data/metadig/store/stage/{code,data,graph,metadata}
        - node list:  Node list from DataONE
        - download: Acquire data files that are used by assessment checks

* metadig-worker: runs quality jobs
    * dependencies:
        - metadig-engine
        - metadig-r
        - metadig-py
        - solr
        - postgres
        - rabbitMQ
    
## MetaDIG engine components

The following diagram shows the various components of the MetaDIG engine:

```mermaid
graph LR
  A[Client] --> D1[DataONE member node]
  subgraph Metadig Engine
    Ms[metadig-scheduler]
    Mc[metadig-controller]
    Mw[metadig-worker]
    Msc[metadig-scorer]
  end
  Ms ----> |Solr Query| D1
  Ms --> |Metadig API| Mc
  Mc --> |basicPublish| Rq
  Mc --> |basicPublish| Rs
  Rc --> |basicConsume| Mc
  Rq --> |basicConsume|Mw
  Mw --> |basicPublish| Rc 
  Msc --> |basicPublish| Rc
  Mw --> Solr
  Mw <--> PG
  Rs --> |basicConsume| Msc
  Msc --> Solr
  Msc --> FS
  subgraph RabbitMQ
    Rq([RabbitMQ Quality])
    Rc([RabbitMQ Completed])
    Rs([RabbitMQ Scorer])
  end
  subgraph Storage
    Solr[metadig-solr]
    PG[metadig-postgres]
    FS[Persistent Filestore]
  end
```

An older, more detailed, but less accurate, diagram is available [here](https://github.com/NCEAS/metadig-engine/blob/master/docs/images/metadig-engine_components.png)

## The following diagrams show message passing between the MetaDIG components:

* This sequence diagram showing how a job is popped off of the quality queue by a worker, processed by the worker, results saved to Postgres and Solr, and then the controller is told the job is complete.

```mermaid
sequenceDiagram
  participant QualityQueue 
  participant Worker
  participant Postgres
  participant Solr
  participant Completed as CompletedQueue
  participant Controller

  title MetaDIG Engine: Processing a Request

  QualityQueue->>Worker: basicConsume()
  Worker->>Postgres: insert('processing')
  Worker->>QualityQueue: acknowledgement
  activate Worker
  Worker->>Worker: processReport()
  deactivate Worker

  alt success case
    Worker->>Solr: add(document)
    Worker->>Postgres: update('success')
    Worker->>CompletedQueue: QueueEntry
    
    CompletedQueue->>Controller: handleCompleted(success)
    Controller->>CompletedQueue: acknowledgement
  else failure
    Worker->>Postgres: update('failure')
    Worker->>CompletedQueue: QueueEntry
    CompletedQueue->>Controller: handleCompleted(failure)
    Controller->>CompletedQueue: acknowledgement
  end
```

* This sequence diagram shows how a trigger event on a MN or CN results in a job being added to the pending queue.

```mermaid
sequenceDiagram
  participant D1Client
  participant Metacat
  participant Scheduler
  participant Controller
  participant Queue
  participant Worker

  title MetaDIG Engine: 'Quality' Queue

  activate D1Client
  D1Client ->> Metacat: MN.create(metadata, sysmeta)
  deactivate D1Client

  activate Metacat
  Metacat --> Scheduler: Harvest
  deactivate Metacat

  activate Scheduler
  Scheduler ->> Scheduler: MDQClient.requestReport(sysmeta)
  Scheduler ->> Controller: https://quality..../suite/{id}/run
  deactivate Scheduler

  activate Controller
  Controller->>Queue: BasicPublish
  deactivate Controller

  Queue ->> Worker: BasicConsume
  activate Worker
  Worker ->> Worker: processRun(metadata, sysmeta, suiteId)
  deactivate Worker
```

## The metadig-engine scheduler

* This sequence diagram shows how the scheduler determines which DataONE metadata documents to create quality reports for, then submits requests to metadig-engine to create the reports.

```mermaid
sequenceDiagram
  participant Scheduler
  participant MNIndex
  participant QualityIndex
  participant Controller

  title MetaDIG Engine: Index Monitor

  activate Scheduler
  Scheduler ->> MNIndex: query(pid="*", updateId)
  activate MNIndex
  MNIndex ->> Scheduler: pid list
  deactivate MNIndex
  Scheduler ->> QualityIndex: query(pid, suiteId)
  activate QualityIndex
  QualityIndex ->> Scheduler: pid status
  alt does not exist
    Scheduler ->> Controller: /suite/id/run
    Controller ->> QualityIndex: add()
  end
  deactivate QualityIndex
  deactivate Scheduler
```

### Monitoring for stuck jobs

As shown in the diagram below, the worker pre-emptively acknowledges ('acks') the message before finishing the quality process. This is because while most processes are quite short (< 1 minute), occasionally they are very long (> 30 minutes). By default, [the RabbitMQ consumer will timeout](https://www.rabbitmq.com/consumers.html#acknowledgement-timeout) it's connection if it has not received an `ack` within 30 minutes of sending a message. Although the two phase queue system (quality and completed queues) was initially implemented to help this problem, it was not sufficient to prevent the timeouts and resuling stranded connections, thus the pre-emptive ack was also implemented. As part of this impelementation, the controller also launches a quartz job on a schedule configurable in the metadig.properties file to make sure that quality jobs don't get "stuck" in the processing state. This might happen if a worker unexpectedly dies mid-process, after acknowledging the RabbitMQ quality message, but before completing the task.  The following sequence diagram shows how this process works. In the diagram below the "Client" could either be a direct request from the API or (more likely) the scheduler.


```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant RMQ Quality
    participant Worker
    participant RMQ Completed

    Client->>Controller: Request report
    Controller-->>RMQ Quality: basicPublish()
    Worker-->>RMQ Quality: basicConsume()
    Note over Worker: run.Save() [processing]
    Worker -->> RMQ Quality: basicAck()
    Note over Worker: processReport()
    Note over Worker: run.Save() [success]
    Worker-->>RMQ Completed: basicPublish()
    Controller -->> RMQ Completed: basicConsume()

    alt Quartz Monitor
    Note over Controller: listInProcessRuns()
    Controller-->>RMQ Quality: basicPublish()
    Worker-->>RMQ Quality: basicConsume()
    Note over Worker: run.Save() [processing]
    Worker -->> RMQ Quality: basicAck()
    Note over Worker: processReport()
    Note over Worker: run.Save() [success]
    Worker-->>RMQ Completed: basicPublish()
    Controller -->> RMQ Completed: basicConsume()
    end
```


## Metadata Assessment

### Metadata Quality Display Mockups
* The following display shows metadata quality summarized for all of DataONE:

![DataONE Metadata Quality](https://github.com/NCEAS/metadig-engine/blob/master/docs/mockups/DataONE/DataONE-profile.png "DataONE Profile Page")

* The following display shows metadata quality summarized for KNB:
![KNB Metadata Quality](https://github.com/NCEAS/metadig-engine/blob/master/docs/mockups/KNB/knb-profile.png "KNB Profile Page")

* The following display shows metadata quality summarized for a DataONE user group:
![User Group Metadata Quality](https://github.com/NCEAS/metadig-engine/blob/master/docs/mockups/UserGroup/group-profile.png "UserGroup Profile Page")

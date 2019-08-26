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
* Metadig-controller:
    * Includes MetaDIG API 
        * Web API that implements the MetaDIG REST API
    * dependencies
        * Metadig-report-worker
    * Renamed/refactored version of mdq-webapp
* Metadig-engine-core
    * Java library for executing quality suites
    * No dependencies on any other MetaDIG component
* Metadig-report-worker: a lightweight server instance that can be configured to process MetaDIG jobs
    * Depends: metadig-engine-core
    * servlet that exposes an API for running quality jobs
* Metadig-index-worker: a lightweight server instance that can be configured to process MetaDIG jobs
        * servlet that exposes an API for running quality jobs
        * Renamed/refactored version of mdq-webapp
* API
    * definition available at https://app.swaggerhub.com/apis/mbjones/metadig/2.0.0-a1-oas3#/
* Internal functions
    * private processTask()
    * private getTaskFromMaster()
* Metadig-controller: a controller for managing a pool of MetaDIG engines and a task 
queue
    * dependencies
        * metadig-report-worker
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
        * MetaDIGTask -- data structure describing a task, including
            * Metadata pid
            * Metadata formatid
            * Metadata access info (url, or other pointer like filesystem location)
            * Sysmeta access info (url, or other pointer like filesystem location)
            * Data access info ( (url, or other pointer like filesystem location)
            * Resource map and its sysmeta
    * Metadig-r
        * R package used to author and test R quality tests
    * Metadig-py
        * Python module used to author and test quality tests written in python
    * Mdq-webapp:
        * The Apache Tomcat based webapp that accepts MetaDIG service requests and forwards
          them to metadig-controller

* Metadig-graph-generator
	* creates graphs of aggregated quality scores
	* queries the Quality Solr Server for input quality scores
	* writes graphs of aggregated scores to persistent disk
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

* Metadig-py
    * Python module used to author and test quality tests written in python
    
## MetaDIG engine components

The following diagram shows the various components of the MetaDIG engine:

![MetaDIG Engine Components](https://github.com/NCEAS/metadig-engine/blob/master/docs/images/metadig-engine_components.png "MetaDIG Engine Components")

## The following diagrams show message passing between the MetaDIG components:

* This sequence diagram showing how a job is popped off of the pending queue by a worker, added to the inprocess queue, processed by the worker, results saved to a Tier 3 node, and then the controller is told the job is complete.

![Worker Process](https://github.com/NCEAS/metadig-engine/blob/master/docs/images/process-queue-entry_sequence.png "Worker Process")

* This sequence diagram shows how a trigger event on a MN or CN results in a job being added to the pending queue.

![Queue Event Sequence](https://github.com/NCEAS/metadig-engine/blob/master/docs/images/queue-event-trigger_sequence.png "Queue Event Sequence")

## The metadig-engine scheduler

* This sequence diagram shows how the scheduler determines which DataONE metadata documents to create quality reports for, then submits requests to metadig-engine to create the reports.

![Scheduler Sequence](https://github.com/NCEAS/metadig-engine/blob/master/docs/images/index-monitor_sequence.png "Scheduler Sequence")

## Metadata Quality Display Mockups
* The following display shows metadata quality summarized for all of DataONE:

![DataONE Metadata Quality](https://github.com/NCEAS/metadig-engine/blob/master/docs/mockups/DataONE/DataONE-profile.png "DataONE Profile Page")

* The following display shows metadata quality summarized for KNB:
![KNB Metadata Quality](https://github.com/NCEAS/metadig-engine/blob/master/docs/mockups/KNB/knb-profile.png "KNB Profile Page")

* The following display shows metadata quality summarized for a DataONE user group:
![User Group Metadata Quality](https://github.com/NCEAS/metadig-engine/blob/master/docs/mockups/UserGroup/group-profile.png "UserGroup Profile Page")

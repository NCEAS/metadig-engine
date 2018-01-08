# MetaDIG Architecture Notes

This document outlines the system architecture for generating
metadata quality reports for holdings in DataONE Member Nodes.

## Design goals
* Not overloading MNs
* Know the state of engine processing tasks
* Scalability to handle many parallel processing requests
* Deployable at MN or CN
* Admin can configure which suites are run
* Robust to failures and restarts of master and/or workers

## MetaDIG system components
* Metadig-engine: java library for executing Suites
    * No dependencies on any other metadig component
    * Renamed version of mdqengine
* Metadig-worker: a lightweight server instance that can be configured to process metadig jobs
    * Depends: metadig-engine
    * servlet that exposes an API for running quality jobs
    * Renamed/refactored version of mdq-webapp
* API
    * ?
* Internal functions
    * private processTask()
    * private getTaskFromMaster()
* Metadig-master: a controller for managing a pool of metadig engines and a task queue
    * Depends: metadig-worker
    * Depends: RabbitMQ
    * Depends: Tier 3 MN implementation supporting SOLR query engine (e.g., Metacat)
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
    * Mdq-webapp: 

## Future modules
    * Metadig-py
    * Python module used to author and test quality tests witten in python

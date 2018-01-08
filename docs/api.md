## Metadata Quality Engine API

### Summary

The API will allow clients to interact with the MQE using published methods either directly
or via a REST layer.

### Types
- Selector
- Check*
- Suite*
- Result
- Run* 

*stand-alone document format type

### Design methods
#### Checks
- MQE.listChecks()
	- GET /checks
- MQE.createCheck(check)
	- POST /checks (check is multi-part form data)
- MQE.getCheck()
	- GET /checks/:id
- MQE.updateCheck(check)
	- PUT /checks/:id (check is multi-part form data)
- MQE.deleteCheck()
	- DELETE /checks/:id

#### Suites
- MQE.listSuites()
	- GET /suites
- MQE.createSuite(suite)
	- POST /suites (suite is multi-part form data)
- MQE.getSuite()
	- GET /suites/:id
- MQE.updateSuite(suite)
	- PUT /suites/:id (suite is multi-part form data)
- MQE.deleteSuite()
	- DELETE /suites/:id

### Execution methods
- MQE.runSuite(suite, InputStream document)
	- POST /suites/:id/run (document is multi-part form data)

### Reporting methods
- MQE.listRuns()
	- GET /runs
- MQE.getRun()
	- GET /runs/:id
- MQE.deleteRun()
	- DELETE /runs/:id
	
### Aggregation methods
- MQE.aggregate() - returns CSV of results of running given suite over corpus returned by SOLR query
	- GET /suites/:id/aggregate/:query

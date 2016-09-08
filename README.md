# mdqengine
The Java project for MetaDig

[![Build Status](https://travis-ci.org/NCEAS/mdqengine.svg?branch=master)](https://travis-ci.org/NCEAS/mdqengine)

The MDQ Engine is a flexible engine to managing and executing metadata quality checks on a wide variety of metadata documents.
Quality Checks are organized into Suites that can be stored and managed by the engine before ultimately being run against input metadata documents.
QC run results are provided and can be stored by the engine for later retrieval and analysis, or consumed immediately and discarded.
We have aimed to support multiple languages for writing checks so that different communities can use the code and libraries with which they are most familiar.

Read on to see how you can get started with the MDQ engine!

## Creating a check
For technical details about the Suite, Check and Run models, Java model and the XSD schema provide documentation.
Below is a brief description of the common check elements to get started writing QC suites.

* Environment

	The MDQ engine allows checks to be written in a few different languages depending on the needs and complexity of the check or the 
	expertise of the check author. Supported environments include:
	- R
	- Python
	- JavaScript
	- Java 
	
* Code

	The code element is where most of the check work is performed and should adhere to the syntax rules for your specified environment. 
	
	For scripted environments (not 'Java') this is the code to be executed. 
	When Check.environment is 'Java' this will be the fully qualified class name of the Callable<Result> implementation.
	The script code can take a few different forms, but should minimally return some value that will be captured as a single Result.output.
	The simplest form is just a series of script statements with the final statement returning the desired value for Result.output.
	Alternatively, a call() function can be defined that returns the desired Result.output. This function will be called automatically if no output is found when executing the script as a series of statements.
	 If both output and status need to be returned, the code can set 'output' and 'status' variables and those will be included in the Result.
	 Finally - for greatest control - a full instance of the Result class can be returned in which case the code writer is responsible for setting the appropriate Result fields in a variable named 'mdq_result'.
	
* Selectors

	But how do you get values from your metadata document into your check code environment? Selectors!
	
	Selectors are used to extract certain parts of the metadata document and make those values available to the Check.code. 
	Each selector should have a unique name within the same check since the Selector.name is used to create a global variable in the scripting environment. 
	The code can then reference that variable name (exactly!) and gain access to the value (if it exists).
	The variables can be strings, numbers, booleans and lists of those types. When a selector does locate a value in the document, a null value (however that is represented in the particular script environment) will be provided.

## Selectors and Subselectors
	Sometimes it will be useful to select multiple values from a document and maintain the structure of where they originated. This can be accomplished using Selector.subselector. The parent selector uses an xpath to find some tree[s] in the document and the subselector queries each of the matched trees. Typically, a list of lists will be returned to your environment when using this pattern.

## Script environment state
	Sometimes it will be useful to maintain the script environment state (variables that have been initialized by selectors or by the check code itself) across multiple different checks. Setting the inheritState flag to true allows the previous check's variables to be accessed (and any previous checks before it as long as each check has opted to inherit state). Care should be taken that variable names do not collide from one check to another, otherwise the results can be confusing.
	

## Where to put your checks
	So you have written a check and validated it against the MDQE schema? Great!
	You can place the XML file in the 'checks' resource directory where it will automatically be loaded when the MDQ engine next initializes.
	The same is true of suites; they are included in the 'suites' resource directory.
	If you would like to reuse previously-defined checks, you may reference them my id from within your suite and they will be fully included in the suite that references them without having to duplicate XML code.
	
## Running your suites
	It is probably easiest to develop checks within an editor devoted your the script syntax you have chosen before placing the code in a check or suite XML document. You can also test the code by setting variable as you would expect them to be initialized by the selectors in your check. This will be a quicker way to identify interpretation and runtime issues before ever using the MDQ engine at all.
	Once you are satisfied with your check code, you can run a suite (one or more checks in a suite) using the command line:

`mvn exec:java -Dexec.mainClass="edu.ucsb.nceas.mdqengine.MDQEngine" -Dexec.args="<path-to-suite.xml> <path-to-metadata.xml>"`

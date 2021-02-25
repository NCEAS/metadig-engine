# metadig-engine
The Java project for MetaDig

[![](https://travis-ci.com/NCEAS/metadig-engine.svg?branch=main)](https://travis-ci.com/NCEAS/metadig-engine)

The Metadata Quality Engine is a flexible engine for managing and executing metadata quality checks on a wide variety of metadata documents.
Quality Checks are organized into Suites that can be stored and managed by the engine before ultimately being run against input metadata documents.
QC run results are provided and can be stored by the engine for later retrieval and analysis, or consumed immediately and discarded.
We have aimed to support multiple languages for writing checks so that different communities can use the code and libraries with which they are most familiar.

Read on to see how you can get started with the Metadata Quality Engine.

## Creating a check
For technical details about the Suite, Check and Run models, Java model and the XSD schema provide documentation.
Below is a brief description of the common check elements to get started writing QC suites.

* Environment

	The Quality Engine allows checks to be written in a few different languages depending on the needs and complexity of the check or the expertise of the check author. Supported environments include:
	- R
	- Python (Jython)
	- JavaScript
	- Java 
	
* Code

	The code element is where most of the check work is performed and should adhere to the syntax rules for your specified environment. 
	
	For scripted environments (not 'Java') this is the code to be executed. 
	When `Check.environment` is 'Java' this will be the fully qualified class name of the `Callable<Result>` implementation.
	The script code can take a few different forms, but should minimally return some value that will be captured as a single Result.output.
	The simplest form is just a series of script statements with the final statement returning the desired value for `Result.output`.
	Alternatively, a `call()` function can be defined that returns the desired `Result.output`. This function will be called automatically if no output is found when executing the script as a series of statements.
	 If both output and status need to be returned, the code can set `output` and `status` variables and those will be included in the Result.
	 Finally - for greatest control - a full instance of the `Result` class can be returned in which case the code writer is responsible for setting the appropriate Result fields in a variable named `mdq_result`.
	
* Selectors

	Values can be extracted from metadata documents and defined in your check using `selectors`.
	
	Selectors are used to extract certain parts of the metadata document and make those values available to the `Check.code`. 
	Each selector should have a unique name within the same check since the Selector.name is used to create a global variable in the scripting environment. 
	The code can then reference that variable name (exactly!) and gain access to the value (if it exists).
	The variables can be strings, numbers, booleans and lists of those types. When a selector does not locate a value in the document, a `null` value will be provided (however that is represented in the particular script environment).

## Script environment state

The script environment between successive checks can be maintained (variables that have been initialized by selectors or by the check code itself) 
Setting the inheritState flag to true allows the previous check's variables to be accessed (and any previous checks before it as long as each check has opted to inherit state). Care should be taken that variable names do not collide from one check to another, otherwise the results can be confusing.

## Where to put checks

A separate repository has be created for the suites and checks used by the Quality Engine, at https://github.com/NCEAS/metadig-checks.
If you wish to add a check or suite, please visit this repository and create a Git Pull Request containing any additions.

## Running suites

It is recommended to develop checks within an editor supporting the check language being used, before placing the code in a check or suite XML document. You can also test the code by setting variables as you would expect them to be initialized by the selectors in your check. This will be a quicker way to identify interpretation and runtime issues before testing with the Quality Engine.

The R package https://github.com/NCEAS/metadig-r can be used to assist in authoring checks.

Once you are satisfied with your check code, you can run a suite (one or more checks in a suite) using the command line:

`mvn exec:java -Dexec.mainClass="edu.ucsb.nceas.mdqengine.MDQEngine" -Dexec.args="<path-to-suite.xml> <path-to-metadata.xml> <path-to-DataONE-systemmetadata document>"`


## Using the library elsewhere

In order to use Metadata Quality Engine features in other projects, the Quality Engine distribution can be included in other Maven projects like any other dependency and all public classes and methods will be readily available to that project.



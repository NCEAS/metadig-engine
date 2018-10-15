# Note that metadig-engine.jar, used in called Makefiles, is obtained from the maven locally installed version, so 
# 'mvn install' or bin/install.sh must be run to refresh it before metadig-engineear Docker images are built.

SUBDIRS := metadig-tomcat metadig-worker metadig-httpd

METADIG_ENGINE_REPO=$(HOME)/.m2/repository/edu/ucsb/nceas/metadig-engine/$(METADIG_ENGINE_VERSION)/
#METADIG_ENGINE_REPO=/home/slaughter/stage
export METADIG_ENGINE_REPO

METADIG_WEBAPP_REPO=$(HOME)/.m2/repository/edu/ucsb/nceas/metadig-webapp/$(METADIG_WEBAPP_VERSION)/
#METADIG_WEBAPP_REPO=/home/slaughter/stage
export METADIG_WEBAPP_REPO

#DOCKER_REGISTRY := 
DOCKER_REGISTRY := docker.io/
#DOCKER_REGISTRY := localhost:5000/
#DOCKER_REGISTRY := docker-ucsb-1.test.dataone.org:5000/
export DOCKER_REGISTRY

#DOCKER_REPO := metadig/
DOCKER_REPO := sbpcs59/
export DOCKER_REPO

DOCKER_TAG := dev
export DOCKER_TAG

METADIG_WEBAPP_VERSION := 1.1.3-SNAPSHOT
export METADIG_WEBAPP_VERSION

METADIG_ENGINE_VERSION := 1.1.3-SNAPSHOT
export METADIG_ENGINE_VERSION

# Set Java options to optimize running inside containers
JAVA_OPTIONS := -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap
export JAVA_OPTIONS

all: $(SUBDIRS)
$(SUBDIRS):
	$(MAKE) -e -C $@

httpd:
	cd metadig-httpd; make -e

worker:
	cd metadig-worker; make -e

tomcat:
	cd metadig-tomcat; make

.PHONY: all $(SUBDIRS)




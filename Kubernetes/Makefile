SUBDIRS := metadig-httpd metadig-tomcat metadig-worker 
DOCKER_HUB := docker.io/
export DOCKER_HUB

DOCKER_REPO := sbpcs59/
export DOCKER_REPO

DOCKER_TAG := dev
export DOCKER_TAG

METADIG_WEBAPP_VERSION := 1.1.3-SNAPSHOT
export METADIG_WEBAPP_VERSION

METADIG_ENGINE_VERSION := 1.1.3-SNAPSHOT
export METADIG_ENGINE_VERSION

all: $(SUBDIRS)
$(SUBDIRS):
	$(MAKE) -e -C $@

worker:
	cd metadig-worker; make

httpd:
	cd metadig-httpd; make

tomcat:
	cd metadig-tomcat; make

.PHONY: all $(SUBDIRS)




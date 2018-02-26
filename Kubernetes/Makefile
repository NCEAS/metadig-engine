SUBDIRS := metadig-httpd metadig-tomcat metadig-worker
DOCKER_REPO := docker.io/sbpcs59/
export DOCKER_REPO

all: $(SUBDIRS)
$(SUBDIRS):
	$(MAKE) -e -C $@

.PHONY: all $(SUBDIRS)


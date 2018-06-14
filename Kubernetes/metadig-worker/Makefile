# Note that metadig-engine.jar is obtained from the maven locally installed version, so 
# 'mvn install' or bin/install.sh must be run to refresh it before metadig-engineear
# Docker images are built.

all: build

build:
	cp $(METADIG_ENGINE_REPO)/metadig-engine-$(METADIG_ENGINE_VERSION).jar metadig-engine.jar
	# D1 indexing can't find these files in the .jar, have to pull them out and add dir to classpath
	jar xvf metadig-engine.jar solr
	docker build -t $(DOCKER_REGISTRY)$(DOCKER_REPO)metadig-worker:$(DOCKER_TAG) .
	docker push $(DOCKER_REGISTRY)$(DOCKER_REPO)metadig-worker:$(DOCKER_TAG)



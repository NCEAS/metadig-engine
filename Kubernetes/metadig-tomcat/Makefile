# Note that metadig-engine.jar is obtained from the maven locally installed version, so 
# 'mvn install' or bin/install.sh must be run to refresh it before metadig-engineear
# Docker images are built.

all: build

build:
	cp $(METADIG_WEBAPP_REPO)/metadig-webapp-$(METADIG_WEBAPP_VERSION).war metadig-webapp.war
	docker build -t $(DOCKER_REGISTRY)/$(DOCKER_REPO)metadig-tomcat:$(DOCKER_TAG) .
	docker push $(DOCKER_REGISTRY)/$(DOCKER_REPO)metadig-tomcat:$(DOCKER_TAG)


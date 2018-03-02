all: build

build:
	cp $(HOME)/.m2/repository/edu/ucsb/nceas/metadig-webapp/$(METADIG_WEBAPP_VERSION)/metadig-webapp-$(METADIG_WEBAPP_VERSION).war metadig-webapp.war
	docker build -t $(DOCKER_REPO)metadig-tomcat:$(DOCKER_TAG) .
	docker push $(DOCKER_HUB)$(DOCKER_REPO)metadig-tomcat:$(DOCKER_TAG)


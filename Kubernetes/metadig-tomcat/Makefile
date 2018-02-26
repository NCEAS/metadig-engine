all: build

build:
	cp $(HOME)/.m2/repository/edu/ucsb/nceas/metadig-webapp/$(METADIG_WEBAPP_VERSION)/metadig-webapp-$(METADIG_WEBAPP_VERSION).war metadig-webapp.war
	docker build -t metadig-tomcat .
	docker tag metadig-tomcat $(DOCKER_REPO)metadig-tomcat:dev
	docker push $(DOCKER_REPO)metadig-tomcat:dev

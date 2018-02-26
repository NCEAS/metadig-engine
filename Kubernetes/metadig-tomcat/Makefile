all: build

build:
	cp ~/git/NCEAS/metadig-webapp/target/metadig-webapp.war .
	docker build -t metadig-tomcat .
	docker tag metadig-tomcat $(DOCKER_REPO)metadig-tomcat:dev
	docker push $(DOCKER_REPO)metadig-tomcat:dev

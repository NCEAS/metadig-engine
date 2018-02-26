all: build

build:
	docker build -t metadig-httpd .
	docker tag metadig-httpd $(DOCKER_REPO)metadig-httpd:dev
	docker push $(DOCKER_REPO)metadig-httpd:dev

all: build

build:
	docker build -t $(DOCKER_REPO)metadig-httpd:$(DOCKER_TAG) .
	docker push $(DOCKER_HUB)$(DOCKER_REPO)metadig-httpd:$(DOCKER_TAG)


all: build

build:
	cp $(HOME)/.m2/repository/edu/ucsb/nceas/metadig-engine/$(METADIG_ENGINE_VERSION)/metadig-engine-$(METADIG_ENGINE_VERSION).jar metadig-engine.jar
	docker build -t $(DOCKER_REPO)metadig-worker:$(DOCKER_TAG) .
	docker push $(DOCKER_HUB)$(DOCKER_REPO)metadig-worker:$(DOCKER_TAG)



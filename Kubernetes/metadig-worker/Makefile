all: build

build:
	cp $(HOME)/.m2/repository/edu/ucsb/nceas/metadig-engine/$(METADIG_ENGINE_VERSION)/metadig-engine-$(METADIG_ENGINE_VERSION).jar metadig-engine.jar
	docker build -t metadig-worker .
	docker tag metadig-worker $(DOCKER_REPO)metadig-worker:dev
	docker push $(DOCKER_REPO)metadig-worker:dev

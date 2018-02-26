all: build

build:
	cp ~/Documents/metadig-engine/target/metadig-engine-1.1.3-SNAPSHOT.jar .
	docker build -t metadig-worker .
	docker tag metadig-worker $(DOCKER_REPO)metadig-worker:dev
	docker push $(DOCKER_REPO)metadig-worker:dev

# Use an OpenJDK runtime as a parent image
FROM eclipse-temurin:17.0.7_7-jre-jammy
# 9-jre does not run with .Worker
#FROM openjdk:9-jre

LABEL org.opencontainers.image.source="https://github.com/nceas/metadig-engine"
LABEL org.opencontainers.image.title="metadig-engine worker"
LABEL org.opencontainers.image.version="1.0.1"
LABEL maintainer="Jeanette Clark jclark@nceas.ucsb.edu"

RUN mkdir -p /var/data/repos

# Set the working directory 
WORKDIR /var/lib/metadig

ARG ENGINE_TAG=3.2.0-SNAPSHOT
ENV ENGINE_TAG=${ENGINE_TAG}

RUN groupadd -g 59990 metadig
RUN useradd -u 59990 -g 59990 metadig

# The most recently built jar file is copied from the maven build directory to this dir by maven, so that
# it can be copyied to the image.
COPY target/metadig-engine-$ENGINE_TAG.jar metadig-engine-$ENGINE_TAG.jar
# For some reason, the DataONE indexer software can't find these files unless they are put in
# directory and included in the CLASSPATH (see java CMD). They are in the source tree under 'main/resources',
# but maybe further action needs to be taken for java/dataone to be able to find them without having to
# do this extra step.
COPY Docker/metadig-worker/solr solr/

RUN apt-get update
RUN apt-get install -y g++ git r-base r-base-dev r-base-core r-doc-html libc6-dev libssl-dev libxml2 libxml2-dev libcurl4-openssl-dev file libjansson4 libmagic-mgc libmagic1 libyara8 yara python3 python3-pip python3-dev openjdk-17-jdk

# Set Java environment variables
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH="$PATH:$JAVA_HOME/bin"

# Install required R packages
RUN echo 'options(repos = c(CRAN = "http://cran.rstudio.com"))' >> /usr/lib/R/etc/Rprofile.site
RUN Rscript -e "install.packages(c('remotes', 'stringr', 'jsonlite'))"
RUN Rscript -e "remotes::install_github('NCEAS/metadig-r', ref = 'v.0.2.0')"

# DataONE indexer prints copious error msgs if these files don't exist
RUN mkdir -p /etc/dataone/index && touch /etc/dataone/index/d1client.properties && touch /etc/dataone/node.properties && touch /etc/dataone/index/cn-index-processor.properties

COPY Docker/metadig-worker/requirements.txt /opt/local/metadig/

RUN pip install -r /opt/local/metadig/requirements.txt

# get the list of yara rules
ADD https://github.com/YARAHQ/yara-forge/releases/download/20250615/yara-forge-rules-core.zip .

RUN unzip yara-forge-rules-core

RUN python3 -c "import yara; r = yara.compile(filepath='/var/lib/metadig/packages/core/yara-rules-core.yar'); r.save('/var/lib/metadig/yara-compiled.yc')"
RUN chmod o+rx /var/lib/metadig/yara-compiled.yc

RUN chown -R metadig:metadig /var/lib/metadig /etc/dataone /opt/local/metadig

USER 59990

# Run the Worker process
# Note: docker --buile-arg only allows one argument (one token only, multiple tokens inside quotes doesn't work, so have
#   to specify java options directly on command line.
# Set classpath to include /opt/local/metadig/log4j.properties, if it exists, so that logging can be changed without
# having to rebuild the container. Note that on k8s, this dir is mapped to the persistent volume, so will be /data/metadig/log4j.properties
CMD java -Dlog4j2.formatMsgNoLookups=true -XX:+UnlockExperimentalVMOptions -XX:+UseSerialGC -cp /opt/local/metadig/config:./metadig-engine-$ENGINE_TAG.jar:./solr -Dpython.path=/opt/local/metadig/metadig-py edu.ucsb.nceas.mdqengine.Worker


# Use an OpenJDK runtime as a parent image - This is a Debian distro
FROM openjdk:8-jre-stretch

MAINTAINER slaughter@nceas.ucsb.edu

# Set the working directory
WORKDIR /var/lib/metadig

# This file was created from the https://github.com/NCEAS/metadig-r repo
# and contains R functions that assist in writing R based quality checks.
COPY metadig_0.2.0.tar.gz metadig.tar.gz
#COPY log4j.properties .
# The most recently built jar file is copied from the maven build directory to this dir by maven, so that
# it can be copyied to the image.
COPY metadig-engine.jar metadig-engine.jar
# For some reason, the DataONE indexer software can't find these files unless they are put in
# directory and included in the CLASSPATH (see java CMD). They are in the source tree under 'main/resources',
# but maybe further action needs to be taken for java/dataone to be able to find them without having to
# do this extra step.
COPY solr solr

# DataONE indexer prints copious error msgs if these files don't exist
RUN mkdir -p /etc/dataone/index && touch /etc/dataone/index/d1client.properties && touch /etc/dataone/node.properties && touch /etc/dataone/index/cn-index-processor.properties

# Add R runtime and install packges required by the quality suites
RUN apt update
RUN apt -y install vim bash
RUN apt -y install r-base r-cran-httr r-cran-xml2 r-cran-tidyr r-cran-scales r-cran-lubridate r-cran-ggplot2 r-cran-magrittr
# Debian stretch doesn't have a pre-cooked package for readr, so install now.
RUN Rscript --vanilla -e 'install.packages("readr", repos=c(CRAN = "http://cran.rstudio.com"))'
RUN Rscript --vanilla -e 'install.packages("metadig.tar.gz", repos=NULL)'

# Run the Scorer process
# Note: docker --build-arg only allows one argument (one token only, multiple tokens inside quotes doesn't work, so have
#   to specify java options directly on command line.
# Set classpath to include /opt/local/metadig/log4j.properties, if it exists, so that logging can be changed without
# having to rebuild the container. Note that on k8s, this dir is mapped to the persistent volume, so will be /data/metadig/log4j.properties
CMD java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:+UseSerialGC -cp /opt/local/metadig/config:./metadig-engine.jar:./solr edu.ucsb.nceas.mdqengine.scorer.Scorer


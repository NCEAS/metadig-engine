# Use an OpenJDK runtime as a parent image - This is a Debian distro
FROM openjdk:8-jre-stretch

MAINTAINER slaughter@nceas.ucsb.edu

# Set the working directory
WORKDIR /var/lib/metadig

# This file was created from the https://github.com/NCEAS/metadig-r repo
# and contains R functions that assist in writing R based quality checks.
COPY metadig_0.2.0.tar.gz metadig.tar.gz
COPY log4j.properties .
# The most recently built jar file is copied from the maven build directory to this dir by maven, so that
# it can be copyied to the image.
COPY metadig-engine.jar metadig-engine.jar
# For some reason, the DataONE indexer software can't find these files unless they are put in
# directory and included in the CLASSPATH (see java CMD). They are in the source tree under 'main/resources',
# but maybe further action needs to be taken for java/dataone to be able to find them without having to
# do this extra step.
COPY solr solr

# For testing
#COPY FAIR-scores.csv .
#COPY plot.R .

# DataONE indexer prints copious error msgs if these files don't exist
RUN mkdir -p /etc/dataone/index && touch /etc/dataone/index/d1client.properties && touch /etc/dataone/node.properties && touch /etc/dataone/index/cn-index-processor.properties

# Add R runtime and install packges required by the quality suites
RUN apt update
RUN apt -y install vim
RUN apt -y install r-base
RUN apt -y install r-cran-httr
RUN apt -y install r-cran-xml2
RUN apt -y install r-cran-tidyr
RUN apt -y install r-cran-scales
RUN apt -y install r-cran-lubridate
RUN apt -y install r-cran-ggplot2
RUN Rscript --vanilla -e 'install.packages("readr", repos="https://cran.cnr.berkeley.edu/")'
RUN Rscript --vanilla -e 'install.packages("metadig.tar.gz", repos=NULL)'

# Run the Scorer process
# Note: docker --build-arg only allows one argument (one token only, multiple tokens inside quotes doesn't work, so have
#   to specify java options directly on command line.
CMD java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:+UseSerialGC -cp ./metadig-engine.jar:./solr edu.ucsb.nceas.mdqengine.scorer.Scorer


FROM tomcat:8.5
#FROM tomcat:8.5-alpine

MAINTAINER slaughter@nceas.ucsb.edu

# add context to /usr/local/tomcat/webapps
COPY ./metadig-webapp.war /usr/local/tomcat/webapps/metadig-webapp.war

#EXPOSE 8009
EXPOSE 8080

CMD ["catalina.sh", "run"]


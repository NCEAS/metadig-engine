#!/bin/bash

# Follow these steps in order to enable SSL for solr standalone server.
# From SO: https://stackoverflow.com/questions/41592427/letsencypt-solr-ssl-jvm
# As i have a key for the Domain already, and Solr responds on mydomain.com:8983 all that is needed is to create a Java Key Store (jks) from the existing keys on the system

# Note: Use the password "metadig" when prompted by openssl
sudo openssl pkcs12 -export -in /etc/letsencrypt/live/docker-ucsb-4.dataone.org/fullchain.pem -inkey /etc/letsencrypt/live/docker-ucsb-4.dataone.org/privkey.pem -out pkcs.p12 -name metadig

# specifing the location of the Lets-Encrypt Cert (on my system /etc/letsencrypt/live/mydomain.com/)
# Then convert the PKCS12 key to a jks, replacing password where needed.

# keytool -importkeystore -deststorepass PASSWORD_STORE -destkeypass PASSWORD_KEYPASS -destkeystore keystore.jks -srckeystore pkcs.p12 -srcstoretype PKCS12 -srcstorepass STORE_PASS -alias NAME

sudo keytool -importkeystore -deststorepass metadig -destkeypass metadig -destkeystore keystore.jks -srckeystore pkcs.p12 -srcstoretype PKCS12 -srcstorepass metadig -alias metadig
sudo cp keystore.jks /opt/solr/server/etc/solr-ssl-letsencrypt.keystore.jks 
sudo chown solr /opt/solr/server/etc/solr-ssl-letsencrypt.keystore.jks 
sudo chgrp solr /opt/solr/server/etc/solr-ssl-letsencrypt.keystore.jks 

rm -f keystore.jks

# Now that the keystore has been created, Solr must be told where it is:

#* on docker-ucsb-4, the ’service solr start’ (/etc/init.d/solr) reads from /etc/default/solr.in.sh
#    * these values are currently used
#        * SOLR_SSL_ENABLED=true
#        * # Uncomment to set SSL-related system properties
#        * # Be sure to update the paths to the correct keystore for your environment
#        * SOLR_SSL_KEY_STORE=/opt/solr/server/etc/solr-ssl-letsencrypt.keystore.jks
#        * SOLR_SSL_KEY_STORE_PASSWORD=metadig
#        * SOLR_SSL_KEY_STORE_TYPE=JKS
#        * SOLR_SSL_TRUST_STORE=/opt/solr/server/etc/solr-ssl-letsencrypt.keystore.jks
#        * SOLR_SSL_TRUST_STORE_PASSWORD=metadig
#        * SOLR_SSL_TRUST_STORE_TYPE=JKS
#        * #SOLR_SSL_NEED_CLIENT_AUTH=false
#        * SOLR_SSL_WANT_CLIENT_AUTH=false


# Now restart Solr
sudo service solr restart

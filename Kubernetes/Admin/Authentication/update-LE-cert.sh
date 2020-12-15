#!/bin/bash

# Call the Let's Encrypt certbot which will update our LE certs used by
# k8s, if it determines they need to be updated.
# Note: this script is intended to be run as a root cron job
# Note: this certificate can be checked with the following command:
#     openssl s_client -showcerts -servername api.test.dataone.org -connect api.test.dataone.org:30443
#     - this output should show both the LE cert and the intermediate cert:
#        - 1 s:/C=US/O=Let's Encrypt/CN=Let's Encrypt Authority X3
#        - i:/O=Digital Signature Trust Co./CN=DST Root CA X3

# Note: the user needs to have sudo access to run this script

debug=1

# The user managing k8s
user=metadig
userHome=~metadig
# k8s namespace that we are managing
#k8sns=metadig
k8sns=nginx-ingress

# Save current LE cert modified time so we can see if certbot delivers
# new certs
#domain=`hostname -f`
#domain=api.test.dataone.org
domain=docker-ucsb-4.dataone.org
domainDir=$domain
CA_DIR=/etc/letsencrypt/live/${domainDir}
# Use fullchain.pem, which includes the intermediate certificate, that will allow TLS
# client authentication, for those clients that don't know about LE certs
certFilename=fullchain.pem
certFilepath=${CA_DIR}/$certFilename

privkeyFilename=privkey.pem
privkeyFilepath=${CA_DIR}/$privkeyFilename
certModTime=`sudo stat -c %Y ${certFilepath}`

# Open port 80 so that the certbot can send a challenge which will verify
# that we have control of the IP that it will create the cert for
#certbotHost=acme-v02.api.letsencrypt.org
#certbotIP=`host ${certbotHost} | grep 'has address' | sed 's/.*has address\s*//'`

# Since 2019, certbot uses 'multi-perspective validation', so that they check from
# multiple systems, so open up port 80 to any - we are not able to determine the
# the IP that the certbot request will come from.
sudo ufw allow 80
#sudo ufw allow from ${certbotIP} to any port 80
#/usr/bin/certbot renew -d ${domain} > /var/log/letsencrypt/letsencrypt-renew.log 2>&1
sudo /usr/bin/certbot certonly --standalone -d ${domain} | sudo tee -a /var/log/letsencrypt/letsencrypt-renew.log >/dev/null

# Close the port as soon as certbot is done
sudo ufw delete allow 80
#sudo ufw delete allow from ${certbotIP} to any port 80
sudo ufw status

certModTimeNew=`sudo stat -c %Y ${certFilepath}`

if (( $certModTimeNew > $certModTime )); then
  if (( $debug )); then
    echo "Updating k8s with new cert obtained from Let's Encrypt certbot"
  fi
  # k8s (specifically the nginx ingress) accesses the LE certs as a k8s secret, so we have to renew that
  # with any new certs obtained from certbot
  # see https://docs.bitnami.com/kubernetes/how-to/secure-kubernetes-services-with-ingress-tls-letsencrypt/

  if [ ! -d ${userHome}/tmp ]; then
    mkdir ${userHome}/tmp
  fi

  # Copy files so that metadig user can read them
  sudo cp ${certFilepath} ${userHome}/tmp
  sudo cp ${privkeyFilepath} ${userHome}/tmp
  sudo chown ${user}.${user} ${userHome}/tmp/*.pem

  tmpPrivkeyPath=${userHome}/tmp/$privkeyFilename
  tmpCertpath=${userHome}/tmp/$certFilename
  # These commands have to run as the metadig user, so that the ~metadig/kube/config can be read, which is needed
  # for k8s authorization
  kubectl get secret ${k8sns}-tls-cert --namespace ${k8sns}
  kubectl delete secret ${k8sns}-tls-cert --namespace ${k8sns}
  kubectl create secret tls ${k8sns}-tls-cert --key $tmpPrivkeyPath --cert $tmpCertpath --namespace ${k8sns}
  rm -f $tmpPrivkeyPath
  rm -f $tmpCertpath

  # Note: it is not necessary to restart any k8s services, the TLS certificate will now be used
  # by any services that require it.
else
  if (( $debug )); then
    echo "Let's Encrypt cert not updated by certbot, Not updating k8s with new certfile "
  fi
fi
#!/bin/bash -x

# Call the Let's Encrypt certbot which will update our LE certs used by
# k8s, if it determines they need to be updated.

debug=1

# The user managing k8s
user=metadig
# k8s namespace that we are managing
#k8sns=metadig
k8sns=nginx-ingress

# Save current LE cert modified time so we can see if certbot delivers
# new certs
domain=`hostname -f`
damainDir=$domain
domain=api.test.dataone.org,${domain}
CA_DIR=/etc/letsencrypt/live/${domainDir}
# Use fullchain.pem, which includes the intermediate certificate, that will allow TLS
# client authentication, for those clients that don't know about LE certs
#certFilename=${CA_DIR}/cert.pem
certFilename=${CA_DIR}/fullchain.pem
privkeyFilename=${CA_DIR}/privkey.pem
certModTime=`stat -c %Y ${certFilename}`

# Open port 80 so that the certbot can send a challenge which will verify
# that we have control of the IP that it will create the cert for
#certbotHost=acme-v02.api.letsencrypt.org
#certbotIP=`host ${certbotHost} | grep 'has address' | sed 's/.*has address\s*//'`

# Since 2019, certbot uses 'multi-perspective validation', so that they check from
# multiple systems, so open up port 80 to any - we are not able to determine the
# the IP that the certbot request will come from.
ufw allow 80
#sudo ufw allow from ${certbotIP} to any port 80
#/usr/bin/certbot renew -d ${domain} > /var/log/letsencrypt/letsencrypt-renew.log 2>&1
/usr/bin/certbot renew -d ${domain} > /var/log/letsencrypt/letsencrypt-renew.log 2>&1
# Close the port as soon as certbot is done
ufw delete allow 80
#sudo ufw delete allow from ${certbotIP} to any port 80
ufw status

certModTimeNew=`stat -c %Y ${certFilename}`

if (( $certModTimeNew > $certModTime )); then
  if (( $debug )); then
    echo "Updating k8s with new cert obtained from Let's Encrypt certbot"
  fi
  # k8s (specifically the nginx ingress) accesses the LE certs as a k8s secret, so we have to renew that
  # with any new certs obtained from certbot
  # see https://docs.bitnami.com/kubernetes/how-to/secure-kubernetes-services-with-ingress-tls-letsencrypt/

  #kubectl describe secret metadig-tls-cert --namespace metadig
  if [ ! -d ~${user}/tmp ]; then
    mkdir ~${user}/tmp
  fi
  cp ${certFilename} ~${user}/tmp
  cp ${privkeyFilename} ~${user}/tmp
  chown ${user}.${user} ~${user}/tmp/*.pem

  su ${user} -c "kubectl get secret ${k8sns}-tls-cert --namespace ${k8sns}"
  su ${user} -c "kubectl delete secret ${k8sns}-tls-cert --namespace ${k8sns}"
  #sudo kubectl create secret tls ${k8sns}-tls-cert --key ${CA_DIR}/privkey.pem --cert ${CA_DIR}/cert.pem --namespace ${k8sns}
  #su ${user} -c "kubectl create secret tls ${k8sns}-tls-cert --key ~${user}/tmp/privkey.pem --cert ~${user}/tmp/cert.pem --namespace ${k8sns}"
  su ${user} -c "kubectl create secret tls ${k8sns}-tls-cert --key ~${user}/tmp/privkey.pem --cert ~${user}/tmp/chain.pem --namespace ${k8sns}"
  #su metadig -c "kubectl get secret metadig-tls-cert --namespace metadig"
  rm -f ~${user}/tmp/privkey.pem ~${user}/tmp/cert.pem

  # Note: it is not necessary to restart any k8s services, the TLS certificate will now be used
  # by any services that require it.
else
  if (( $debug )); then
    echo "Let's Encrypt cert not updated by certbot, Not updating k8s with new certfile "
  fi
fi
 Kubernetes commands

export KUBECONFIG=~/.kube/config

function kesh()
{
    # Connect to a running k8s container
    # Have to specify the container for multi container pods
    # Is $2 set to the container to contect to?
    pod=`echo $1 | sed 's/pod\///'`

    if [ -n "$2" ] ; then
        namespace=$2
    else
        namespace="metadig"
    fi

    if [ -n "$3" ] ; then
        kubectl exec -n $namespace -it $pod -c $3 -- /bin/sh
    else
        kubectl exec -n $namespace -it $pod -- /bin/sh
    fi
}

# RabbmitMQ queue length
function rqlen()
{
    #if [ -n "$1" ] ; then
    #    queue=$1
    #else
    #    queue=quality
    #fi
    queue=quality


    # Get address of the RabbitMQ Admin API running inside the container
    # This will be used to get the metadig quality queue length

    addr=`kubectl get service metadig-rabbitmq --namespace=metadig -o wide | grep metadig-rabbitmq | awk '{print $3}'`
    length=`curl -s -u metadig:quality http://${addr}:15672/api/queues/%2F/${queue} | \
        python3 -c "import sys, json; print(json.load(sys.stdin)['messages'])"`

    echo $length
}

function kl()
{
    if [ -n "$2" ] ; then
        namespace=$2
    else
        namespace="metadig"
    fi

    # Is $1 set to the cert to get info for?
    if [ -n "$3" ] ; then
        kubectl logs -n $namespace $1 -c $3
    else
        kubectl logs -n $namespace $1
    fi
}

function klf()
{

    if [ -n "$2" ] ; then
        namespace=$2
    else
        namespace="metadig"
    fi

    # Is $1 set to the cert to get info for?
    if [ -n "$3" ] ; then
        kubectl logs --tail=50 -f -n $namespace $1 -c $3
    else
        kubectl logs --tail=50 -f -n $namespace $1
    fi
}

function kga()
{
    if [ -n "$1" ] ; then
        kubectl get pods,services -n $1 -o wide
    else
        kubectl get pods,services --all-namespaces -o wide
    fi
}

function kgc()
{
    kubectl config get-contexts
}

function kuc()
{
    if [ -n "$1" ] ; then
        kubectl config use-context $1
    else
        kubectl config get-contexts
    fi
}

function kgc()
{
    kubectl config get-contexts
}

# Connect to the pgbouncer connection pooling service
function pgb ()
{
    # The $1 argument is the network address of the metadig/postgres service, i.e. from 'kga' output
    # metadig       service/postgres               ClusterIP   10.100.54.162    <none>        5432/TCP,6432/TCP   3m        app=postgres
    addr=`kubectl get service metadig-postgres --namespace=metadig -o wide | grep 'metadig-postgres' | awk '{print $3}'`
    psql -h $addr -p 6432 -U postgres pgbouncer
}


# Connect to the postgres database
function pg ()
{
    # The $1 argument is the network address of the metadig/postgres service, i.e. from 'kga' output
    # metadig       service/postgres               ClusterIP   10.100.54.162    <none>        5432/TCP,6432/TCP   3m        app=postgres
    addr=`kubectl get service metadig-postgres --namespace=metadig -o wide | grep 'postgres' | awk '{print $3}'`
    psql -h $addr -p 5432 -U postgres postgres
}

# Connect to the postgres database
function pgm ()
{
    # Determine the network address of the metadig/postgres service, i.e. from 'kubectl' output
    # metadig       service/postgres               ClusterIP   10.100.54.162    <none>        5432/TCP,6432/TCP   3m        app=postgres
    addr=`kubectl get service metadig-postgres --namespace=metadig -o wide | grep 'metadig-postgres' | awk '{print $3}'`
    psql -h $addr -p 5432 -U metadig metadig
}

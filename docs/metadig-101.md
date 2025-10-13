# Metadig 101: Deployment, Maintenance, Development

More comprehensive docs are available:

- [Architecture](https://github.com/NCEAS/metadig-engine/blob/main/docs/Architecture.md)
- [Developer Guide](https://github.com/NCEAS/metadig-engine/blob/main/docs/developer-guide.md)
- [Operations Manual](https://github.com/NCEAS/metadig-engine/blob/main/docs/operations-manual.md)


## Deployment

#### Auth

For initial setup with Rancher Desktop, set config env var to point to the config files, eg:

```
KUBECONFIG=/Users/clark/.kube/config:/Users/clark/.kube/config-prod:/Users/clark/.kube/config-dev
```

To set up the cluster secrets, please reach out to support@dataone.org. Access to decrypt the file
the `config-dev.gpg` file in the instructions below is restricted for security reasons.

- Grab fresh copy of `security` repo
- copy `config-dev.gpg` into your local `~/.kube`
- `gpg config-dev` to decrypt the file
- `kubectl config use-context dev-metadig` to set the correct context

### Docker images

Docker images are built automatically for each pushed tag, each push to develop, and each push to a feature branch using GitHub actions. See `metadig-engine/.github/workflows/build.yml`:line 82 for the docker portion of things.

The `metadig-engine` repo builds 3 images (worker, scorer, scheduler). `metadig-webapp` builds the controller image. My preference is that only one version of `metadig-engine` runs on the cluster. Since `metadig-webapp` has `metadig-engine` as a dependency, even if no changes are made to `metadig-webapp`, if `metadig-engine` gets a release, `metadig-webapp` will as well to upgrade the `metadig-engine` dependency to the most recent version.

To accomplish this, each release of `metadig-engine` needs to be pushed to the dataone maven repository using `mvn publish`. Once that is pushed (can also be a snapshot, for pre-release testing), either build and push the controller image to `metadig-webapp`, or push any commit to trigger a GHA build that will build that image with the correct version of `metadig-engine`. My usual workflow for all of this is:

- work normally on metadig engine, pushing commits as I go which automatically build images
- when I need a new `metadig-controller` image, run `mvn publish` on a snapshot from the `metadig-engine` repository
- trigger a build (push a commit to GitHub) on `metadig-webapp` to get the controller image
- `helm upgrade` as described below

### Checks

Checks are deployed from a ceph mount on `datateam` at `/var/data/dev/metadig/metadig` (dev) or `/var/data/apps/metadig/metadig` production.

To deploy a set of checks, from `metadig-checks`:

- modify `build.properties`: line 14 to set the suites to build, and increment metadig-checks version as needed
    - this will likely be arctic-data-center-1.2.0.xml,ess-dive-1.2.1.xml,FAIR-suite-0.4.0.xml,knb-suite.xml, and the data suite, unless a new version of any of the metadata suites are released
- run `ant dist`
- `scp dist/metadig-checks-{version}.tar datateam:/var/data/dev/metadig/metadig`
- ssh into datateam `/var/data/dev/metadig/metadig`
- `tar xvf metadig-checks-{version}.tar`
- restart worker pods?? I can't remember if this step is required or not

### Dev cluster

Update `Chart.yaml` to increment chart version and app version. In `values.yaml` for **each** of the scheduler, scorer, worker, and controller, adjust the `image.tag` value to the value of the iamge tag to deploy. Can also be a branch name.

Set your context to `dev-metadig`.

Run all of these commands from `metadig-engine/helm`. Adjust helm chart version, and number of worker pods, as needed.

```
helm upgrade metadig-scheduler ./metadig-scheduler --namespace metadig --set image.pullPolicy=Always --recreate-pods=true --set k8s.cluster=dev
helm upgrade metadig-scorer ./metadig-scorer --namespace metadig --set image.pullPolicy=Always --recreate-pods=true --set k8s.cluster=dev
helm upgrade metadig-worker ./metadig-worker --namespace metadig --set image.pullPolicy=Always --set replicaCount=1 --recreate-pods=true --set k8s.cluster=dev
helm upgrade metadig-controller ./metadig-controller --namespace metadig --set image.pullPolicy=Always --recreate-pods=true --set k8s.cluster=dev
```

### Production cluster

Update `Chart.yaml` to increment chart version and app version. In `values.yaml` for **each** of the scheduler, scorer, worker, and controller, adjust the `image.tag` value to the value of the tag to deploy.

Set your context to `prod-metadig`.

Again, run from `metadig-engine/helm`. Adjust helm chart version, and number of worker pods, as needed.

```
helm upgrade metadig-scheduler ./metadig-scheduler --namespace metadig --set image.pullPolicy=Always --recreate-pods=true
helm upgrade metadig-scorer ./metadig-scorer --namespace metadig --set image.pullPolicy=Always --recreate-pods=true
helm upgrade metadig-worker ./metadig-worker --namespace metadig --set image.pullPolicy=Always --set replicaCount=20 --recreate-pods=true 
helm upgrade metadig-controller ./metadig-controller --namespace metadig --set image.pullPolicy=Always --recreate-pods=true
```

## Maintenance


### DataONE token renewal

When the token gets renewed, you need to update the long-lived DataONE CN token on production metadig. Here are the steps:

- get a fresh copy of the `security` repo and de-encrypt the secret
- set the context to production
    - `kubectl config use-context prod-metadig`
- delete the existing secret
    - `kubectl delete secret dataone-token -n metadig --ignore-not-found`
- create the new secret
    - `kubectl create secret generic -n metadig --type=opaque dataone-token --from-literal=DataONEauthToken="this is the new value"`
- restart the scorer and scheduler pods
    - `kubectl get pods`
    - `kubectl delete pod/metadig-scorer-{podid}`
    - `kubectl delete pod/metadig-scheduler-{podid}`


You'll then need to rewind the last harvest date for nodes that had quality runs while the token was expired. You can follow the instructions in the [operations manual](https://github.com/NCEAS/metadig-engine/blob/main/docs/operations-manual.md#triggering-a-run-after-restart) to do this. You can use the instructions there, or your knowledge of the token expiration, to set the date to change the last harvest to. Note that you should **only** ever roll back the last harvest date, not forwards.


### RabbitMQ health and restarting metadig

Because of the outstanding [bug on RabbitMQ acking](https://github.com/NCEAS/metadig-engine/issues/448) we still occasionally get closed channels. If metadig seems like it is having problems (ESS-Dive or datateam reports issues sometimes), this is the first thing to check. You can view the RabbitMQ dashboard by running the following (in either prod or dev context, depending on what you are debugging):

```
kubectl port-forward "service/metadig-rabbitmq" 15672
```

and then navigating to localhost:15672 in a browser. The username is metadig, pw is quality.

From here, examine the number of connections, channels, and the number of "ready" messages. If the connections and channels match, and ready is 0, then the system is in good health. If there are many more connections than channels, you should proably restart. Messages are persistent so you don't need to worry about losing messages in the queue.

### Restarting Metadig

This is done by deleting pods, in a particular order. The postgres and solr pods do not need to be restarted, only rabbitmq, scheduler, controller, worker, and scorer.

`kubectl delete pod podname` will restart any given pod. 

`kubectl get -o name pods | grep metadig-worker | xargs -n 1 kubectl delete` will loop through all of the worker pods and delete them (this syntax is helpful for both rabbitmq and worker pods).

If you think metadig missed tasks, follow the instructions for [triggering a run after restart](https://github.com/NCEAS/metadig-engine/blob/main/docs/operations-manual.md#triggering-a-run-after-restart). This is usually not necessary, but might be.


#### Increase worker pods

For normal operations, 20 worker pods is plenty. For larger tasks (like a big harvest rollback, or launching a new suite) you might need to increase the number of worker pods.

The number of pods should not exceed 100 or it will exceed the number of available postgres connections.

```
kubectl scale deployment metadig-worker --replicas=50
```

## Development

The most comprehensive source of documentation for development is in the [developer guide](https://github.com/NCEAS/metadig-engine/blob/main/docs/developer-guide.md), but I'll add some tips here.

### Dependencies

- `metadig-engine`
- `metadig-checks`
- `metadig-webapp`
- [`metadig-R`](https://github.com/NCEAS/metadig-r/)
- [`metadig-py`](https://github.com/NCEAS/metadig-py)
- Jep (it should install with metadig-py, but just in case `pip install jep`)

### Local Configuration

You'll need to have `/opt/local/metadig` set up similarly to the mount on `datateam` at `/var/data/dev/metadig/metadig`.

The key files are

- metadig.properties
    - make a copy from `metadig-engine/helm/metadig-controller/config-dev/metadig.properties` and change the values as needed for your local setup. See my copy below
- taskList.csv
    - copied from `metadig-engine/helm/metadig-scheduler/config-dev/taskList.csv`
- checks and suites
    - from metadig checks, run the steps for deploying checks above, but copy them to your `/opt/local/metadig` instead of to the sever
- other data files
    - these are handled by the above step for the checks and suites

```
#
# metadig.properties file for the development k8s cluster
#
mdq.store.directory = /opt/local/metadig
DataONE.authToken = 
CN.subjectId = CN=urn:node:CN,DC=dataone,DC=org
CN.serviceUrl = https://cn.dataone.org/cn
cnStage.subjectId = CN=urn:node:cnStage,DC=dataone,DC=org
cnStage.serviceUrl = https://cn-stage.test.dataone.org/cn 
mnTestARCTIC.subjectId = CN=urn:node:mnTestARCTIC,DC=dataone,DC=org
mnTestARCTIC.serviceUrl = https://test.arcticdata.io/metacat/d1/mn
ARCTIC.serviceUrl = https://arcticdata.io/metacat/d1/mn
RabbitMQ.host = localhost
RabbitMQ.port = 5672
RabbitMQ.username = guest
RabbitMQ.password = guest
solr.location = http://localhost:8983/solr/
jdbc.url = jdbc:postgresql://localhost:5432/metadig
postgres.user = metadig
postgres.passwd = metadig
task.file = /opt/local/metadig/taskList.csv
quality.serviceUrl = http://metadig-controller.metadig.svc:8080/quality
metadig.base.directory = /opt/local/metadig
metadig.store.directory = /opt/local/metadig/store
index.latest = false
metadig.data.dir = /opt/local/metadig/data
bookkeeper.enabled = false
# CN stage short-lived token for Peter's ORCID
bookkeeper.authToken =
bookkeeper.url = http://bookkeeper.bookkeeper.svc.cluster.local:8080/bookkeeper/v1
quartz.monitor = true
quartz.monitor.schedule = 0 * * * * ?
quartz.monitor.processing.time = 24
quartz.monitor.run.limit = 8
jep.path = /Users/clark/.virtualenvs/jep/lib/python3.10/site-packages/jep
store.store_type = HashStore
store.store_path = /Users/clark/Documents/metacat-hashstore
store.store_depth = 3
store.store_width = 2
store.store_algorithm = SHA-256
store.store_metadata_namespace = https://ns.dataone.org/service/types/v2.0#SystemMetadata

```

You'll want to pay special attention to the `jdbc.url` and `jep.path`

### Java Debugger in VS Code

The easiest way to step through individual lines is to use the VS Code debugger with the config I've set up. I'm sure this can be modified for IntelliJ relatively easily.

- from the command palette, Debug: Select Debug Session
- Start new session
- Run MDQEngine

This is best for the worker code and any related parts. Most data quality work has been on the worker and dispatchers, so I've been using this debugging configuration quite a lot.

VS Code launch file:

```
       {
            "name": "Run MDQEngine",
            "type": "java",
            "request": "launch",
            "mainClass": "edu.ucsb.nceas.mdqengine.MDQEngine",
            "args": [
                "/opt/local/metadig/suites/arctic-data-center-1.2.0.xml",
                "${workspaceFolder}/src/test/resources/test-docs/doi:10.18739_A2KS6J63D.xml",
                "${workspaceFolder}/src/test/resources/test-docs/doi:10.18739_A2KS6J63D.sm",
            ],
            "cwd": "${workspaceFolder}"
        }

```

From terminal:

```
/usr/bin/env /Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin/java -agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=localhost:56055 @/var/folders/xp/vcwlmnrj5gg646dbp2c1fkh00000gq/T/cp_cc5ctz50q7ewq62pd7cfg7lpt.argfile edu.ucsb.nceas.mdqengine.MDQEngine /opt/local/metadig/suites/arctic-data-center-1.2.0.xml /Users/clark/Documents/metadig/metadig-engine/src/test/resources/test-docs/doi:10.18739_A2KS6J63D.xml /Users/clark/Documents/metadig/metadig-engine/src/test/resources/test-docs/doi:10.18739_A2KS6J63D.sm 
```

### Integrated local testing

For local testing with RabbitMQ see the instructions in [the developer guide](https://github.com/NCEAS/metadig-engine/blob/main/docs/developer-guide.md#running-locally-with-rabbitmq-message-queuing).

#### Setting up a hashstore

Dou could spin this up very quickly but here is what I did to get a set of complete packages with metadata that I could use for testing.

First create a hashstore (after installing):

`hashstore /home/clark/Documents/metacat-hashstore -chs -dp=3 -wp=2 -ap=SHA-256 -nsp="https://ns.dataone.org/service/types/v2.0#SystemMetadata"`

Then this R script will download 10 data packages into a temporary directory, and write a bash script that you can run to add all of the individual items to the store. (Sorry for the hardcoded absolute paths, I didn't think anyone might ever use this)

```
library(dataone)
library(arcticdatautils)

mn <- getMNode(CNode("PROD"), "urn:node:ARCTIC")

result <- query(mn, list(q = 'formatType:RESOURCE',
                          fl = 'identifier,submitter,fileName',
                          sort = 'dateUploaded+desc',
                          rows='10'),
                as = "data.frame")

for (z in 1:nrow(result)){
    ids <- get_package(mn, result$identifier[z], file_names = TRUE)
    pids <- unlist(ids)
    
    for (i in 1:length(pids)){
        fp <- paste0("~/Documents/metacat_temp/data/", gsub("/", "%20", pids[i]))
        obj <- getObject(mn, pids[i])
        writeBin(obj, fp)
        
        sys <- getSystemMetadata(mn, pids[i])
        t <- datapack::serializeSystemMetadata(sys, version = "v2")
        fp <- paste0("~/Documents/metacat_temp/metadata/", gsub("/", "%20", pids[i]))
        writeLines(t, fp)
    
    }
    rm(ids, pids)
}


mp <- dir("~/Documents/metacat_temp/metadata/", full.names = TRUE)
dp <- dir("~/Documents/metacat_temp/data/", full.names = TRUE)

pids <- dir("~/Documents/metacat_temp/data/") %>% gsub("%20", "/", .)


for (i in 1:length(pids)){
    write(paste0("hashstore ~/Documents/metacat-hashstore -storeobject -pid=",pids[i], " -path=", dp[i]), "run.sh", append = TRUE)
    write(paste0("hashstore ~/Documents/metacat-hashstore -storemetadata -pid=",pids[i], " -path=", mp[i]), "run.sh", append = TRUE)
    
}
```

Once you've run that, just run the `run.sh` script that just got written.

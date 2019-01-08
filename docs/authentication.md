# Kubernetes Authentication

MetaDIG services running on the Kubernetes (k8s) cluster located at https://docker-ucsb-1.dataone.org use the k8s Role Based Access Control (RBAC) mechanism. RBAC is used to authenticate users logged into the cluster nodes and determine what operations a user can perform such as starting, stopping and viewing services and deployments.

## RBAC overview

Authentication in k8s RBAC can be based on X509 certificates, OAuth2, or tokens. For the MetaDIG k8s cluster, X509 certificates are used.

RBAC uses the k8s objects 'subjects', 'verbs' and 'resources' to control access.

A subject can be specified as either "User" or "Group". k8s does not have an object that corresponds to a user or group, instead these values are obtained from the k8s context, which is described in "MetaDIG Configuration Context". 

Resources are the k8s objects a user can manipulate, such as 'pods', 'persistenVolumes', 'configMaps', 'deployments', 'secrets', 'namespaces', 'replicasets', 'roles', 'roleBindings', 'services'

Verbs are the actions that can be applied to a resource, such as 'create', 'get', 'delete', 'list', 'update', 'edit', 'watch', 'exec'

## Roles

A role associates a role name with a set of permissions and verbs. A role can only be applied to a single namespace. 

The `metadig` namespace has the role `metadig-all`, as shown in the file `metadig-role.yaml`:

```
kind: Role
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  namespace: metadig
  name: metadig-all
rules:
- apiGroups: ["*"]
  resources: ["*", "*/*"] # Have to specify that all resources and all subresources are included
  verbs:     ["*"]
```
  
The `metadig-all` role allows all verbs on all user accessible resource types in the `metadig` namespace.

The `metadig-all` role is created with the command:

```
kubectl create -f metadig-role.yaml
```

## Role Bindings

A role binding grants the permissions specified in a role to a user or user group.

The role binding used in the `metadig` namespace is contained in the file `metadig-role-binding.yaml`, and references the

```
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: metadig-all-roleBinding
  namespace: metadig
subjects:
- kind: User # can also specify "Group"
  name: metadig 
  apiGroup: rbac.authorization.k8s.io
roleRef:
  kind: Role
  name: metadig-all
  apiGroup: rbac.authorization.k8s.io
  
```
The `metadig-roleBinding` role binding is created with the command:

```
kubectl create -f metadig-role-binding.yaml
```

## X509 Certificate Based Authentication For k8s

When the k8s cluster was initially configured, a self signed certificate was created for the cluster in /etc/kubernetes/pki/CA.key, which serves as the certificate authority for the cluster. This certificate is the root certificate that user certificates are chained to.  For example, the certificate used for the `metadig` user (and context) is created with the following commands:

- Generate a private key for the metadig context:

```bash
openssl genrsa -out metadig.key 2048
```
- Generate the certificate signing request:

```
openssl req -new -key metadig.key -out metadig.csr -subj "/CN=metadig/O=nceas"
```

- Generate the metadig certificate:

```
sudo openssl x509 -req -in metadig.csr -CA /etc/kubernetes/pki/ca.crt -CAkey /etc/kubernetes/pki/ca.key -CAcreateserial -out metadig.crt -days 500
```

**Note: In X509 certificate based authentication for k8s, the "User" referenced by k8s API corresponds to the "Common Name" (CN) specified in the user certificate (metadig.crt) and the `Group` corresponds to the "Organization" (O) field.**

This certificate will be used when the k8s 'context' is set for a login session, as described in the section "Setting The User Context"

## MetaDIG Configuration Context

The k8s configuration file ~/.kube/config contains authentication information that is made available to the k8s via the client program `kubectl`. Multiple contexts can be defined for one configuration file, each one associated with a context name, for example `metadig` is the default
context name for the `metadig` user.

The `metadig` context was created with the following steps:

- Create a user entry in the k8s configuration associated with the user `metadig`:

```
kubectl config set-credentials metadig --client-certificate=~/.kube/metadig.crt  --client-key=~/.kube/metadig.key
```

- The `metadig` context is added to the configuration with the command:

```
kubectl config set-context metadig --cluster=kubernetes --namespace=metadig --user=metadig
```

Now this context is available for use:

```
kubectl config use-context metadig
```

The `current-context` field is set in the config file by this command, so that the context will remain in effect across Linux logins.

The `user` specified in the context is used to determine which role bindings are in effect. In this case, the user `metadig` is associated with the role bindings `metadig-all-roleBinding`, which references the role `metadig-all`, which provides access to all verbs for all resources in the `metadig` namespace.

## ClusterRoles

A ClusterRole differs from the Role already described as it applies to resources across namespaces. ClusterRoles can be used for the same resources used with Roles, but in addition can be applied to cluster-wide resources such as nodes.

ClusterRoles are not currently used by MetaDIG services.

## ClusterRoleBindings

ClusterRoleBindings are used to apply the rules from ClusterRoles cluster-wide.

ClusterRoleBindings are not currently used by MetaDIG services.

## ServiceAccounts

ServiceAccounts provide an identity for pods that require access to the k8s API after the pod has been created. In contrast, "User" and "Group" authentication (in the MetaDIG case via X509 certificates) provides access to the k8s API to the user performing operations such as creating, deleting, listing resources.

Roles and RoleBindings can be applied to ServiceAccounts to define the access they will have.

A ServiceAccount can be defined for a namespace and assigned to a pod. If one is not assigned when a pod is created, the 'default' ServiceAccount will be assigned.

A `metadig` ServiceAccount is currently defined, but is using just the default permissions, as no k8s API operations are performed by MetaDIG services.











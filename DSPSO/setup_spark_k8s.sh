# Create namespaces.
kubectl create namespace spark-operator
kubectl create namespace spark-jobs

# Create service accounts and role bindings.
kubectl create serviceaccount spark --namespace=spark-operator
kubectl create clusterrolebinding spark-operator-role --clusterrole=edit --serviceaccount=spark-operator:spark --namespace=spark-operator

# Install the operator with values.yaml.
helm install pso spark-operator/spark-operator -f ./kubernetes/values.yaml --namespace spark-operator
# Create namespaces.
kubectl create namespace spark-operator
kubectl create namespace spark-jobs

# Create service accounts and role bindings.
kubectl create serviceaccount spark --namespace=spark-operator
kubectl create clusterrolebinding spark-operator-role --clusterrole=edit --serviceaccount=spark-operator:spark --namespace=spark-operator

# Apply webhook support.
kubectl apply -f kubernetes/spark-operator-webhook.yaml

# Install the operator with values.yaml.
helm install pso spark-operator/spark-operator -f ./kubernetes/values.yaml --namespace spark-operator

# Create volumes.
kubectl apply -f kubernetes/local-pv.yaml --namespace=spark-jobs
kubectl apply -f kubernetes/local-pvc.yaml --namespace=spark-jobs
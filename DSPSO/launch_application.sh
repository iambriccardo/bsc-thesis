# Delete old application if exists.
kubectl delete -n spark-jobs sparkapplications.sparkoperator.k8s.io dpso

# Launch application.
kubectl apply -f kubernetes/dpso.yaml --namespace=spark-jobs

# We wait for 20 seconds just to give time to the driver pod to start.
sleep 20

# Expose spark ui.
kubectl port-forward service/dpso-ui-svc 4040:4040 --namespace=spark-jobs
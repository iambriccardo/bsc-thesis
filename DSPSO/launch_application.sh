# Launch application.
kubectl apply -f kubernetes/dpso.yaml --namespace=spark-jobs

sleep 10

# Expose spark ui.
kubectl port-forward service/dpso-ui-svc 4040:4040 --namespace=spark-jobs
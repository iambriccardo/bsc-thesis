echo "Choose variant of the algorithm [sync|async]:"
read VARIANT

# Delete old application if exists.
kubectl delete -n spark-jobs sparkapplications.sparkoperator.k8s.io dpso

# Launch application.
if [[ "$VARIANT" == "sync" ]]; then
  kubectl apply -f kubernetes/sync_dpso.yaml --namespace=spark-jobs
elif [[ "$VARIANT" == "async" ]]; then
  kubectl apply -f kubernetes/async_dpso.yaml --namespace=spark-jobs
fi

# We wait for 20 seconds just to give time to the driver pod to start.
sleep 20

# Expose spark ui.
kubectl port-forward service/dpso-ui-svc 4040:4040 --namespace=spark-jobs
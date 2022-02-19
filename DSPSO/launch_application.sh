echo "Choose variant of the algorithm [normal|sync|async]:"
read VARIANT

minikube mount /Users/riccardobusetti/mnt/data:/data &

kubectl delete -n spark-jobs sparkapplications.sparkoperator.k8s.io normal-pso
kubectl delete -n spark-jobs sparkapplications.sparkoperator.k8s.io sync-dpso
kubectl delete -n spark-jobs sparkapplications.sparkoperator.k8s.io async-dpso

# Launch application.
if [[ "$VARIANT" == "normal" ]]; then
  kubectl apply -f kubernetes/normal-pso.yaml --namespace=spark-jobs
elif [[ "$VARIANT" == "sync" ]]; then
  kubectl apply -f kubernetes/sync-dpso.yaml --namespace=spark-jobs
elif [[ "$VARIANT" == "async" ]]; then
  kubectl apply -f kubernetes/async-dpso.yaml --namespace=spark-jobs
fi

# We wait for 20 seconds just to give time to the driver pod to start.
# sleep 20

# Expose spark ui.
# kubectl port-forward service/$VARIANT-dpso-ui-svc 4040:4040 --namespace=spark-jobs
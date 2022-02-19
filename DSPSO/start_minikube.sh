# Delete existing minikube.
minikube delete

# Start minikube with 4 cpus and 8gbs of ram.
minikube start --cpus 8 --memory 8192

# Start the dashboard.
minikube dashboard

# Wait for the processes to finish.
wait
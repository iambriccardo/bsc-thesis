# Delete existing minikube.
minikube delete

# Start minikube with 4 cpus and 8gbs of ram.
minikube start --cpus 4 --memory 8192

# In parallel mount a volume and start the dashboard.
minikube mount /Users/riccardobusetti/mnt/data:/data &
minikube dashboard

# Wait for the processes to finish.
wait
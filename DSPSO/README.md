# Command for launching Spark on the local machine

## Local deployment
spark-submit \
--class MainKt \
--master local[*] \
./target/mainModule-1.0-shaded.jar <sync|async>

## K8S cluster deployment
$SPARK_HOME/bin/spark-submit \
--master k8s://https://kubernetes.docker.internal:6443 \
--deploy-mode cluster \
--name spark-pso \
--class MainKt \
--conf spark.executor.instances=5 \
--conf spark.kubernetes.container.image=riccardobusetti/spark:spark-3.2.0 \
--conf spark.kubernetes.authenticate.driver.serviceAccountName=spark \
local:///opt/spark/jars/mainModule-1.0-shaded.jar
# Command for launching Spark on the local machine

## Local deployment
spark-submit \
--class MainKt \
--master local[*] \
./target/mainModule-1.0-shaded.jar <sync|async>

## K8S cluster deployment

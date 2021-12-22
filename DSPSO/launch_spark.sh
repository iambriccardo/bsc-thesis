spark-submit \
--class MainKt \
--master local[*] \
./target/mainModule-1.0-shaded.jar sync 10 5 2 results/best-position.txt false
spark-submit \
--class MainKt \
--master local[*] \
./target/mainModule-1.0-shaded.jar --async --fitness-eval-delay=0 --local-master --iterations=100 --particles=10 --super-rdd-size=5 --fog-nodes=10 --modules=10 --result-path=results/sync-best-position.txt --keep-alive
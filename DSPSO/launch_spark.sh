spark-submit \
--class MainKt \
--master local[*] \
./target/mainModule-1.0-shaded.jar --normal --fitness-eval-delay=100 --local-master --iterations=10 --particles=100 --fog-nodes=100 --modules=100 --result-path=results/normal-best-position.txt --keep-alive
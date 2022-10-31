# Distributed Particle Swarm Optimization: Design of Synchronous and Asynchronous Algorithms for Optimization Problems at The Edge

This repository contains the code, the presentation and the pdf of the thesis I did for my BSc in Computer Science. I have graduated with 110L/110 on the 22nd July 2022.

## Abstract

The growth and wide adoption of edge computing have introduced several issues such as load balancing, resource provisioning, and workload placement which are all developing as optimization problems. An algorithm, in particular, was successful in solving some of these optimization problems, namely PSO. Particle Swarm Optimization (PSO) is a natureinspired stochastic optimization algorithm that emulates the behavior of bird flocks, whose objective is to iteratively improve the solution of a problem over a given objective. The execution of PSO at the edge would result in the offload of resource-intensive computational tasks from the cloud to the edge, leading to more efficient use of existing resources. However, it would also introduce several performance and fault tolerance challenges due to the resource-constrained environment with a high probability of faults. Given the benefits and the challenges above, the need for a new PSO algorithm to be efficiently executed in an edge network arises. This thesis introduces multiple distributed synchronous and asynchronous variants of the PSO algorithm implemented with the Kotlin programming language, built on the Apache Spark distributed computing framework and Kubernetes container orchestration platform. These algorithm variants aim to address the performance and fault tolerance problems introduced by the execution in an edge network. Moreover, they want to provide a greater level of scalability. By designing a PSO algorithm that distributes the load across multiple executor nodes, effectively realizing both coarse- and fine-grained parallelism, we can significantly increase the algorithms’ performance, fault tolerance, and scalability. The experimental results show the benefits of the proposed distributed variants of the PSO algorithm versus a baseline, the traditional PSO, testing different values of specific configuration parameters of the algorithms.

## Technologies Used

The work in the thesis used the following technologies:
* Kotlin
* Kotlin coroutines
* Apache Spark
* Kubernetes
* Spark on K8S Operator
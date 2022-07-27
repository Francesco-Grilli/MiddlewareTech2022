# Middleware Technologies for Distributed Systems project
This project was developed as main assignment for the course of Middleware Technologies for Distributed Systems at Politecnico di Milano during academic year 2021-22

## Index

- [Tested configuration](#tested-configuration)
- [System setup](#system-setup)
- [Contributors](#contributors)

## Tested setup
The project has been carefully tested under the following configuration; all the system setup information, refers to the configuration which is mentioned hereby: 
- A virtual machine for the ContikiNG part (running on a Linux machine) <!-- Improve this description -->
- A Windows computer to run the MPI simulation <!-- Improve this description -->
- A Windows computer to host Node-RED, Spark and Kafka sections.

## System setup
<!-- Insert explanations to setup Contiki and MPI parts -->

### Node-RED
Node-RED was run natively on a Windows machine; to install it, follow the instructions provided on the Node-RED [website](https://nodered.org/docs/getting-started/local). You should be able to run it, then, just by inserting the `node-red` command.

### Spark
Due to this technology's limitations, Spark was run on WSL, instead of natively on the machine. A good introduction to Apache Spark on WSL can be found [here](https://nicolosonnino.it/spark-on-wsl/). 
<!-- Insert commands to launch Spark -->

### Kafka
Apache Kafka broker, instead, was run on a Docker environment as the project uses Kafka's log compaction feature, which on Windows tends to throw errors upon cleaning. A full guide to its installation can be found [here](https://www.youtube.com/watch?v=Zq8aMrRnvQE).
Following the aforementioned guide, the broker was launched starting the powershell in the downloaded directory and providing the command 

`docker-compose -f .\zk-single-kafka-single.yml up`

The program can be then gently shut down pressing `Ctrl + C`.

## Contributors
- [Gibellini Federico](https://github.com/gblfrc)
- [Grilli Francesco](https://github.com/Francesco-Grilli)
- [Mannarino Andrea](https://github.com/AndreaMannarino)

# Noise data production and analysis
This project was developed as main assignment for the course of Middleware Technologies for Distributed Systems at [Politecnico di Milano](https://polimi.it) during academic year 2021-22.

## Index

- [Documents](#documents)
- [Tested configuration](#tested-configuration)
- [System setup](#system-setup)
- [Troubleshooting](#troubleshooting)
- [Contributors](#contributors)

## Documents

Project-related documents are available at the following links:
- [Specifications](https://github.com/Francesco-Grilli/MiddlewareTech2022/blob/main/Middleware%20Technologies%20Projects%202022.pdf)
- Documentation

## Tested configuration
The project has been carefully tested under the following configuration; all the system setup information, thus, refers to the configuration which is mentioned hereby: 
- A virtual machine for the ContikiNG part (running on a Linux machine)
- A Windows computer to run the MPI simulation
- A Windows computer to host Node-RED, Spark and Kafka sections.

## System setup
<!-- Insert explanations to setup Contiki-->

###MPI
Message Passing Interface (MPI) is a portable message-passing standard designed to function on parallel computing architectures. There are several open source implementation for instance we have chosen Open MPI which is open source: [website](https://www.open-mpi.org/).
In order to compile and run program for MPI on Windows you need to download WSL and follow the instruction on the official website.
To launch the program:
- Compile the code using the command: mpic++ -o main Main.cpp Simulator.cpp Simulator.h SimulationParameters.h -lmosquitto
- Run the program with the parameters requested: mpirun -np 2 ./main 1000 1000 16 16 60 80 2 4 5.0 14.0 15 100 41.903641 12.466195

The compiling process require the installation of the mosquitto library from the official site [website](https://mosquitto.org/download/) and the linking of it.
While running the process you can specify the number of processes to allocate.

To shut down the system:
- Press 'Ctrl + C' in the WSL terminal


### Node-RED
Node-RED was run natively on a Windows machine; to install it, follow the instructions provided on the Node-RED [website](https://nodered.org/docs/getting-started/local). You should be able to run it, then, just by typing the `node-red` command in the `cmd` shell.
To stop the program, just press `Ctrl + C` from the shell in which Node-RED is running.

### Spark
Due to this technology's limitations, Spark was run on WSL, instead of natively on the machine. A good introduction to Apache Spark on WSL can be found [here](https://nicolosonnino.it/spark-on-wsl/). 
To launch the Spark program from the WSL, head to Spark's directory, then:
- Launch a master with the command: `./sbin/start-master.sh`;
- Launch a worker with the command: `./sbin/start-worker.sh spark://localhost:7077`[^spark-master];
- Submit the project jar with the command: 

`./bin/spark-submit --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.2.1 --class it.polimi.mwtech2022.project1.DataAnalysis /path/to/project/target/project1-spark-1.0.jar`[^spark-submit]

To shut the system down:
- Press `Ctrl + C` in the WSL terminal from which you submitted the project;
- Stop the worker with the command: `./sbin/stop-worker.sh spark://localhost:7077`[^spark-master];
- Stop the master with the command: `./sbin/start-master.sh`;

### Kafka
Apache Kafka broker, instead, was run on a Docker environment as the project uses Kafka's log compaction feature, which on Windows tends to throw errors upon cleaning. A full guide to its installation can be found [here](https://www.youtube.com/watch?v=Zq8aMrRnvQE).
Following the aforementioned guide, the broker was launched starting the powershell in the downloaded directory and providing the command 

`docker-compose -f .\zk-single-kafka-single.yml up`

The program can be then gently shut down pressing `Ctrl + C`.

## Troubleshooting
- In case you haven't deployed the Spark master with the default configuration, the project couldn't be submitted; that's due to the fact that the project gets the path to the Spark master from its own configuration file, called `settings.json`. To fix this issue, just replace the Spark master's location in the configuration file with your actual deployment location.

## Contributors
- [Gibellini Federico](https://github.com/gblfrc)
- [Grilli Francesco](https://github.com/Francesco-Grilli)
- [Mannarino Andrea](https://github.com/AndreaMannarino)

[^spark-master]: Spark master's location depends on your Spark settings; here the default settings were used, so the master was started at `localhost:7077`
[^spark-submit]: Replace `/path/to/project/` with the actual path of the project directory

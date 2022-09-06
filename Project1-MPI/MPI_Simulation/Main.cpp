#include <time.h>
#include <cstdlib>
#ifdef _WIN32
#include <Windows.h>
#else
#include <unistd.h>
#endif
#include "Simulator.h"
#include <thread>
#include <chrono>



int main(int argc, char* argv[]) {

	int rank, size;


	MPI_Init(&argc, &argv);

	MPI_Comm_size(MPI_COMM_WORLD, &size);
	MPI_Comm_rank(MPI_COMM_WORLD, &rank);

	/*
	* Initialize the struct and get the parameters from argv.
	* If they are not correctly set, the value of each parameter is set by default
	*/

	simulationParameters parameters{};

	parameters.width = argc > 0 ? atoi(argv[1]) : 1000;
	parameters.length = argc > 1 ? atoi(argv[2]) : 1000;
	parameters.numberPeople = (argc > 2 ? atoi(argv[3]) : 1000) / size;
	parameters.numberCars = (argc > 3 ? atoi(argv[4]) : 1000) / size;
	parameters.noisePerPerson = argc > 4 ? atoi(argv[5]) : 60;
	parameters.noisePerCar = argc > 5 ? atoi(argv[6]) : 80;
	parameters.distanceAffectPeople = argc > 6 ? atoi(argv[7]) : 15;
	parameters.distanceAffectCar = argc > 7 ? atoi(argv[8]) : 25;
	parameters.movingSpeedPeople = argc > 8 ? atof(argv[9]) : 5.0;
	parameters.movingSpeedCar = argc > 9 ? atof(argv[10]) : 14.0;
	parameters.timeStep = argc > 10 ? atof(argv[11]) : 10;
	parameters.granularity = argc > 11 ? atoi(argv[12]) : 10;
	parameters.latitude = argc > 12 ? atof(argv[13]) : 41.903641;
	parameters.longitude = argc > 13 ? atof(argv[14]) : 12.466195;
	parameters.rank = rank;
	parameters.size = size;

	/*Each process will istantiate a Simulator object with all parameters set*/
	Simulator s(parameters);

	/*
	* Infinite loop that perform 3 main operation:
	*	1- calculateNoise() is the real simulation. Each process will simulate the noise of each car and people and
	*		will produce a matrix of noise of the entire area
	*	2- recomputePosition() each process will recompute the position of every car and people inside the simulation
	*	3- gatherNoises() all matrices will be gathered and added together and for each granular area will send the 
	*		noise to an MQTT broker
	* At the end each process will sleep for the timeStep selected by the parameters
	*/
	while (true) {
		s.calculateNoise();
		s.recomputePosition();
		s.gatherNoises();
		usleep(parameters.timeStep * 1000000);
	}
	

	MPI_Finalize();

	return 0;

}
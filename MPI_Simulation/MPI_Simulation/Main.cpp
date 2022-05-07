#include <time.h>
#include <cstdlib>
#ifdef _WIN32
#include <Windows.h>
#else
#include <unistd.h>
#endif
#include "Simulator.h"



int main(int argc, char* argv[]) {

	int rank, size;


	MPI_Init(&argc, &argv);

	MPI_Comm_size(MPI_COMM_WORLD, &size);
	MPI_Comm_rank(MPI_COMM_WORLD, &rank);

	//initialize random seed with the rank for each process
	simulationParameters parameters{};

	if (argc >= 12) {
		//getting parameters from the command line
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
	}
	else {
		std::cout << "ERROR: NOT ENOUGH PARAMETERS\n\n";
		return 0;
	}
	parameters.rank = rank;
	parameters.size = size;

	Simulator s(parameters);

	s.calculateNoise();
	//s.recomputePosition();
	s.gatherNoises();
	/*s.calculateNoise();
	s.recomputePosition();
	s.gatherNoises();*/

	//some printing debug
	/*printf("I am process %d out of %d\n", rank, size);
	printf("Number of cars %d:	Number of People: %d\n", numberCars, numberPeople);
	printf("Width: %d	Length: %d\n", width, length);
	printf("RandomNumber: %d", rand());*/
	

	MPI_Finalize();

	return 0;

}
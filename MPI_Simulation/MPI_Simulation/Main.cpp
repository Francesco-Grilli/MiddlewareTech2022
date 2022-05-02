#include <mpi.h>

#include <time.h>
#include <iostream>
#include <cstdlib>
#include <vector>

#ifdef _WIN32
#include <Windows.h>
#else
#include <unistd.h>
#endif

#include "Simulator.h"
#include <math.h>



int main(int argc, char* argv[]) {

	int rank, size;
	

	//getting parameters from the command line
	const int width = argc>0 ? atoi(argv[1]) : 1000;
	const int length = argc>1 ? atoi(argv[2]) : 1000;
	int numberPeople = argc > 2 ? atoi(argv[3]) : 1000;
	int numberCars = argc > 3 ? atoi(argv[4]) : 1000;
	

	MPI_Init(&argc, &argv);

	MPI_Comm_size(MPI_COMM_WORLD, &size);
	MPI_Comm_rank(MPI_COMM_WORLD, &rank);

	//initialize random seed with the rank for each process
	

	Simulator s(rank, numberPeople, numberCars, width, length, size);
	s.calculateNoise();
	s.gatherNoises();

	//some printing debug
	/*printf("I am process %d out of %d\n", rank, size);
	printf("Number of cars %d:	Number of People: %d\n", numberCars, numberPeople);
	printf("Width: %d	Length: %d\n", width, length);
	printf("RandomNumber: %d", rand());*/
	

	MPI_Finalize();

	return 0;

}
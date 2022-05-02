#include <iostream>
#include <utility>
#include <vector>
#include <mpi.h>
#include <math.h>


class Simulator
{

private:

	int rank;
	int numberPeople;
	int numberCars;
	const int width;
	const int length;
	int sizeProcess;

	MPI_Op SUM_DECIBEL;
	
	//Struct element that contains the information for the simulation of cars and people
	struct element {
		float posX;
		float posY;
		std::pair<float, float> speed;
	};

	//array that contains people and cars
	std::vector<element> cars;
	std::vector<element> people;

	//2d matrix for the noises
	float** noises;

	void initializeVariables();


public:

	Simulator(int rank, int numberPeople, int numberCars, const int width, const int length, int sizeProcess);
	~Simulator();

	void calculateNoise();
	void gatherNoises();

	static void sumOfNoises(void* inputBuffer, void* outputBuffer, int* len, MPI_Datatype* datatype);

};


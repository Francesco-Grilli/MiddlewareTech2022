#include <iostream>
#include <utility>
#include <vector>
#include <mpi.h>
#include <math.h>
#include "SimulationParameters.h"
#include "mosquitto.h"


class Simulator
{

private:

	//All variable needed for the simulation
	simulationParameters parameters;
	
	bool definedOperation;


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
	void initializePeopleCars();
	void mosquittoInit();
	inline float sumOfNoises(float n1, float n2);
	


public:

	Simulator(simulationParameters parameters);
	~Simulator();

	void calculateNoise();
	void gatherNoises();

	void recomputePosition();

	static void sumOfNoises(void* inputBuffer, void* outputBuffer, int* len, MPI_Datatype* datatype);

};


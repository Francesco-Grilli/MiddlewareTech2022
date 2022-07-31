#include <iostream>
#include <utility>
#include <vector>
#include <mpi.h>
#include <math.h>
#include <sstream>
#include <chrono>
#include "SimulationParameters.h"
#include "mosquitto.h"
#include <iomanip>

#define M_PI 3.14159265358979323846


class Simulator
{

private:

	/*All variables needed for the simulation*/
	simulationParameters parameters;
	
	/*Boolean that check if the new MPI_Op has been correctly defined*/
	bool definedOperation;

	/*New MPI_Op function that sum correctly decibels*/
	MPI_Op SUM_DECIBEL;
	
	/*Struct that contains the position and the speed of cars and people inside the simulation area*/
	struct element {
		double posX;
		double posY;
		std::pair<double, double> speed;
	};

	std::vector<element> cars;
	std::vector<element> people;

	/*Instance of mosquitto that connect to MQTT broker*/
	mosquitto* mosq;

	/*2D matrix that contains all noises simulated*/
	double** noises;


	double* recvData;
	double* averageData;
	double* data;


	void initializeVariables();
	void initializePeopleCars();
	void mosquittoInit();
	void closeMosquitto();
	void sendDataMosquitto(double x, double y, double noise);

	inline double sumOfNoises(double n1, double n2);
	inline double calculateDistance(int x1, int x2, int y1, int y2);

	void averagingData();
	


public:

	Simulator(simulationParameters parameters);
	~Simulator();

	void calculateNoise();
	void gatherNoises();
	void recomputePosition();

	static void sumOfNoises(void* inputBuffer, void* outputBuffer, int* len, MPI_Datatype* datatype);

};


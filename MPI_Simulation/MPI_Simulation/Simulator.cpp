#include "Simulator.h"

//PRIVATE FUNCTIONS

/*
* Used for initialize all private class variables. 
* Randomize using the rank of each process.
* Dynamically allocate space for the matrix of noise (one allocation for each process once)
* Create the MPI_Op in order to sum all noises correctly as sum of decibels
*/
void Simulator::initializeVariables()
{

	srand(time(NULL) + this->parameters.rank);

	cars.resize(this->parameters.numberCars);
	people.resize(this->parameters.numberPeople);

	this->definedOperation = false;


	this->noises = new double* [this->parameters.width];
	for (int i = 0; i < this->parameters.width; i++)
		this->noises[i] = new double[this->parameters.length];

	if (MPI_Op_create(&sumOfNoises, 0, &SUM_DECIBEL) == MPI_SUCCESS) {
		this->definedOperation = true;
	}

	int dataCount = this->parameters.width * this->parameters.length;
	this->recvData = new double[dataCount];
}


/*
* Using the random function initialized with the rank as seed, set the position and the speed of each car and person
*/
void Simulator::initializePeopleCars()
{

	for (auto &elem : this->people) {
		elem.posX = rand() % this->parameters.width;
		elem.posY = rand() % this->parameters.length;
		elem.speed.first = static_cast<double>(rand() / static_cast<double>(RAND_MAX / (this->parameters.movingSpeedPeople * 2))) - this->parameters.movingSpeedPeople;
		elem.speed.second = static_cast<double>(rand() / static_cast<double>(RAND_MAX / (this->parameters.movingSpeedPeople * 2))) - this->parameters.movingSpeedPeople;
	}

	for (auto &elem : this->cars) {
		elem.posX = rand() % this->parameters.width;
		elem.posY = rand() % this->parameters.length;
		elem.speed.first = static_cast<double>(rand() / static_cast<double>(RAND_MAX / (this->parameters.movingSpeedCar * 2))) - this->parameters.movingSpeedCar;
		elem.speed.second = static_cast<double>(rand() / static_cast<double>(RAND_MAX / (this->parameters.movingSpeedCar * 2))) - this->parameters.movingSpeedCar;
	}

}

/*
* This function allow to create a new instance of mosquitto only by the process with rank 0 (the main one)
* After creating the instance will connect to mosquitto broker
*/
void Simulator::mosquittoInit()
{
	if (this->parameters.rank == 0) {

		mosquitto_lib_init();

		this->mosq = mosquitto_new("publisher-c++", true, nullptr);

		if (mosquitto_connect(this->mosq, "test.mosquitto.org", 1883, 60) != MOSQ_ERR_SUCCESS) {
			throw std::exception();
			mosquitto_destroy(this->mosq);
		}

	}

}

/*
* When the program will terminate the instance of mosquitto will be disconnected and destroyed
*/
void Simulator::closeMosquitto()
{

	mosquitto_disconnect(this->mosq);

	mosquitto_destroy(this->mosq);

	mosquitto_lib_cleanup();

}

/*
* This function allow to publish a message to the broker.
*	- x and y are the position of the data/noise in latitude and longitude coordinates
*	-noise is the value of noise in decibel of the simulated position
*/
void Simulator::sendDataMosquitto(double x, double y, double noise)
{

	std::time_t ms = std::time(nullptr);

	std::stringstream message;
	
	message <<std::setprecision(10) << "{\"x\" : " << x << ", \"y\" : " << y << ", \"noise\" : [" << noise << "], \"timestamp\" : " << ms << "}";

	std::string s = message.str();

	mosquitto_publish(this->mosq, nullptr, "Receiver1", s.size(), s.c_str(), 0, false);

}

/*
* This function, given two noises, return the sum of them (in log base)
*/
inline double Simulator::sumOfNoises(double n1, double n2)
{
	return 10.0 * std::log10(std::pow(10, n1 / 10.0) + std::pow(10, n2 / 10.0));
}

/*
* This function return the distance of two points in a plane
*/
inline double Simulator::calculateDistance(int x1, int x2, int y1, int y2)
{
	return std::sqrt(std::pow((x2 - x1), 2) + std::pow((y2 - y1), 2));
}

/*
* This function:
*	1- divide the matrix of noise based on the chosen granularity
*	2- for each small area (granular area) will average all noises in order to send the average data
*	3- for each small area will calculate the latitude and the longitude 
*	4- the averaged noise will be published to the mqtt broker with the coordinates
*/
void Simulator::averagingData()
{

	int d = this->parameters.width / this->parameters.granularity;
	int l = this->parameters.length / this->parameters.granularity;

	for (int i = 0; i < d; i++) {
		for (int j = 0; j < l; j++) {
			double sum = 0;
			for (int k = i * this->parameters.granularity; k < i * this->parameters.granularity + this->parameters.granularity; k++) {
				for (int s = j * this->parameters.granularity; s < j * this->parameters.granularity + this->parameters.granularity; s++) {
					sum += this->noises[k][s];
				}
			}

			sum = sum / (this->parameters.granularity * this->parameters.granularity);

			for (int k = i * this->parameters.granularity; k < i * this->parameters.granularity + this->parameters.granularity; k++) {
				for (int s = j * this->parameters.granularity; s < j * this->parameters.granularity + this->parameters.granularity; s++) {
					this->noises[k][s] = sum;
				}
			}

			double currentLatitude;
			double currentLongitude;

			currentLatitude = this->parameters.latitude + 
								((double)((double)j*this->parameters.granularity + (double)this->parameters.granularity/2) / (double)6371) * 
							((double)180 / M_PI);
			currentLongitude = this->parameters.longitude + 
								((double)(i*this->parameters.granularity + (double)this->parameters.granularity/2) / (double)6371) * 
							((double)180 / M_PI) / std::cos(this->parameters.latitude * M_PI / (double)180);


			sendDataMosquitto(currentLatitude, currentLongitude, sum);
		}
	}

	/*for (int i = 0; i < this->parameters.width; i++) {
		for (int j = 0; j < this->parameters.length; j++) {
			std::cout << (int)this->noises[i][j] << " ";
		}
		std::cout << "\n";
	}*/
}

//PUBLIC FUNCTIONS

/*
* This is the constructor of the simulation:
*	1- set the parameters of the simulation
*	2- initialize the variables needed and initialize the position and speed of people and cars
*	3- initialize the mosquitto instance and will catch any exception
*/
Simulator::Simulator(simulationParameters parameters) : 
	parameters(parameters)
{
	initializeVariables();
	initializePeopleCars();
	
	try {
		this->mosquittoInit();
	}
	catch (std::exception e) {
		std::cout << e.what() << "\n";
		throw std::runtime_error("Mosquitto has not been initialized");
	}
}

/*
* This is the destructor and will:
*	1- close the mosquitto instance
*	2- delete the dynamical memory associated with the matrix of noises
*/
Simulator::~Simulator()
{

	this->closeMosquitto();

	for (int i = 0; i < this->parameters.width; i++)
		delete this->noises[i];
	delete this->noises;
	delete this->recvData;
}

/*
* This is the function that will simulate the noise of each person and car:
*	1- add a base noise between 30 to 40 (noise when there is nobody / noise due to the nature)
*	2- for each person (and car) will calculate the position (floor of the current position) and calculate a small area
*		around that is influenced by the noise
*	3- from the base noise will add the noise of the car (person) using a decreasing function (the noise decrease while 
*		incrementing the distance)
*/
void Simulator::calculateNoise()
{

	for (int i = 0; i < this->parameters.width; i++) {
		for (int j = 0; j < this->parameters.length; j++) {
			this->noises[i][j] = rand() % 10 + 30;
		}
	}

	for (auto& p : this->people) {
		int normX = std::floor(p.posX);
		int normY = std::floor(p.posY);
		std::cout << "People x y:  " << normX << " " << normY<<"\n";

		int x1 = std::max(normX - this->parameters.distanceAffectPeople, 0);
		int x2 = std::min(normX + this->parameters.distanceAffectPeople, (this->parameters.width-1));
		int y1 = std::max(normY - this->parameters.distanceAffectPeople, 0);
		int y2 = std::min(normY + this->parameters.distanceAffectPeople, (this->parameters.length-1));

		for (int i = x1; i <= x2; i++) {
			for (int j = y1; j <= y2; j++) {
				double noiseToAdd = 0;
				double distance = calculateDistance(i, normX, j, normY);
				if (distance == 0) {
					noiseToAdd = this->parameters.noisePerPerson;
				}
				else {
					noiseToAdd = std::max(-8.6 * std::log(distance) + (double)this->parameters.noisePerPerson -6.0, 0.0);
				}
			
				this->noises[i][j] = sumOfNoises(this->noises[i][j], noiseToAdd);
			}
		}
	}

	for (auto& p : this->cars) {
		int normX = std::floor(p.posX);
		int normY = std::floor(p.posY);
		std::cout << "Car x y:  " << normX << " " << normY << "\n";
		std::cout << "i am process" << this->parameters.rank << "\n";

		int x1 = std::max(normX - this->parameters.distanceAffectCar, 0);
		int x2 = std::min(normX + this->parameters.distanceAffectCar, (this->parameters.width - 1));
		int y1 = std::max(normY - this->parameters.distanceAffectCar, 0);
		int y2 = std::min(normY + this->parameters.distanceAffectCar, (this->parameters.length - 1));

		for (int i = x1; i <= x2; i++) {
			for (int j = y1; j <= y2; j++) {
				double noiseToAdd;
				double distance = calculateDistance(i, normX, j, normY);
				if (distance == 0) {
					noiseToAdd = this->parameters.noisePerCar;
				}
				else {
					noiseToAdd = std::max(-8.6 * std::log(distance) + (double)this->parameters.noisePerCar - 6.0, 0.0);
				}

				this->noises[i][j] = sumOfNoises(this->noises[i][j], noiseToAdd);
			}
		}
	}
	/*if (this->parameters.rank == 0) {
		for (int i = 0; i < this->parameters.width; i++) {
			for (int j = 0; j < this->parameters.length; j++) {
				std::cout <<this->noises[i][j] << " ";
			}
			std::cout << "\n";
		}
	}*/

}

/*
* This funciton allow to gather all matrix from all process to the main one.
*	1- Transform a 2d matrix in a single long array of data t
*	2- Collect all noises from all processes to the main one (rank=0) and sum them together using a SUM_DECIBEL function
*	3- The main process will reconstruct the 2d matrix in order to average and to publish all data
*/
void Simulator::gatherNoises()
{
	double* data = new double[this->parameters.width * this->parameters.length];

	int count = 0;
	for (int i = 0; i < this->parameters.width; i++) {
		for (int j = 0; j < this->parameters.length; j++) {
			data[count] = this->noises[i][j];
			count++;
		}
	}

	double* sendData = data;
	int dataCount = this->parameters.width * this->parameters.length;
	//MPI receive dataType = MPI_double
	int root = 0;
	//MPI Communicator = MPI_COMM_WORLD

	try {
		if(this->definedOperation)
			MPI_Reduce(sendData, this->recvData, dataCount, MPI::DOUBLE, SUM_DECIBEL, root, MPI_COMM_WORLD);
		else
			MPI_Reduce(sendData, this->recvData, dataCount, MPI::DOUBLE, MPI::SUM, root, MPI_COMM_WORLD);
	}
	catch (std::exception e) {
		std::cout << "RANK: " << this->parameters.rank << "\n";
		std::cout << e.what() << "\n";
	}


	if (this->parameters.rank == 0) {

		int c = 0;
		for (int i = 0; i < this->parameters.width; i++) {
			for (int j = 0; j < this->parameters.length; j++) {
				this->noises[i][j] = ((double*)recvData)[c];
				c++;
			}
		}
		this->averagingData();
	}

	delete data;
}


/*
* Each process will recompute the position of all people and cars inside the area.
* If a car of a person will go outside the simulation area it will be added again in a random position
*/
void Simulator::recomputePosition()
{

	

	for (auto& p : this->people) {
		
		p.posX = p.posX + p.speed.first * this->parameters.timeStep;
		p.posY = p.posY + p.speed.second * this->parameters.timeStep;
		
		if (p.posX > this->parameters.width || p.posX < 0 || p.posY > this->parameters.length || p.posY < 0) {
			p.posX = rand() % this->parameters.width;
			p.posY = rand() % this->parameters.length;
			p.speed.first = static_cast<double>(rand() / static_cast<double>(RAND_MAX / (this->parameters.movingSpeedPeople * 2))) - this->parameters.movingSpeedPeople;
			p.speed.second = static_cast<double>(rand() / static_cast<double>(RAND_MAX / (this->parameters.movingSpeedPeople * 2))) - this->parameters.movingSpeedPeople;
		}
	}

	for (auto& p : this->cars) {

		p.posX = p.posX + p.speed.first * this->parameters.timeStep;
		p.posY = p.posY + p.speed.second * this->parameters.timeStep;

		if (p.posX > this->parameters.width || p.posX < 0 || p.posY > this->parameters.length || p.posY < 0) {
			p.posX = rand() % this->parameters.width;
			p.posY = rand() % this->parameters.length;
			p.speed.first = static_cast<double>(rand() / static_cast<double>(RAND_MAX / (this->parameters.movingSpeedCar * 2))) - this->parameters.movingSpeedCar;
			p.speed.second = static_cast<double>(rand() / static_cast<double>(RAND_MAX / (this->parameters.movingSpeedCar * 2))) - this->parameters.movingSpeedCar;
		}
	}


}


/*
* New defined function that sum up two noises together using the log function
*/
void Simulator::sumOfNoises(void* inputBuffer, void* outputBuffer, int* len, MPI_Datatype* datatype)
{

	double* input = (double*)inputBuffer;
	double* output = (double*)outputBuffer;

	for (int i = 0; i < *len; i++)
	{
		output[i] = 10.0 * std::log10(std::pow(10, output[i] / 10) + std::pow(10, input[i] / 10));
	}

}

#include "Simulator.h"

void Simulator::initializeVariables()
{
	//Randomize using the rank
	srand(time(NULL) + this->parameters.rank);

	//Resize the vector
	cars.resize(this->parameters.numberCars);
	people.resize(this->parameters.numberPeople);

	this->definedOperation = false;


	//allocate space for the matrix of noises
	this->noises = new float* [this->parameters.width];
	for (int i = 0; i < this->parameters.width; i++)
		this->noises[i] = new float[this->parameters.length];

	if (MPI_Op_create(&sumOfNoises, 0, &SUM_DECIBEL) == MPI_SUCCESS) {
		this->definedOperation = true;
	}
}

void Simulator::initializePeopleCars()
{

	/*Random initialization of the position and the speed of the people and cars simulated*/

	for (auto &elem : this->people) {
		elem.posX = rand() % this->parameters.width;
		elem.posY = rand() % this->parameters.length;
		elem.speed.first = static_cast<float>(rand() / static_cast<float>(RAND_MAX / (this->parameters.movingSpeedPeople * 2))) - this->parameters.movingSpeedPeople;
		elem.speed.second = static_cast<float>(rand() / static_cast<float>(RAND_MAX / (this->parameters.movingSpeedPeople * 2))) - this->parameters.movingSpeedPeople;
	}

	for (auto &elem : this->cars) {
		elem.posX = rand() % this->parameters.width;
		elem.posY = rand() % this->parameters.length;
		elem.speed.first = static_cast<float>(rand() / static_cast<float>(RAND_MAX / (this->parameters.movingSpeedCar * 2))) - this->parameters.movingSpeedCar;
		elem.speed.second = static_cast<float>(rand() / static_cast<float>(RAND_MAX / (this->parameters.movingSpeedCar * 2))) - this->parameters.movingSpeedCar;
	}


}

Simulator::Simulator(simulationParameters parameters) : 
	parameters(parameters)
{
	initializeVariables();
	initializePeopleCars();
}

Simulator::~Simulator()
{
	for (int i = 0; i < this->parameters.length; i++)
		delete this->noises[i];
	delete this->noises;
}

void Simulator::calculateNoise()
{

	//std::cout << "CALCULATENOISE\n";
	for (int i = 0; i < this->parameters.width; i++) {
		for (int j = 0; j < this->parameters.length; j++) {
			this->noises[i][j] = rand()%100;
		}
	}
	//std::cout << "END OF CALCULATENOISE\n";

}

void Simulator::gatherNoises()
{

	//std::cout << "BEFORE GATHERNOISES\n";
	//Transform the matrix (2D array) into a single long array
	float* data = new float[this->parameters.width * this->parameters.length];

	int count = 0;
	for (int i = 0; i < this->parameters.width; i++) {
		for (int j = 0; j < this->parameters.length; j++) {
			data[count] = this->noises[i][j];
			count++;
		}
	}

	/*for (int i = 0; i < this->width * this->length; i++) {
		std::cout << data[i] << "	\n";
	}*/

	/*MPI_Barrier(MPI_COMM_WORLD);*/

	//std::cout << "CREATED LONG ARRAY\n";

	float* sendData = data;
	int dataCount = this->parameters.width * this->parameters.length;
	//MPI send dataType = MPI_FLOAT
	float* recvData = new float[dataCount];
	int recvCount = this->parameters.width * this->parameters.length;
	//MPI receive dataType = MPI_FLOAT
	int root = 0;
	//MPI Communicator = MPI_COMM_WORLD


	//std::cout << "INVOKING GATHER FUNCTION\n";

	//Gathering all matrices from all processes
	try {
		if(this->definedOperation)
			MPI_Reduce(sendData, recvData, dataCount, MPI::FLOAT, SUM_DECIBEL, root, MPI_COMM_WORLD);
		else
			MPI_Reduce(sendData, recvData, dataCount, MPI::FLOAT, MPI::SUM, root, MPI_COMM_WORLD);
	}
	catch (std::exception e) {
		std::cout << "RANK: " << this->parameters.rank << "\n";
		std::cout << e.what() << "\n";
	}

	//std::cout << "END GATHER FUNCTION + START PRINT\n";


	if (this->parameters.rank == 0) {
		//We are in the root node, we have to calculate the sum of all matrices

		//std::cout << "PRINT DATACOUNT " << dataCount << "\n";
		
		for (int i = 0; i < this->parameters.length; i++) {
			for (int j = 0; j < this->parameters.width; j++) {
				std::cout << (int)((float*)recvData)[i * this->parameters.width + j] << "\t";
			}
			std::cout << "\n";
			
		}
	}

}

void Simulator::recomputePosition()
{

	/*TODO: have to multiply the speed for the timestep!*/

	for (auto& p : this->people) {
		
		p.posX = p.posX + p.speed.first;
		p.posY = p.posY + p.speed.second;
		
		if (p.posX > this->parameters.width || p.posX < 0 || p.posY > this->parameters.length || p.posY < 0) {
			p.posX = rand() % this->parameters.width;
			p.posY = rand() % this->parameters.length;
			p.speed.first = static_cast<float>(rand() / static_cast<float>(RAND_MAX / (this->parameters.movingSpeedPeople * 2))) - this->parameters.movingSpeedPeople;
			p.speed.second = static_cast<float>(rand() / static_cast<float>(RAND_MAX / (this->parameters.movingSpeedPeople * 2))) - this->parameters.movingSpeedPeople;
		}
	}

	for (auto& p : this->cars) {

		p.posX = p.posX + p.speed.first;
		p.posY = p.posY + p.speed.second;

		if (p.posX > this->parameters.width || p.posX < 0 || p.posY > this->parameters.length || p.posY < 0) {
			p.posX = rand() % this->parameters.width;
			p.posY = rand() % this->parameters.length;
			p.speed.first = static_cast<float>(rand() / static_cast<float>(RAND_MAX / (this->parameters.movingSpeedCar * 2))) - this->parameters.movingSpeedCar;
			p.speed.second = static_cast<float>(rand() / static_cast<float>(RAND_MAX / (this->parameters.movingSpeedCar * 2))) - this->parameters.movingSpeedCar;
		}
	}


}

void Simulator::sumOfNoises(void* inputBuffer, void* outputBuffer, int* len, MPI_Datatype* datatype)
{

	float* input = (float*)inputBuffer;
	float* output = (float*)outputBuffer;

	for (int i = 0; i < *len; i++)
	{
		
		//output[i] += input[i];
		output[i] = 10 * std::log10(std::pow(10, output[i] / 10) + std::pow(10, input[i] / 10));
	}

}

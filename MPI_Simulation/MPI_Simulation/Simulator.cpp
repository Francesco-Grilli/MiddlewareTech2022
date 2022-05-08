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

	int dataCount = this->parameters.width * this->parameters.length;
	this->recvData = new float[dataCount];
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

void Simulator::mosquittoInit()
{
	if (this->parameters.rank == 0) {
		mosquitto* mosq;

		mosquitto_lib_init();

		mosq = mosquitto_new("publisher-c++", true, nullptr);

		if (mosquitto_connect(mosq, "test.mosquitto.org", 1883, 60) != MOSQ_ERR_SUCCESS) {
			throw std::runtime_error("Impossible to connect to the broker");
			mosquitto_destroy(mosq);
		}

		std::cout << "Sending a message\n";
		mosquitto_publish(mosq, nullptr, "MyTopic", 4, "Ciao", 1, true);

		mosquitto_disconnect(mosq);

		mosquitto_destroy(mosq);

		mosquitto_lib_cleanup();
	}

	


	

}

inline float Simulator::sumOfNoises(float n1, float n2)
{
	return 10 * std::log10(std::pow(10, n1 / 10) + std::pow(10, n2 / 10));
}

inline float Simulator::calculateDistance(int x1, int x2, int y1, int y2)
{
	return std::sqrt(std::pow((x2 - x1), 2) + std::pow((y2 - y1), 2));
}

void Simulator::averagingData()
{

	int d = this->parameters.width / this->parameters.granularity;
	int l = this->parameters.length / this->parameters.granularity;

	for (int i = 0; i < d; i++) {
		for (int j = 0; j < l; j++) {
			int sum = 0;
			for (int k = i * this->parameters.granularity; k < i * this->parameters.granularity + 4; k++) {
				for (int s = j * this->parameters.granularity; s < j * this->parameters.granularity + 4; s++) {
					sum += this->noises[k][s];
				}
			}

			sum = sum / (this->parameters.granularity * this->parameters.granularity);

			for (int k = i * this->parameters.granularity; k < i * this->parameters.granularity + 4; k++) {
				for (int s = j * this->parameters.granularity; s < j * this->parameters.granularity + 4; s++) {
					this->noises[k][s] = sum;
				}
			}
		}
	}

	for (int i = 0; i < this->parameters.length; i++) {
		for (int j = 0; j < this->parameters.width; j++) {
			std::cout << this->noises[j][i] << " ";
		}
		std::cout << "\n";
	}
	std::cout << "END OF COMPUTATION\n";
}

Simulator::Simulator(simulationParameters parameters) : 
	parameters(parameters)
{
	initializeVariables();
	initializePeopleCars();
	//mosquittoInit();
}

Simulator::~Simulator()
{
	for (int i = 0; i < this->parameters.width; i++)
		delete this->noises[i];
	delete this->noises;
	delete this->recvData;
}

void Simulator::calculateNoise()
{

	//std::cout << "CALCULATENOISE\n";
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
				float noiseToAdd;
				float distance = calculateDistance(i, normX, j, normY);
				if (distance == 0) {
					noiseToAdd = this->parameters.noisePerPerson;
				}
				else {
					noiseToAdd = std::max(-8.6 * std::log(distance) + this->parameters.noisePerPerson -6, 0.0);
				}
			
				this->noises[i][j] = sumOfNoises(this->noises[i][j], this->parameters.noisePerPerson);
			}
		}
	}

	for (auto& p : this->cars) {
		int normX = std::floor(p.posX);
		int normY = std::floor(p.posY);
		std::cout << "Car x y:  " << normX << " " << normY << "\n";

		int x1 = std::max(normX - this->parameters.distanceAffectCar, 0);
		int x2 = std::min(normX + this->parameters.distanceAffectCar, (this->parameters.width - 1));
		int y1 = std::max(normY - this->parameters.distanceAffectCar, 0);
		int y2 = std::min(normY + this->parameters.distanceAffectCar, (this->parameters.length - 1));

		for (int i = x1; i <= x2; i++) {
			for (int j = y1; j <= y2; j++) {
				float noiseToAdd;
				float distance = calculateDistance(i, normX, j, normY);
				if (distance == 0) {
					noiseToAdd = this->parameters.noisePerCar;
				}
				else {
					noiseToAdd = std::max(-8.6 * std::log(distance) + this->parameters.noisePerCar - 6, 0.0);
				}

				this->noises[i][j] = sumOfNoises(this->noises[i][j], this->parameters.noisePerCar);
			}
		}
	}

	//std::cout << this->noises[0][0] << "\n";
	//std::cout << "END OF CALCULATENOISE\n";

}

void Simulator::gatherNoises()
{
	//Transform the matrix (2D array) into a single long array
	float* data = new float[this->parameters.width * this->parameters.length];

	int count = 0;
	for (int i = 0; i < this->parameters.width; i++) {
		for (int j = 0; j < this->parameters.length; j++) {
			data[count] = this->noises[i][j];
			count++;
		}
	}

	float* sendData = data;
	int dataCount = this->parameters.width * this->parameters.length;
	//MPI receive dataType = MPI_FLOAT
	int root = 0;
	//MPI Communicator = MPI_COMM_WORLD


	//Gathering all matrices from all processes
	try {
		if(this->definedOperation)
			MPI_Reduce(sendData, this->recvData, dataCount, MPI::FLOAT, SUM_DECIBEL, root, MPI_COMM_WORLD);
		else
			MPI_Reduce(sendData, this->recvData, dataCount, MPI::FLOAT, MPI::SUM, root, MPI_COMM_WORLD);
	}
	catch (std::exception e) {
		std::cout << "RANK: " << this->parameters.rank << "\n";
		std::cout << e.what() << "\n";
	}


	if (this->parameters.rank == 0) {
		//We are in the root node, we have to calculate the sum of all matrices
		
		int c = 0;
		for (int i = 0; i < this->parameters.width; i++) {
			for (int j = 0; j < this->parameters.length; j++) {
				this->noises[i][j] = (int)((float*)recvData)[c];
				c++;
			}
		}

		this->averagingData();

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

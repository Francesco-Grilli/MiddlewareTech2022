#include "Simulator.h"

void Simulator::initializeVariables()
{

	cars.resize(numberCars);
	people.resize(numberPeople);

	this->noises = new float* [this->width];
	for (int i = 0; i < this->width; i++)
		this->noises[i] = new float[this->length];

	if (MPI_Op_create(&sumOfNoises, 0, &SUM_DECIBEL) == MPI_SUCCESS) {
		/*std::cout << "OK|||\n";*/
	}
}

Simulator::Simulator(int rank, int numberPeople, int numberCars, const int width, const int length, int sizeProcess) :
	rank(rank), numberPeople(numberPeople), numberCars(numberCars), width(width), length(length), sizeProcess(sizeProcess)
{
	initializeVariables();
}

Simulator::~Simulator()
{
	for (int i = 0; i < length; i++)
		delete this->noises[i];
	delete this->noises;
}

void Simulator::calculateNoise()
{
	srand(time(NULL) + this->rank);

	//std::cout << "CALCULATENOISE\n";
	for (int i = 0; i < this->width; i++) {
		for (int j = 0; j < this->length; j++) {
			this->noises[i][j] = rand()%100;
		}
	}
	//std::cout << "END OF CALCULATENOISE\n";

}

void Simulator::gatherNoises()
{

	//std::cout << "BEFORE GATHERNOISES\n";
	//Transform the matrix (2D array) into a single long array
	float* data = new float[this->width * this->length];

	int count = 0;
	for (int i = 0; i < this->width; i++) {
		for (int j = 0; j < this->length; j++) {
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
	int dataCount = this->width * this->length;
	//MPI send dataType = MPI_FLOAT
	float* recvData = new float[dataCount];
	int recvCount = this->width * this->length;
	//MPI receive dataType = MPI_FLOAT
	int root = 0;
	//MPI Communicator = MPI_COMM_WORLD


	//std::cout << "INVOKING GATHER FUNCTION\n";

	//Gathering all matrices from all processes
	try {
		MPI_Reduce(sendData, recvData, dataCount, MPI::FLOAT, SUM_DECIBEL, root, MPI_COMM_WORLD);
	}
	catch (std::exception e) {
		std::cout << "RANK: " << this->rank << "\n";
		std::cout << e.what() << "\n";
	}

	//std::cout << "END GATHER FUNCTION + START PRINT\n";


	if (this->rank == 0) {
		//We are in the root node, we have to calculate the sum of all matrices

		//std::cout << "PRINT DATACOUNT " << dataCount << "\n";
		
		for (int i = 0; i < this->length; i++) {
			for (int j = 0; j < this->width; j++) {
				std::cout << (int)((float*)recvData)[i * this->width + j] << "\t";
				
			}
			std::cout << "\n";
			
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

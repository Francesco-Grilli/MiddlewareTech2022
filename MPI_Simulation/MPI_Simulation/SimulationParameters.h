#pragma once

struct simulationParameters {
	int width;
	int length;
	int numberPeople;
	int numberCars;
	int noisePerPerson;
	int noisePerCar;
	int distanceAffectPeople;
	int distanceAffectCar;
	float movingSpeedPeople;
	float movingSpeedCar;
	int timeStep;
	int rank;
	int size;
};

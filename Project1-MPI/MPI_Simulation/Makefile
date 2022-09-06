OBJECT = main
MPIRUNFLAGS = -np 4
MPIDISTRIBUTE = --hostfile hostfile

all: $(wildcard *.cpp)
	rm main
	mpic++ -o ${OBJECT} Main.cpp Simulator.cpp Simulator.h SimulationParameters.h -lmosquitto
	mpirun ${MPIDISTRIBUTE} ${MPIRUNFLAGS} ./${OBJECT} 200 400 10 10 60 80 3 5 5.0 14.0 10 4 45.454203 9.189982
run:
	mpirun ${MPIRUNFLAGS} --oversubscribe ./${OBJECT} 1000 1000 16 16 60 80 2 4 5.0 14.0 15 100 41.903641 12.466195
debug:
	mpirun ${MPIDISTRIBUTE} ${MPIRUNFLAGS} ./${OBJECT} 20 20 2 2 60 80 3 5 5.0 14.0 10 4 45.454203 9.189982
compile: $(wildcard *.cpp)
	mpic++ -o ${OBJECT} Main.cpp Simulator.cpp Simulator.h SimulationParameters.h -lmosquitto

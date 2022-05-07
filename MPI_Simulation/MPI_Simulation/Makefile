OBJECT = main
MPIRUNFLAGS = -np 2
MPIDISTRIBUTE = --hostfile hostfile

all: $(wildcard *.cpp)
	rm main
	mpic++ -o ${OBJECT} Main.cpp Simulator.cpp Simulator.h SimulationParameters.h -lmosquitto
	mpirun ${MPIDISTRIBUTE} ${MPIRUNFLAGS} ./${OBJECT} 20 40 2 2 60 80 3 5 5.0 14.0 10
run:
	mpirun ${MPIDISTRIBUTE} ${MPIRUNFLAGS} ./${OBJECT} 8 8 40 40 60 80 15 25 5.0 14.0 10
debug:
	mpirun ${MPIDISTRIBUTE} ${MPIRUNFLAGS} ./${OBJECT} 20 20 2 2 60 80 3 5 5.0 14.0 10
compile: $(wildcard *.cpp)
	mpic++ -o ${OBJECT} Main.cpp Simulator.cpp Simulator.h SimulationParameters.h -lmosquitto

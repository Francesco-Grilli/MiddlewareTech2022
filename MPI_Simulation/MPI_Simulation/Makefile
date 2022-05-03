OBJECT = main
MPIRUNFLAGS = -np 4
MPIDISTRIBUTE = --hostfile hostfile

all: $(wildcard *.cpp)
	rm main
	mpic++ -o ${OBJECT} Main.cpp Simulator.cpp Simulator.h SimulationParameters.h
	mpirun ${MPIDISTRIBUTE} ${MPIRUNFLAGS} ./${OBJECT} 8 8 40 40 60 80 15 25 5.0 14.0 10
run:
	mpirun ${MPIDISTRIBUTE} ${MPIRUNFLAGS} ./${OBJECT} 8 8 40 40 60 80 15 25 5.0 14.0 10
debug:
	mpirun ${MPIDISTRIBUTE} ${MPIRUNFLAGS} --debug 0 ./${OBJETC} 8 8 40 40
compile: $(wildcard *.cpp)
	mpic++ -o ${OBJECT} Main.cpp Simulator.cpp Simulator.h SimulationParameters.h

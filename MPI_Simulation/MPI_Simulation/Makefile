OBJECT = main
MPIRUNFLAGS = -np 4
MPIDISTRIBUTE = --hostfile hostfile

all: $(wildcard *.cpp)
	rm main
	mpic++ -o ${OBJECT} Main.cpp Simulator.cpp Simulator.h
	mpirun ${MPIDISTRIBUTE} ${MPIRUNFLAGS} ./${OBJECT} 8 8 40 40
run:
	mpirun ${MPIDISTRIBUTE} ${MPIRUNFLAGS} ./${OBJECT} 8 8 40 40
debug:
	mpirun ${MPIDISTRIBUTE} ${MPIRUNFLAGS} --debug 0 ./${OBJETC} 8 8 40 40
compile: $(wildcard *.cpp)
	mpic++ -o ${OBJECT} Main.cpp Simulator.cpp Simulator.h

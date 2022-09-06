
# Fault-Tolerant Stream Processing System

This demo is written in Akka, an actor-based toolkit for building highly concurrent and distributed applications for Java, and it implements a simple pipeline divided into 3 stages.

Each stage can process and aggregate data inside a sliding window.
To make the stream process fault-tolerant to connection partition and program failure, each stage is supervised by an actor and can:
- Recover the internal state of the window in case of failure
- Implement reliable message delivery as at-least-once, so that no data is lost and is processed at least once.

Each actor has some parameters:
- Stage number, which represents the operator to instantiate. There are 3 operators and 2 other actors: the submitter (stage number 4) that generates new data 
for each stage and the printer (stage number 5) that collects and prints all data coming from the pipeline.
- windowSize, which represents the number of elements that each process is aggregating 
- windowSlide, which represents how many elements of the window are discharged for every aggregation
- maxNumberReplica, which tells how many times the same operator is replicated by the same supervisor

Each supervisor is able to receive data coming from the previous stage, redirect and balance them to the child operators.
Each child will receive only a subset of all messages coming from the supervisor using a key on each message.

## Running Tests

To correctly launch the demo, just run the main class 5 times with different CLI parameters:

#### Example:

- `stage 1 windowSize 5 windowSlide 1 maxNumberReplica 2`
- `stage 2 windowSize 5 windowSlide 1 maxNumberReplica 2`
- `stage 3 windowSize 5 windowSlide 1 maxNumberReplica 2`
- `stage 4 windowSize 5 windowSlide 1 maxNumberReplica 2`
- `stage 5 windowSize 5 windowSlide 1 maxNumberReplica 2`

All processes can be run in any order and each supervisor will wait for the instantiation of the next stage.

The Submitter will simulate the generation of data messages to Supervisor 1 which will aggregate the data and redirect it to the next supervisor; it also simulates a fault behaviour to the second stage through an ErrorMessage that causes the throwing of an exception, the restart of the actor and the recovery of the internal state.

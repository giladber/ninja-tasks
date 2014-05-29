# Ninja-tasks

## Introduction

Ninja-tasks is a library written in the Scala language, based on the akka actor library, whose purpose is to give its users a java/scala API for distributed computing purposes, while hiding all details of the actual distribution and execution underlying this mechanism - that is, it allows its users to concentrate solely on the business logic, rather than on its method of execution.

Using the Scala language, along with the akka library which provides the actor abstraction, remoting and cluster support, allows for easy and seamless interoperability with both any akka-based system, and also any java or scala based application.

## Execution Architecture

Ninja-tasks is generally meant to be used across clusters of machines, but can also have all of its subsystems run on one machine alone, working in the same way. It contains two subsystems:
- The job management subsystem - this subsystem is responsible for the where and when parts of the execution process - it decides which tasks will be next to run (when), and which machines to submit them to (where). Once task results are returned to it, they are collected and reduced to a user-specified result form.
- The execution subsystem - this part of the library is responsible solely for the execution of the tasks which are submitted to it, reporting on their results if succeeded, or their reasons of failure. Its work is given by the job management subsystem mentioned above.

Each of these subsystems is bundled as a single jar.
It is recommended to not perform any communication with the execution subsystem, as it is designed to only talk with the job management subsystem. Thus, users should only be aware of the job management subsystem, and may use either its API or communicate with it using actors.

Since both subsystems can easily become bottlenecks to one another if executed from the same machine, it is the generally recommended topology to run each subsystem on a different machine. Adding more machines running the execution subsystem will increase your processing power, while adding machines running the job management subsystem will increase availability, durability and balance across job management systems where load will be high.

## Work Architecture

The job management (henceforth simply management) subsystem receives work items which have a simple hierarchy - they have a container, a 'work' object, which contains 'job' objects - which in turn are the single most basic execution unit. Work objects contain several important data items, such as the number of underlying job objects, result reduction function, user-supplied priority and more.
Job objects, on the other hand, are simply the smallest unit of execution, and basically their only purpose is to be given an input and give back an output.

## SPI

There are some basic interfaces which need to be implemented by the user in order to use the distributed execution functionality. All of these interfaces (traits) are in the org.ninjatasks.spi package.
1. ExecutableJob - this trait represents the job objects described above. Its implementations must specify execution logic along with required input data.
2. Work - This trait represents the work objects described above. Its implementations must specify the input given to job objects, along with a result reduction function, an initial result, and the number of underlying tasks to be processed.
3. JobCreator - this trait is required in order to implement a lazy job creation process, so that the management subsystem can be run in machines without requiring huge amounts of RAM to support big work requests. It is queried in a work object and is queried for jobs whenever there is enough space and time to process more.

## Functional operations

Work objects may be wrapped by the user in a RichWork interface, providing functional operations such as map, flatMap and filter to both the work's result and to its underlying job objects. This rich API allows for a very easy way to manipulate work objects and create multi-step computations without having to worry about the execution mechanism at all.

## Examples

To come :)

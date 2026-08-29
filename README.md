Redis Server in Java

A lightweight Redis-compatible server implemented from scratch in Java.

Features

TCP-based Redis-compatible server

RESP command parsing

In-memory key-value storage

PING, SET, GET, and INFO replication

REPLCONF and PSYNC

Master-Replica replication

Full synchronization and RDB payload handling

Docker support

Kubernetes, KIND, and Helm support

Technologies

Java

Maven

TCP/IP & Java Sockets

Redis RESP Protocol

Docker

Kubernetes

KIND

Helm

Git & GitHub

Architecture

                   ┌──────────────────────┐
                   │     Redis Master     │
                   │      Port: 6379      │
                   └──────────┬───────────┘
                              │
                     Replication Stream
                              │
                              ▼
                   ┌──────────────────────┐
                   │     Redis Replica    │
                   │      Port: 6382      │
                   └──────────────────────┘

Master-Replica Replication

The replica connects to the master using:

1. PING
2. REPLCONF listening-port
3. REPLCONF capa
4. PSYNC
5. FULLRESYNC
6. RDB payload
7. Replication command stream

Example:

Master PING response: +PONG
REPLCONF listening-port response: +OK
REPLCONF capa response: +OK
PSYNC response: +FULLRESYNC <replication-id> 0

Write commands are then streamed from the Master to the Replica.

SET city Mumbai
        │
        ▼
Master :6379
        │
        ▼
Replica :6382

Supported Commands

PING

PING

Response:

PONG

SET

SET city Mumbai

Response:

OK

GET

GET city

Example:

Mumbai

INFO replication

INFO replication

Master:

role:master
master_replid:<replication-id>
master_repl_offset:0

Replica:

role:slave
master_replid:<replication-id>
master_repl_offset:0

REPLCONF

REPLCONF listening-port 6382
REPLCONF capa psync2
REPLCONF ACK 0

PSYNC

PSYNC ? -1

Example response:

+FULLRESYNC <replication-id> 0

Running Locally

Prerequisites

Install Java, Maven, and Git.

java -version
mvn -version

Build

git clone https://github.com/Abhijit1617/codecrafters-redis-java.git
cd codecrafters-redis-java
mvn clean package

Start Redis Master

java -jar target/codecrafters-redis.jar --port 6379

Start Redis Replica

Open another terminal:

java -jar target/codecrafters-redis.jar --port 6382 --replicaof "localhost 6379"

Testing

Test the server with a Redis client or TCP client:

PING
SET city Mumbai
GET city
INFO replication

Expected:

PING
→ PONG

SET city Mumbai
→ OK

GET city
→ Mumbai

To verify replication:

Master :6379
SET city Mumbai
        │
        ▼
Replica :6382
GET city
→ Mumbai

Docker

Build:

docker build -t myredis .

Run Master:

docker run -p 6379:6379 myredis

Run Replica:

docker run -p 6382:6382 myredis --port 6382 --replicaof "<MASTER_IP> 6379"

Replace <MASTER_IP> with the address reachable by the container.

Kubernetes

Kubernetes configuration is available under:

kind/

Create a KIND cluster:

kind create cluster --config k.yml --name redis-cluster

Build and load the image:

docker build -t myredis:latest .
kind load docker-image myredis:latest --name redis-cluster

Deploy:

kubectl apply -f pod.yml
kubectl apply -f service.yml

Check:

kubectl get pods
kubectl get services

Helm

The Helm chart is available under:

redis-chart/

Render:

helm template redis-chart

Lint:

helm lint redis-chart

Install:

helm install redis-app redis-chart

Check:

kubectl get pods
kubectl get services

Uninstall:

helm uninstall redis-app

Project Structure

codecrafters-redis-java/
│
├── src/
│   └── main/
│       └── java/
│           └── Main.java
│
├── dind/
├── kind/
├── redis-chart/
├── Dockerfile
├── pom.xml
├── README.md
├── .gitignore
└── azure-pipelines.yml

RESP Protocol

The server communicates using the Redis Serialization Protocol (RESP).

Example:

SET city Mumbai

RESP representation:

*3\r\n
$3\r\n
SET\r\n
$4\r\n
city\r\n
$6\r\n
Mumbai\r\n

Learning Objectives

Java networking

TCP sockets

Client-server architecture

Redis protocol

RESP parsing

In-memory data structures

Master-Replica replication

RDB synchronization concepts

Replication offsets

Docker

Kubernetes

Helm

Git and GitHub

Future Improvements

DEL

EXISTS

INCR

EXPIRE and TTL

Concurrent request processing

Persistent storage

Partial resynchronization

Additional Redis data types

Automated integration testing

License

This project is intended for educational and portfolio purposes.

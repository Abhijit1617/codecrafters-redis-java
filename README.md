<div align="center">

⚡ Redis Server in Java

A Redis-compatible in-memory server built from scratch with Java

<p>
  <img src="https://img.shields.io/badge/Java-23-orange?style=for-the-badge&logo=openjdk" alt="Java 23"/>
  <img src="https://img.shields.io/badge/Maven-Build-C71A36?style=for-the-badge&logo=apachemaven" alt="Maven"/>
  <img src="https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker" alt="Docker"/>
  <img src="https://img.shields.io/badge/Kubernetes-Ready-326CE5?style=for-the-badge&logo=kubernetes" alt="Kubernetes"/>
  <img src="https://img.shields.io/badge/Helm-Chart-0F1689?style=for-the-badge&logo=helm" alt="Helm"/>
</p>

<p>
  <b>TCP Sockets</b> • <b>RESP Protocol</b> • <b>Master-Replica Replication</b> • <b>Docker</b> • <b>Kubernetes</b>
</p>

<br/>

<img src="https://readme-typing-svg.demolab.com?font=Fira+Code&size=22&pause=1000&center=true&vCenter=true&width=650&lines=Building+Redis+from+Scratch+%F0%9F%9A%80;Java+%2B+TCP+Sockets+%2B+RESP;Master+%E2%86%92+Replica+Replication;Docker+%2B+Kubernetes+%2B+Helm" alt="Typing animation"/>

</div>

🚀 About the Project

This project is a lightweight Redis-compatible server implemented from scratch in Java.

It provides a practical implementation of Redis-style client-server communication, RESP parsing, in-memory key-value storage, and Master-Replica replication.

The project also includes containerization and deployment support using Docker, Kubernetes, KIND, and Helm.

✨ Features

Feature

Status

TCP client-server communication

✅

RESP protocol parsing

✅

In-memory key-value storage

✅

PING

✅

SET

✅

GET

✅

INFO replication

✅

REPLCONF

✅

PSYNC handshake

✅

Full synchronization

✅

RDB payload handling

✅

Master-Replica replication

✅

Replication command streaming

✅

Docker support

✅

Kubernetes support

✅

KIND support

✅

Helm support

✅

🧠 How It Works

                         ┌──────────────────────┐
                         │       CLIENT         │
                         │   Redis Commands     │
                         └──────────┬───────────┘
                                    │
                                    │ RESP / TCP
                                    ▼
                         ┌──────────────────────┐
                         │    REDIS MASTER      │
                         │      :6379           │
                         │                      │
                         │  In-Memory Storage   │
                         └──────────┬───────────┘
                                    │
                           Replication Stream
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │    REDIS REPLICA     │
                         │      :6382           │
                         │                      │
                         │  Synchronized Data  │
                         └──────────────────────┘

Example

SET city Mumbai
      │
      ▼
┌──────────────┐
│ Master :6379 │
└──────┬───────┘
       │
       │ Replication
       ▼
┌──────────────┐
│ Replica :6382│
└──────────────┘

🔄 Master-Replica Replication

The replica performs a synchronization handshake with the Master:

PING
  ↓
REPLCONF listening-port
  ↓
REPLCONF capa
  ↓
PSYNC
  ↓
FULLRESYNC
  ↓
RDB Payload
  ↓
Replication Command Stream

Example:

Master PING response:
+PONG

REPLCONF listening-port:
+OK

REPLCONF capa:
+OK

PSYNC response:
+FULLRESYNC <replication-id> 0

After synchronization, write commands are streamed to the replica.

Master
  │
  │ SET city Mumbai
  ▼
Replica
  │
  │ GET city
  ▼
Mumbai

📡 Replication Verification

Run:

INFO replication

Master

role:master
master_replid:<replication-id>
master_repl_offset:0

Replica

role:slave
master_replid:<replication-id>
master_repl_offset:0

The replica also acknowledges replication progress using:

REPLCONF ACK <offset>

🛠️ Tech Stack

┌─────────────────────────────────────────┐
│              TECHNOLOGIES               │
├─────────────────────────────────────────┤
│ ☕ Java                                  │
│ 📦 Maven                                 │
│ 🔌 TCP / Java Sockets                    │
│ 📡 Redis RESP Protocol                   │
│ 🐳 Docker                                │
│ ☸️ Kubernetes                            │
│ 🧩 KIND                                  │
│ ⎈ Helm                                   │
│ 🔀 Git & GitHub                          │
└─────────────────────────────────────────┘

💻 Supported Commands

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

Response:

Mumbai

INFO replication

INFO replication

Returns Master or Replica replication information.

REPLCONF

REPLCONF listening-port 6382
REPLCONF capa psync2
REPLCONF ACK 0

PSYNC

PSYNC ? -1

Used by the replica to request synchronization.

▶️ Run Locally

Prerequisites

Install:

Java

Maven

Git

Check Java:

java -version

Check Maven:

mvn -version

Build

From the project directory:

mvn clean package

Start Master

java -jar target/codecrafters-redis.jar --port 6379

Master:

localhost:6379

Start Replica

Open another terminal in the project directory:

java -jar target/codecrafters-redis.jar --port 6382 --replicaof "localhost 6379"

Replica:

localhost:6382

🧪 Quick Test

Run the Master and Replica first.

Then test:

PING

PONG

Set a value on the Master:

SET city Mumbai

OK

Read the value:

GET city

Mumbai

Then query the Replica:

GET city

Mumbai

This confirms that the write was replicated.

🐳 Docker

Build Image

docker build -t myredis .

Run Master

docker run -p 6379:6379 myredis

Run Replica

docker run -p 6382:6382 myredis --port 6382 --replicaof "<MASTER_IP> 6379"

Replace <MASTER_IP> with the address reachable from the container.

☸️ Kubernetes

The project includes Kubernetes configuration under:

kind/

Basic architecture:

             Kubernetes Cluster
                     │
                     ▼
              ┌─────────────┐
              │ Redis Pod   │
              └──────┬──────┘
                     │
                     ▼
              ┌─────────────┐
              │   Service   │
              └─────────────┘

Create KIND Cluster

Example configuration:

kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4

nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 30007
        hostPort: 30007

Create the cluster:

kind create cluster --config k.yml --name redis-cluster

Build and Load Docker Image

docker build -t myredis:latest .

kind load docker-image myredis:latest --name redis-cluster

Deploy

kubectl apply -f pod.yml
kubectl apply -f service.yml

Check Pods:

kubectl get pods

Check Services:

kubectl get services

⎈ Helm

The project contains a Helm chart:

redis-chart/

Render Templates

helm template redis-chart

Validate Chart

helm lint redis-chart

Install

helm install redis-app redis-chart

Check:

kubectl get pods
kubectl get services

Uninstall

helm uninstall redis-app

📁 Project Structure

codecrafters-redis-java/
│
├── 📂 src/
│   └── 📂 main/
│       └── 📂 java/
│           └── Main.java
│
├── 📂 dind/
│   └── Docker configuration
│
├── 📂 kind/
│   └── Kubernetes configuration
│
├── 📂 redis-chart/
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
│
├── 🐳 Dockerfile
├── 📦 pom.xml
├── 📖 README.md
├── .gitignore
└── azure-pipelines.yml

📡 RESP Protocol

The server communicates using the Redis Serialization Protocol (RESP).

For example:

SET city Mumbai

is represented as:

*3\r\n
$3\r\n
SET\r\n
$4\r\n
city\r\n
$6\r\n
Mumbai\r\n

The server reads the RESP request, parses the command and arguments, and executes the corresponding operation.

🔁 Complete Replication Flow

                   CLIENT
                      │
                      │ SET city Mumbai
                      ▼
              ┌───────────────┐
              │     MASTER    │
              │     :6379     │
              └───────┬───────┘
                      │
                      │ PING
                      │ REPLCONF
                      │ PSYNC
                      │ FULLRESYNC
                      │ RDB
                      │ COMMAND STREAM
                      ▼
              ┌───────────────┐
              │    REPLICA    │
              │     :6382     │
              └───────┬───────┘
                      │
                      ▼
                LOCAL STORAGE

🎯 Learning Objectives

This project provides practical experience with:

Java networking

TCP sockets

Client-server architecture

Redis protocol

RESP parsing

In-memory data structures

Master-Replica replication

RDB synchronization concepts

Replication offsets

Docker containerization

Kubernetes deployment

Helm

Git and GitHub

🚧 Future Improvements

DEL

EXISTS

INCR

EXPIRE and TTL

Concurrent client handling

Persistent storage

Partial resynchronization

Additional Redis data types

Automated integration testing

Improved command parsing

Better error handling

<div align="center">

⭐ If you find this project useful, consider giving it a star!

<br/>

<img src="https://capsule-render.vercel.app/api?type=waving&height=100&section=footer" alt="Footer animation"/>

</div>

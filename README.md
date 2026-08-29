<div align="center">

⚡ Redis Server in Java

A lightweight Redis-compatible in-memory server built from scratch with Java

<p>
  <img src="https://img.shields.io/badge/Java-23-orange?style=for-the-badge&logo=openjdk" alt="Java 23">
  <img src="https://img.shields.io/badge/Maven-Build-C71A36?style=for-the-badge&logo=apachemaven" alt="Maven">
  <img src="https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker" alt="Docker">
  <img src="https://img.shields.io/badge/Kubernetes-Ready-326CE5?style=for-the-badge&logo=kubernetes" alt="Kubernetes">
  <img src="https://img.shields.io/badge/Helm-Chart-0F1689?style=for-the-badge&logo=helm" alt="Helm">
</p>

<p>
  <b>TCP Sockets</b> • <b>RESP</b> • <b>Master-Replica Replication</b> • <b>Docker</b> • <b>Kubernetes</b> • <b>Helm</b>
</p>

<br>

<img src="https://readme-typing-svg.demolab.com?font=Fira+Code&size=21&pause=1200&center=true&vCenter=true&width=680&lines=Redis+Server+%E2%9A%A1+Built+with+Java;TCP+Sockets+%2B+RESP+Protocol;Master+%E2%86%92+Replica+Replication;Docker+%2B+Kubernetes+%2B+Helm" alt="Typing animation">

</div>

📌 About

This project is a Redis-compatible in-memory server implemented from scratch in Java.

It focuses on understanding the internal building blocks behind a Redis-style server: TCP communication, RESP request parsing, command execution, in-memory storage, and Master-Replica replication.

The project also includes Docker, Kubernetes, KIND, and Helm configuration for containerized and local cluster deployment.

✨ Highlights

🔌 TCP-based client-server communication

📡 Redis Serialization Protocol (RESP)

🗄️ In-memory key-value storage

🔄 Master-Replica replication

🤝 PING, REPLCONF, and PSYNC handshake flow

📦 Full synchronization with RDB payload handling

📤 Replication command streaming

📊 Replication information and offsets

🐳 Dockerized application

☸️ Kubernetes deployment

🧩 KIND local cluster support

⎈ Helm chart support

🧰 Tech Stack

Technology

Purpose

☕ Java

Core server implementation

📦 Maven

Build and dependency management

🔌 TCP / Java Sockets

Client-server communication

📡 RESP

Redis-compatible protocol

🐳 Docker

Containerization

☸️ Kubernetes

Container orchestration

🧩 KIND

Local Kubernetes cluster

⎈ Helm

Kubernetes package management

🔀 Git & GitHub

Version control

🧠 Architecture

                         ┌──────────────────────┐
                         │       CLIENT         │
                         │    Redis Commands    │
                         └──────────┬───────────┘
                                    │
                              RESP over TCP
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │     REDIS MASTER     │
                         │       :6379          │
                         │                      │
                         │  In-Memory Storage   │
                         └──────────┬───────────┘
                                    │
                            Replication Stream
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │     REDIS REPLICA    │
                         │       :6382          │
                         │                      │
                         │  Synchronized Data  │
                         └──────────────────────┘

Example

Client
  │
  │ SET city Mumbai
  ▼
Master :6379
  │
  │ Replication
  ▼
Replica :6382
  │
  │ GET city
  ▼
Mumbai

🔄 Master-Replica Replication

The replica synchronizes with the Master through a Redis-style handshake:

┌──────────────┐
│    Replica   │
└──────┬───────┘
       │
       │ 1. PING
       ▼
       │
       │ 2. REPLCONF listening-port
       ▼
       │
       │ 3. REPLCONF capa
       ▼
       │
       │ 4. PSYNC
       ▼
       │
       │ 5. FULLRESYNC
       ▼
       │
       │ 6. RDB Payload
       ▼
       │
       │ 7. Replication Commands
       ▼
┌──────────────┐
│    Master    │
└──────────────┘

Example synchronization responses:

PING
→ +PONG

REPLCONF listening-port
→ +OK

REPLCONF capa
→ +OK

PSYNC
→ +FULLRESYNC <replication-id> 0

After synchronization, write commands are streamed to the replica:

SET city Mumbai
        │
        ▼
   Master :6379
        │
        │ replication
        ▼
   Replica :6382

📊 Verify Replication

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

The replica can acknowledge replication progress with:

REPLCONF ACK <offset>

💻 Supported Commands

Command

Description

PING

Checks server availability

SET key value

Stores a key-value pair

GET key

Retrieves a stored value

INFO replication

Shows replication information

REPLCONF

Replication configuration and acknowledgement

PSYNC

Requests replica synchronization

PING

PING

PONG

SET

SET city Mumbai

OK

GET

GET city

Mumbai

INFO replication

INFO replication

Returns the current Master or Replica replication state.

▶️ Run Locally

Prerequisites

Make sure these are installed:

Java

Maven

Git

Verify:

java -version
mvn -version
git --version

1. Build

From the project directory:

mvn clean package

2. Start the Master

java -jar target/codecrafters-redis.jar --port 6379

Master:

localhost:6379

3. Start the Replica

Open a second terminal in the project directory:

java -jar target/codecrafters-redis.jar --port 6382 --replicaof "localhost 6379"

Replica:

localhost:6382

Running Architecture

Terminal 1                    Terminal 2

Master                        Replica
:6379                         :6382
  │                              │
  └──────── Replication ─────────┘

🧪 Quick Test

Start both the Master and Replica.

Test the Master

PING

Expected:

PONG

Write data

SET city Mumbai

Expected:

OK

Read data

GET city

Expected:

Mumbai

Verify on Replica

GET city

Expected:

Mumbai

This verifies that the value was replicated from the Master to the Replica.

🐳 Docker

Build the Image

docker build -t myredis .

Run Master

docker run -p 6379:6379 myredis

Run Replica

docker run -p 6382:6382 myredis --port 6382 --replicaof "<MASTER_IP> 6379"

Replace <MASTER_IP> with the Master address reachable from the container.

☸️ Kubernetes

Kubernetes configuration is available under:

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

Create a KIND Cluster

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

Build and Load the Image

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

The Helm chart is located at:

redis-chart/

Render Templates

helm template redis-chart

Validate the Chart

helm lint redis-chart

Install

helm install redis-app redis-chart

Check the deployment:

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

is encoded as:

*3\r\n
$3\r\n
SET\r\n
$4\r\n
city\r\n
$6\r\n
Mumbai\r\n

The server:

Client Request
      │
      ▼
RESP Parser
      │
      ▼
Command + Arguments
      │
      ▼
Command Handler
      │
      ▼
In-Memory Storage
      │
      ▼
RESP Response

🔁 End-to-End Flow

                         ┌──────────────┐
                         │    CLIENT    │
                         └──────┬───────┘
                                │
                         RESP / TCP
                                │
                                ▼
                    ┌─────────────────────┐
                    │   REDIS MASTER      │
                    │       :6379         │
                    └──────────┬──────────┘
                               │
                        Replication
                               │
                               ▼
                    ┌─────────────────────┐
                    │   REDIS REPLICA     │
                    │       :6382         │
                    └──────────┬──────────┘
                               │
                               ▼
                         Local Storage

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

KIND

Helm

Git and GitHub

🚧 Future Improvements

Planned improvements may include:

DEL

EXISTS

INCR

EXPIRE and TTL

Improved concurrent client handling

Persistent storage

Partial resynchronization

Additional Redis data types

Automated integration tests

Improved error handling

<div align="center">

⭐ If you like this project, consider giving it a star!

<br>

<img src="https://capsule-render.vercel.app/api?type=waving&height=100&section=footer" alt="Animated footer">

</div>

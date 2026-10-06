# TFTP Client-Server Application

A Java implementation of the Trivial File Transfer Protocol (TFTP) over UDP, developed as part of my university Computer Networks coursework.

This repository preserves the UDP implementation in its submitted state. Generated build files and IDE-specific metadata have been removed, but the source code itself has not been subsequently refactored or rewritten.

## Overview

The project implements a client-server file transfer application based on the TFTP protocol described in RFC 1350.

The client and server communicate using Java UDP sockets and support transferring files in both directions.

The coursework implementation focuses on:

- Read Requests (RRQ)
- Write Requests (WRQ)
- DATA and ACK packet handling
- Octet-mode file transfers
- File-not-found error handling
- Socket timeouts and packet retransmission attempts
- Binary packet construction and parsing using `ByteBuffer`

The server uses UDP port `9000` rather than the standard TFTP port `69`, avoiding the need to bind to a privileged system port.

## Technologies

- Java
- UDP
- `DatagramSocket`
- `DatagramPacket`
- `ByteBuffer`
- Maven
- RFC 1350

## Project Structure

```text
TFTP-UDP-Client/
├── TimeClient/
│   ├── pom.xml
│   └── src/
│       └── main/
│           └── java/
│               └── client/
│                   └── UDPSocketClient.java
├── write_large.txt
└── write_small.txt

TFTP-UDP-Server/
├── TimeServer/
│   ├── pom.xml
│   └── src/
│       └── main/
│           └── java/
│               └── server/
│                   └── UDPSocketServer.java
├── read_large.txt
└── read_small.txt
```

The included text files were used to test file transfers of different sizes during development.

## Running the Project

The server should be started before the client.

### 1. Compile the server

From the `TFTP-UDP-Server` directory:

```bash
mvn -f TimeServer/pom.xml compile
```

Start the server:

```bash
java -cp TimeServer/target/classes server.UDPSocketServer
```

The server listens for UDP requests on port `9000`.

### 2. Compile the client

From the `TFTP-UDP-Client` directory:

```bash
mvn -f TimeClient/pom.xml compile
```

Start the client and provide the server hostname or IP address:

```bash
java -cp TimeClient/target/classes client.UDPSocketClient 127.0.0.1
```

When prompted, enter:

- `1` to request a file from the server
- `2` to send a file to the server

You will then be prompted for the filename.

For local testing, both applications can be run on the same machine using the loopback address:

```text
127.0.0.1
```

## Example Files

For a read request, files such as:

```text
read_small.txt
read_large.txt
```

are included in the server directory.

For a write request, files such as:

```text
write_small.txt
write_large.txt
```

are included in the client directory.

These files were used during the original coursework testing.

## Protocol

TFTP uses a small set of packet types:

| Opcode | Packet |
|-------:|--------|
| 1 | Read Request (RRQ) |
| 2 | Write Request (WRQ) |
| 3 | DATA |
| 4 | ACK |
| 5 | ERROR |

File data is transferred in blocks, with DATA packets containing a block number and file data. ACK packets are used to acknowledge receipt of each block.

The coursework implementation supports **octet mode only**, meaning files are transferred as raw bytes.

## Coursework Scope

This project was produced for a university Computer Networks assignment centred on implementing TFTP using Java socket programming.

The submitted implementation was designed to demonstrate practical understanding of:

- UDP communication
- Client-server architecture
- Application-layer protocols
- Binary network packet formats
- File I/O
- Timeouts and retransmission
- Protocol error handling

The original coursework also explored a TCP-based version of the file-transfer protocol. This repository focuses on the UDP TFTP client-server implementation.

## Limitations

This repository represents the project **as it existed at the time of submission**, rather than a later production-ready rewrite.

Known limitations of the submitted implementation include:

- Only octet transfer mode is supported.
- Error handling is limited primarily to file-not-found errors.
- Simultaneous file transfers were not fully implemented.
- The implementation was developed for coursework and has not subsequently been refactored or hardened for production use.

## Background

This project was completed as part of my university Computer Networks coursework.

The repository has been cleaned for presentation by removing generated Maven build output and IDE-specific files, while retaining the original submitted source code.
package server;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Date;

public class UDPSocketServer extends Thread {

    protected DatagramSocket socket = null;

    public UDPSocketServer() throws SocketException {
        this("UDPSocketServer");
    }

    public UDPSocketServer(String name) throws SocketException {
        super(name);
        socket = new DatagramSocket(9000);
    }

    @Override
    public void run() {

        byte[] recvBuf = new byte[512];     // a byte array that will store the data received by the client, cannot  be greater than 512 bytes

        try {
            // run forever
            while (true) {

                DatagramPacket packet = new DatagramPacket(recvBuf, 512);
                socket.receive(packet);

                // want to extract the opcode from the received packet, opcode operation is first two bytes of packet
                // add packet data into byte array, then wrap into a buffer (.wrap()), before calling .getShort()
                // which returns the first two bytes, which will be the opcode instruction
                byte[] packetData = packet.getData();
                int packetDataLength = packetData.length;
                ByteBuffer opcode = ByteBuffer.wrap(packetData);
                int opcodeOperation = opcode.getShort();

                // need to extract filename and the mode, they are after the opcode and are null-terminated
                // if mode does not translate to string 'octet', we dont use?

                // extract filename by using while loop to add each byte to an int array until 0 byte is reached
                // then convert each element of int array to a char type, before combining to get filename

                // start index at 2 as opcode finishes after second byte
                int packetIndex = 2;
                int[] packetFilenameInt = new int[packetDataLength];
                int count = 0;

                while (packetIndex < packetDataLength && packetData[packetIndex] != 0) {
                    packetFilenameInt[count] = packetData[packetIndex];
                    count++;
                    packetIndex++;
                }

                char[] packetFilenameChar = new char[count];
                for (int i = 0; i < count; i++) {
                    packetFilenameChar[i] = (char) packetFilenameInt[i];
                }

                String packetDataFilename = new String(packetFilenameChar);

                // do the same to get the mode, and then convert it to lowercase
                packetIndex++;
                int[] packetModeInt = new int[packetDataLength];
                count = 0;

                while (packetIndex < packetDataLength && packetData[packetIndex] != 0) {
                    packetModeInt[count] = packetData[packetIndex];
                    count++;
                    packetIndex++;
                }

                char[] packetModeChar = new char[count];
                for (int i = 0; i < count; i++) {
                    packetModeChar[i] = (char) packetModeInt[i];
                }

                String packetDataMode = new String(packetModeChar).toLowerCase();

                if (!packetDataMode.equals("octet")) {
                    System.out.println("Packet Data Mode is not octet: Mode = " + packetDataMode);
                    return;
                }

                System.out.println("Packet data mode " + packetDataMode + ", packet filename " + packetDataFilename + ", opcode " + opcodeOperation);

                InetAddress addr = packet.getAddress();
                int srcPort = packet.getPort();

                // prints what the opcode operation is. Moved below getAddress and getPort methods as need to pass these to handling methods
                if (opcodeOperation == 1) {
                    System.out.println("Received Read Request (RRQ)");
                    HandleReadRequests(packetDataFilename, addr, srcPort);
                } else if (opcodeOperation == 2) {
                    System.out.println("Received Write Request (WRQ)");
                    HandleWriteRequests(packetDataFilename, addr, srcPort);
                } else if (opcodeOperation == 5) {
                    System.out.println("Received Error Opcode (ERROR)");
                    HandleErrorPackets(packetDataFilename, addr, srcPort);
                } else {
                    System.out.println("Received Unknown Opcode (" + opcodeOperation + ")");
                }
            }
        } catch (IOException e) {
            System.err.println(e);
        }
        socket.close();
    }

    public static void main(String[] args) throws IOException {
        new UDPSocketServer().start();
        System.out.println("Time Server Started");
    }

    // method to handle opcode 1, passing filename, client address and port
    // wouldnt work unless I included exception thrown

    public void HandleReadRequests(String filename, InetAddress clientAddress, int clientPort) throws IOException {
        File packetFile = new File(filename);

        if (!packetFile.exists()) {
            System.out.println("File does not exist: " + filename);
            HandleErrorPackets(filename, clientAddress, clientPort);
            return;
        }

        FileInputStream packetFileIn = new FileInputStream(packetFile);
        short dataPacketBlockNumber = 1;
        boolean lastPacketSent = false;

        while (lastPacketSent == false) {
            // need to read the file in 512 byte chunks
            byte[] fileData = new byte[512];
            int packetFileLength = packetFileIn.read(fileData);

            if (packetFileLength == -1) {
                System.out.println("Packet file length is null");
                break;
            }

            // prepare data packet
            ByteBuffer dataPacketPrep = ByteBuffer.allocate(4 + packetFileLength);
            dataPacketPrep.putShort((short) 3);
            dataPacketPrep.putShort(dataPacketBlockNumber);
            dataPacketPrep.put(fileData, 0, packetFileLength);

            // wouldnt work with dataPacketPrep, to fix intellij recommended .array(), will look into
            DatagramPacket dataPacketSend = new DatagramPacket(dataPacketPrep.array(), dataPacketPrep.position(), clientAddress, clientPort);

            // use try catch for timeout exception in while loop so runs until correct acknowledgement received
            // updated to include an extra condiiton so it doesnt run infinitely
            // send data in here as can resend if timeout happens, handles ACKs as well
            boolean correctACKReceived = false;
            int numOfRetriedSends = 0;

            while (correctACKReceived == false && numOfRetriedSends < 10) {
                try {
                    socket.send(dataPacketSend);
                    System.out.println("Sent data packet with block number: " + dataPacketBlockNumber);

                    // creating datagram packet for the ACK, is 4 bytes, 2 for opcode and 2 for block number
                    byte[] ackData = new byte[4];
                    DatagramPacket ackDataPacket = new DatagramPacket(ackData, ackData.length);

                    socket.setSoTimeout(5000);
                    socket.receive(ackDataPacket);

                    // extracting opcode and block from ACK packet
                    byte[] receivedACKData = ackDataPacket.getData();
                    ByteBuffer receivedACKBuffer = ByteBuffer.wrap(receivedACKData);
                    int opcodeOperation = receivedACKBuffer.getShort();
                    short ackBlockNumber = receivedACKBuffer.getShort();
                    System.out.println("Received ACK block number " + ackBlockNumber);

                    // check to see if true, then continue operation by sending data packet
                    if (opcodeOperation == 4) {
                        if (ackBlockNumber == dataPacketBlockNumber) {
                            correctACKReceived = true;
                        }
                    }

                } catch (SocketTimeoutException e) {
                    System.out.println("Socket timed out for block number " + dataPacketBlockNumber);
                    numOfRetriedSends++;
                }

            } if (numOfRetriedSends == 10) {
                System.out.println("Reach max amount of retried sends for block number " + dataPacketBlockNumber);
                return;

            } if (packetFileLength < 512) {
                System.out.println("Last data packet sent.");
                lastPacketSent = true;
            }

            dataPacketBlockNumber++;
        }

        packetFileIn.close();
    }

    public void HandleWriteRequests(String filename, InetAddress clientAddress, int clientPort) throws IOException {
        File writeToFile = new File(filename);
        FileOutputStream filePacketOut = new FileOutputStream(writeToFile);

        // need to create ACK packet to send, again intellij recommends using .array()
        // blockNumber changed to short so can compare later
        short dataPacketBlockNumber = 0;
        ByteBuffer ackPacketPrep = ByteBuffer.allocate(4);
        ackPacketPrep.putShort((short) 4); // opcode 4 for ack
        ackPacketPrep.putShort(dataPacketBlockNumber);
        DatagramPacket ackDataPacket = new DatagramPacket(ackPacketPrep.array(), ackPacketPrep.position(), clientAddress, clientPort);
        socket.send(ackDataPacket);

        boolean lastPacketReceived = false;
        int numOfRetriedSends = 0;

        // repeatedly send ACKS and receive data packets
        while (lastPacketReceived == false && numOfRetriedSends < 10) {

            // created to allow re-receive later on
            DatagramPacket writePacketReceived = new DatagramPacket(new byte[512], 512);

            try {

                socket.setSoTimeout(5000);
                socket.receive(writePacketReceived);

                // packet received, now extract data
                byte[] writePacketData = writePacketReceived.getData();
                int packetDataLength = writePacketReceived.getLength();
                ByteBuffer OpAndBlockNumber = ByteBuffer.wrap(writePacketData);
                int opcodeOperation = OpAndBlockNumber.getShort();
                short blockNumberReceived = OpAndBlockNumber.getShort();
                System.out.println("Packet with opcode " + opcodeOperation + " and block number " + blockNumberReceived);

                // condition to write to file. Want block number + 1 as will be 0 as haev sent an ACK already, but needs to be 1
                if (opcodeOperation == 3) {
                    if (blockNumberReceived == dataPacketBlockNumber) {
                        System.out.println("same packet received, not meant to provide error handling but helps debug.");
                        socket.send(ackDataPacket);
                        continue;
                } if (blockNumberReceived == dataPacketBlockNumber + 1) {

                        // create new byte array to add data part of packet
                        byte[] dataToWrite = new byte[packetDataLength - 4];
                        // copy array from after opcode and block num into dataToWrite
                        System.arraycopy(writePacketData, 4, dataToWrite, 0, dataToWrite.length);
                        filePacketOut.write(dataToWrite, 0, packetDataLength - 4);

                        dataPacketBlockNumber = blockNumberReceived;

                        // prepare next ACK packet to send
                        ackPacketPrep.clear();
                        ackPacketPrep.putShort((short) 4);
                        ackPacketPrep.putShort(dataPacketBlockNumber);
                        ackDataPacket = new DatagramPacket(ackPacketPrep.array(), ackPacketPrep.position(), clientAddress, clientPort);
                        socket.send(ackDataPacket);
                    }

                    if (packetDataLength < 512) {
                        lastPacketReceived = true;
                        System.out.println("Last packet received.");
                        break;
                    }
                }
            } catch (SocketTimeoutException e) {
                // if timeout, print error message then resend ACK incase it was lost
                System.out.println("Socket timed out for block number " + dataPacketBlockNumber);
                socket.send(ackDataPacket);
                numOfRetriedSends++;
            }
        }

        if (numOfRetriedSends == 10) {
            System.out.println("Reach max amount of retried sends for block number " + dataPacketBlockNumber);
        }

        filePacketOut.close();
    }

    public void HandleErrorPackets(String filename, InetAddress clientAddress, int clientPort) throws IOException {
        System.out.println("System unable to find file " + filename);
        // error code 1
        String errorMessage = "System unable to find file " + filename;
        byte[] errorMessageBytes = errorMessage.getBytes();
        // 4 for opcode + error message (2 bytes each), errorMessageBytes.length, 1 for null byte (0)
        ByteBuffer errorMessagePrep = ByteBuffer.allocate(4 + errorMessageBytes.length + 1);
        errorMessagePrep.putShort((short) 5);
        errorMessagePrep.putShort((short) 1);
        errorMessagePrep.put(errorMessageBytes);
        // use byte not short as short adds two bytes, so 00, when we wnat just 0
        errorMessagePrep.put((byte) 0);
        DatagramPacket ErrorPacket = new DatagramPacket(errorMessagePrep.array(), errorMessagePrep.position(), clientAddress, clientPort);
        socket.send(ErrorPacket);
    }

}

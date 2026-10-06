package client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Scanner;

public class UDPSocketClient {

    // the client will take the IP Address of the server (in dotted decimal format as an argument)
    // given that for this tutorial both the client and the server will run on the same machine, you can use the loopback address 127.0.0.1
    public static void main(String[] args) throws IOException {
        
        DatagramSocket socket;
//        DatagramPacket packet;
        
        if (args.length != 1) {
            System.out.println("the hostname of the server is required");
            return;
        }

        // change order of user input -- opcode first then filename
        Scanner clientUserInput = new Scanner(System.in);
        System.out.println("Press 1 to read to a file or 2 to write to a file: ");

        // digital ocean then trial and error. Needs to be a short, not int as int expects 4 bytes
        short opcodeOption = clientUserInput.nextShort();
        ByteBuffer opcode = ByteBuffer.allocate(2).putShort(opcodeOption);
        byte[] opcodeBytes = opcode.array();

        clientUserInput.nextLine();

        System.out.println("What is the filename you want? ");
        String clientfilename = clientUserInput.nextLine();
        byte[] filenameBytes = clientfilename.getBytes();

        // hardset to octet as only mode we want to use. Not sure if this would be done automatically
        String mode = "octet";
        byte[] modeBytes = mode.getBytes();

        // preparing initial request packet to send
        // 2 + ... + 1 + ... + 1 --> 2 is for opcode, 1 is for null byte (0) separation
        ByteBuffer clientSendPacketPrep = ByteBuffer.allocate(2 + filenameBytes.length + 1 + modeBytes.length + 1);
        clientSendPacketPrep.put(opcodeBytes);
        clientSendPacketPrep.put(filenameBytes);
        clientSendPacketPrep.put((byte) 0);
        clientSendPacketPrep.put(modeBytes);
        clientSendPacketPrep.put((byte) 0);

        System.out.println("Opcode instruction: " + opcodeOption);

        InetAddress address = InetAddress.getByName(args[0]);
        socket = new DatagramSocket(4000);
        DatagramPacket clientSendPacket = new DatagramPacket(clientSendPacketPrep.array(), clientSendPacketPrep.position(), address, 9000);

        socket.send(clientSendPacket);

        int retriesSent = 0;

        // while loop with try/catch for receiving appropiate response
        while (retriesSent < 10) {
            try {
                socket.setSoTimeout(5000);

                // create packet for server response, then extract relevant data
                byte[] responsePacketBytes = new byte[512];
                DatagramPacket responsePacket = new DatagramPacket(responsePacketBytes, responsePacketBytes.length);
                socket.receive(responsePacket);

                byte[] packetResponseData = responsePacket.getData();
                ByteBuffer responseOpcode = ByteBuffer.wrap(packetResponseData);
                int responseOpcodeOperation = responseOpcode.getShort();

                // handle accordingly depending on the packet received
                if (responseOpcodeOperation == 3) {
                    System.out.println("DATA packet received");
                    handleDataResponsePacket(responsePacket, socket, address, clientfilename);
                } else if (responseOpcodeOperation == 4) {
                    System.out.println("ACK packet received");
                    handleACKResponsePacket(responsePacket, socket, address, clientfilename);
                } else if (responseOpcodeOperation == 5) {
                    System.out.println("ERROR packet received");
                    // m,aybe add code to print what the error is, but only need fileNotFound
                    break;
                } else {
                    System.out.println("Incorrect opcode received: " + responseOpcodeOperation);
                    break;
                }

            } catch (SocketTimeoutException e) {
                System.out.println("Socket timed out");
                retriesSent++;
                socket.send(clientSendPacket);
            }
        }
        if (retriesSent == 10) {
            System.out.println("No response received after 10 resends of packet.");
        }
        socket.close();
    }

    public static void handleDataResponsePacket(DatagramPacket responsePacket, DatagramSocket socket, InetAddress address, String filename) throws IOException {
        FileOutputStream fileToWriteTo = new FileOutputStream(filename);
        short expectedBlockNumber = 0;
        int numOfRetries = 0;

        // repeatedly send ACKS and receive Data until last data packet sent, or timeout occurs 10 times
        while (numOfRetries < 10) {
            // extract opcode and block number, then the data bytes (4-512) --> opcode already extracted? unnecessary
            // once done, write data bytes to file, send ack and repeat until packet size is < 512 bytes
            try {

                socket.receive(responsePacket);

                // extract relevant information from received packet
                byte[] responsePacketBytes = responsePacket.getData();
                ByteBuffer responsePacketBuffer = ByteBuffer.wrap(responsePacketBytes);
                int responseOpcodeOperation = responsePacketBuffer.getShort();
                short responseBlockNumber = responsePacketBuffer.getShort();

                if (responseOpcodeOperation != 3) {
                    System.out.println("Incorrect opcode recieved, should be 3 but received: " + responseOpcodeOperation);
                    return;
                }

                if (responseBlockNumber != expectedBlockNumber) {
                    System.out.println("Incorrect block number recieved, should be " + expectedBlockNumber + " but received: " + responseBlockNumber);
                    return;
                }
                System.out.println("Data packet with opcode " + responseOpcodeOperation + " and block number " + responseBlockNumber);

                int responseDataLength = responsePacket.getLength();

                // use 4 in middle to avoid adding opcode and block number bytes, - 4 so fits
                fileToWriteTo.write(responsePacketBytes, 4, responseDataLength - 4);

                // send ACK so next packet can be received
                // forst get port number so can send to correct server port
                int serverPort = responsePacket.getPort();

                short ACKBlockNumber = responseBlockNumber;
                ByteBuffer ACKPacketPrep = ByteBuffer.allocate(4);
                ACKPacketPrep.putShort((short) 4);
                ACKPacketPrep.putShort(ACKBlockNumber);
                DatagramPacket ACKPacket = new DatagramPacket(ACKPacketPrep.array(), ACKPacketPrep.position(), address, serverPort);
                socket.send(ACKPacket);

                socket.setSoTimeout(5000);

                if (responseDataLength < 512) {
                    System.out.println("Last packet received.");
                    break;
                }

            } catch (SocketTimeoutException e) {
                System.out.println("Socket timed out for receiving next data packet");
                numOfRetries++;
            }

            expectedBlockNumber++;
        }

        fileToWriteTo.close();
    }

    public static void handleACKResponsePacket(DatagramPacket responsePacket, DatagramSocket socket, InetAddress address, String filename) throws IOException {

        // send dasta packets for server to write to
        // receive ack, send data, repeat
        File fileToRead = new File(filename);

        if (!fileToRead.exists()) {
            System.out.println("File does not exist");
            return;
        }

        // create fileInputStream that reads byte[] up to specified length (512)
        byte[] fileData = new byte[512];
        FileInputStream clientFileData = new FileInputStream(fileToRead);
        int packetFileLength = clientFileData.read(fileData);
        short dataPacketBlockNumber = 1;

        // receive ACK packet, extract relevant data (opcode, block and port)
        socket.receive(responsePacket);
        byte[] responseACKBytes = responsePacket.getData();
        ByteBuffer responseACKBuffer = ByteBuffer.wrap(responseACKBytes);
        int responseOpcodeOperation = responseACKBuffer.getShort();
        short responseBlockNumber = responseACKBuffer.getShort();
        int serverPort = responsePacket.getPort();

        if (responseOpcodeOperation != 4 || responseBlockNumber != 0) {
            System.out.println("incorrect opcode or block number received, should be 4 and 0, but received: " + responseOpcodeOperation + " and " + responseBlockNumber);
            return;
        }

        // whilst we havent reached end of file, we continue this loop
        while (packetFileLength != -1) {

            // creates data packet to send, adding in relevant data and then the corresponding fileData for this chunk
            ByteBuffer dataPacketPrep = ByteBuffer.allocate(4 + packetFileLength);
            dataPacketPrep.putShort((short) 3);
            dataPacketPrep.putShort(dataPacketBlockNumber);
            dataPacketPrep.put(fileData, 0, packetFileLength);

            DatagramPacket dataPacket = new DatagramPacket(dataPacketPrep.array(), dataPacketPrep.position(), address, serverPort);

            boolean correctACKReceived = false;
            int numberOfRetries = 0;

            // sends dataPacket, and if it receives correct ACK, exits and moves onto next data packet. Otherwise, resend up to ten times
            while (correctACKReceived == false && numberOfRetries < 10) {
                try {
                    socket.send(dataPacket);
                    System.out.println("Packet sent with block number: " + dataPacketBlockNumber);

                    byte[] ackData = new byte[4];
                    DatagramPacket nextACKPacket = new DatagramPacket(ackData, ackData.length);

                    socket.setSoTimeout(5000);
                    socket.receive(nextACKPacket);

                    byte[] nextACKPacketBytes = nextACKPacket.getData();
                    ByteBuffer nextACKPacketBuffer = ByteBuffer.wrap(nextACKPacketBytes);
                    int nextACKPacketOpcode = nextACKPacketBuffer.getShort();
                    short nextACKBlockNumber = nextACKPacketBuffer.getShort();

                    if (nextACKPacketOpcode == 4) {
                        if (nextACKBlockNumber == dataPacketBlockNumber) {
                            correctACKReceived = true;
                        }
                    }

                } catch (SocketTimeoutException e) {
                    System.out.println("Socket timed out");
                    numberOfRetries++;
                }
            }

            if (numberOfRetries == 10) {
                System.out.println("Maximum number of retries reached");
                return;
            }

            if (packetFileLength < 512) {
                System.out.println("Last packet sent.");
                break;
            }

            // increases block number and reads next chunk of data
            dataPacketBlockNumber++;
            packetFileLength = clientFileData.read(fileData);
        }

        clientFileData.close();
    }
}

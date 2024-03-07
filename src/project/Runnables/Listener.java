package project.Runnables;

import project.CLI.CLI;
import project.Client;
import project.DataStructures.MissingPeerRecoveryData;
import project.Messages.Message;
import project.Messages.MessageBuilder;
import project.Messages.MessageParser;
import project.Messages.MessageType;
import project.Peer;
import project.Utils.SocketUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

public class Listener implements Runnable{

    private final Client client;
    private DatagramSocket socket;
    private final List<MissingPeerRecoveryData> missingPeers;
    private final Map<UUID, Integer> processMap;

    public Listener(Client client) {
        this.client = client;
        missingPeers = new ArrayList<>();
        processMap = new HashMap<>();
    }
    
    @Override
    public void run() {
        try {
            socket = new DatagramSocket(SocketUtils.PORT_NUMBER);
            byte[] receivedData = new byte[1024];
            DatagramPacket receivedPacket;

            while (true) {

                // receive packet and extract message type and data
                receivedPacket = new DatagramPacket(receivedData, receivedData.length);
                socket.receive(receivedPacket);

                String command = null;
                String processID = null;
                String data = null;
                int sequenceNumber = -1;
                try {
                    command = MessageParser.extractCommand(receivedPacket);
                    processID = MessageParser.extractProcessID(receivedPacket);
                    data = MessageParser.extractData(receivedPacket);
                    sequenceNumber = MessageParser.extractSequenceNumber(receivedPacket);
                } catch (ArrayIndexOutOfBoundsException ignored) {
                }
                UUID processUUID = processID != null ? UUID.fromString(processID) : null;

                // add process to processMap if it is not present
                if(processUUID != null && !processMap.containsKey(processUUID)){
                    processMap.put(processUUID, 0);
                }

                // if the received message has already been received, discard it
                if(processUUID != null && sequenceNumber != -1 &&
                        processMap.containsKey(processUUID) && processMap.get(processUUID) >= sequenceNumber){
                    command = null; // easy way to discard it
                }

                CLI.printDebug("RECEIVED: " + command + ", " + data);

                // if data is null, that means the packet was not formatted according to our rules
                if (command != null && data != null) {
                    // extract information about the sender
                    InetAddress senderAddress = receivedPacket.getAddress();
                    int senderPort = receivedPacket.getPort();

                    // execute action based on command
                    switch (command) {
                        case MessageType.ACK:
                            handleAck(sequenceNumber);
                            break;
                        case MessageType.PING:
                            handlePing(data, senderAddress, senderPort);
                            break;
                        case MessageType.PONG:
                            handlePong(data, senderAddress, senderPort);
                            break;
                        case MessageType.ROOM_MEMBER_START:
                            handleRoomMemberStart(data, sequenceNumber, senderAddress);
                            break;
                        case MessageType.ROOM_MEMBER:
                            handleRoomMember(data, sequenceNumber, senderAddress);
                            break;
                        case MessageType.MEMBER_INFO_REQUEST:
                            handleMemberInfoRequest(data, senderAddress);
                            break;
                        case MessageType.MEMBER_INFO_REPLY:
                            handleMemberInfoReply(data, senderAddress);
                            break;
                        default:
                            break;
                    }

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleAck(int sequenceNumber){
        client.acknowledge(sequenceNumber);
    }

    private void handlePing(String data, InetAddress senderAddress, int senderPort) throws Exception {
        String[] dataVector = data.split(",");
        String userID = dataVector[0];
        String username = dataVector[1];
        if(!userID.equals(client.getPeerData().getIdentifier().toString())) {
            client.addPeer(new Peer(userID, username, senderAddress, senderPort));
            Message response = MessageBuilder.pong(client.getPeerData().getIdentifier().toString(), client.getPeerData().getUsername(), senderAddress);
            SocketUtils.sendPacket(client.getSocket(), response);
        }
    }

    private void handlePong(String data, InetAddress senderAddress, int senderPort) throws Exception{
        String[] dataVector = data.split(",");
        String userID = dataVector[0];
        String username = dataVector[1];
        client.addPeer(new Peer(userID, username, senderAddress, senderPort));
    }

    private void handleRoomMemberStart(String data, int sequenceNumber, InetAddress senderAddress) throws IOException {
        String[] dataVector = data.split(",");
        String roomID = dataVector[0];
        String roomName = dataVector[1];
        String peerID = dataVector[2];
        String peerUsername = dataVector[3];
        int membersNumber = Integer.parseInt(dataVector[4]);
        Optional<Peer> peer = client.getPeers().stream().filter(x -> x.getIdentifier().toString().equals(peerID)).findFirst();
        if(peer.isPresent()) {
            client.createRoomMembership(peer.get(), roomID, roomName, membersNumber);
            sendAck(sequenceNumber, senderAddress);
        }
        else{
            missingPeers.add(new MissingPeerRecoveryData(peerID, roomID, sequenceNumber));
            findMissingPeer(senderAddress, roomID, peerID);
        }
    }

    private void handleRoomMember(String data, int sequenceNumber, InetAddress senderAddress) throws Exception {
        String[] dataVector = data.split(",");
        String roomID = dataVector[0];
        String peerID = dataVector[1];
        String peerUsername = dataVector[2];
        Optional<Peer> peer = client.getPeers().stream().filter(x -> x.getIdentifier().toString().equals(peerID)).findFirst();
        if(peer.isPresent()) {
            client.addRoomMember(roomID, peer.get());
            sendAck(sequenceNumber, senderAddress);
        }
        else{
            missingPeers.add(new MissingPeerRecoveryData(peerID, roomID, sequenceNumber));
            findMissingPeer(senderAddress, roomID, peerID);
        }
    }

    private void handleMemberInfoRequest(String data, InetAddress senderAddress) throws IOException {
        String[] dataVector = data.split(",");
        String peerID = dataVector[0];
        String roomID = dataVector[1];
        Optional<Peer> peer = client.getPeers().stream().filter(x -> x.getIdentifier().toString().equals(peerID)).findFirst();
        if (peer.isPresent()){
            Message reply = MessageBuilder.memberInfoReply(client.getProcessID(), peer.get(), roomID, senderAddress);
            SocketUtils.sendPacket(client.getSocket(), reply);
        }
        else{
            throw new RuntimeException();
        }
    }

    private void handleMemberInfoReply(String data, InetAddress senderAddress) throws Exception {
        String[] dataVector = data.split(",");
        String peerID = dataVector[0];
        String peerUsername = dataVector[1];
        String peerIP = dataVector[2];
        String peerPort = dataVector[3];
        String roomID = dataVector[4];
        Peer peer = new Peer(peerID, peerUsername, InetAddress.getByName(peerIP), Integer.parseInt(peerPort));
        client.addPeer(peer);
        Optional<MissingPeerRecoveryData> recoveryData = missingPeers.stream().filter(x -> x.getPeerID().equals(peerID) && x.getRoomID().equals(roomID)).findFirst();
        if(recoveryData.isPresent()) {
            client.addRoomMember(recoveryData.get().getRoomID(), peer);
            sendAck(recoveryData.get().getAckSequenceNumber(), senderAddress);
        }
        else{
            throw new RuntimeException();
        }
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    private void sendAck(int sequenceNumber, InetAddress destinationAddress) throws IOException{
        Message response = MessageBuilder.ack(client.getProcessID(),sequenceNumber, destinationAddress);
        SocketUtils.sendPacket(client.getSocket(), response);
    }

    private void findMissingPeer(InetAddress destinationAddress, String missingPeerID, String roomID) throws IOException {
        Message request = MessageBuilder.memberInfoRequest(client.getProcessID(), missingPeerID, roomID, destinationAddress);
        SocketUtils.sendPacket(client.getSocket(), request);
    }

}

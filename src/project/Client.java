package project;

import project.CLI.CLI;
import project.Communication.Listeners.MulticastListener;
import project.Communication.Listeners.UnicastListener;
import project.Communication.Messages.*;
import project.Communication.AckWaitingLists.AckWaitingListMulticast;
import project.Communication.AckWaitingLists.AckWaitingListUnicast;
import project.Communication.NetworkUtils;
import project.Communication.MessageHandlers.MulticastMessageHandler;
import project.Communication.MessageHandlers.UnicastMessageHandler;
import project.Exceptions.*;
import project.Model.*;
import project.Communication.Sender;

import java.io.IOException;
import java.net.*;
import java.util.*;

/**
 *  This class is the controller of the application. Everytime the command line receives an input or
 *  a listener captures a packet, data is converted into the right format and is sent to this controller.
 *  If the input is correct, the model is updated, the view is notified, and potentially a message may be
 *  sent to other peers. If something goes wrong, an exception could be thrown, causing the requested
 *  action to be canceled.
 */
public class Client {

    private final Peer myself;
    private final InetAddress broadcastAddress;
    private final UnicastListener unicastListener;
    private final List<MulticastListener> multicastListeners;
    private final Sender sender;
    private final Set<Peer> peers;
    private final Set<Room> createdRooms;
    private final Set<Room> participatingRooms;
    private Room currentlyDisplayedRoom;
    private final Set<AckWaitingListUnicast> ackWaitingListsUni;
    private final Set<AckWaitingListMulticast> ackWaitingListsMulti;

    /**
     * Builds an instance of the application's controller.
     *
     * @param username The username given by the user.
     * @throws Exception Any error that is caused by wrong input.
     */
    public Client(String username) throws Exception {
        peers = new LinkedHashSet<>();
        createdRooms = new LinkedHashSet<>();
        participatingRooms = new LinkedHashSet<>();
        multicastListeners = new ArrayList<>();

        // connects to the network
        String ip;
        try(final DatagramSocket socket = new DatagramSocket()){
            socket.connect(InetAddress.getByName("8.8.8.8"), NetworkUtils.UNICAST_PORT_NUMBER);
            ip = socket.getLocalAddress().getHostAddress();
        }
        CLI.printDebug(ip);

        myself = new Peer(username, InetAddress.getByName(ip));
        unicastListener = new UnicastListener(new DatagramSocket(NetworkUtils.UNICAST_PORT_NUMBER), new UnicastMessageHandler(this));
        sender = new Sender();
        broadcastAddress = NetworkUtils.getBroadcastAddress(myself.getIpAddress());
        currentlyDisplayedRoom = null;
        this.ackWaitingListsUni = new HashSet<>();
        this.ackWaitingListsMulti = new HashSet<>();
    }

    // GETTERS

    /**
     * Returns the currently displayed room.
     * @return The currently displayed room.
     */
    public Room getCurrentlyDisplayedRoom(){
        return currentlyDisplayedRoom;
    }

    /**
     * Returns the data of the client as a Peer.
     * @return The data of the client as a Peer.
     */
    public Peer getPeerData(){
        return myself;
    }

    /**
     * Returns all the active rooms created by the client, as a Set.
     * @return All the active rooms created by the client, as a Set.
     */
    public Set<Room> getCreatedRooms() {
        return createdRooms;
    }

    /**
     * Returns all the active rooms in which the client is participating, as a Set.
     * @return All the active rooms in which the client is participating, as a Set.
     */
    public Set<Room> getParticipatingRooms() {
        return participatingRooms;
    }

    /**
     * Returns all the peers that the client has discovered to be active in the network.
     * @return All the peers that the client has discovered to be active in the network.
     */
    public Set<Peer> getPeers() {
        return peers;
    }

    // SPECIAL GETTERS

    /**
     * Returns the room specified by name if it does exist. Otherwise, throws an exception.
     * 
     * @param name The name of the room to be returned.
     * @return The room specified by name.
     * @throws InvalidParameterException If the room name passed as a parameter does not correspond to any room.
     */
    public Room getRoom(String name) throws InvalidParameterException{
        Set<Room> rooms = new HashSet<>(createdRooms);
        rooms.addAll(participatingRooms);
        Optional<Room> room = rooms.stream().filter(x -> x.getName().equals(name)).findFirst();
        if(room.isPresent()){
            return room.get();
        }
        else{
            throw new InvalidParameterException("There is no room with such a name: " + name);
        }
    }

    /**
     * Returns the room specified by UUID if it does exist. Otherwise, throws an exception.
     * 
     * @param uuid The uuid of the room to be returned.
     * @return The room specified by uuid.
     * @throws InvalidParameterException If the room uuid passed as a parameter does not correspond to any room.
     */
    public Room getRoom(UUID uuid) throws InvalidParameterException {
        Set<Room> rooms = new HashSet<>(createdRooms);
        rooms.addAll(participatingRooms);
        Optional<Room> room = rooms.stream().filter(x -> x.getIdentifier().equals(uuid)).findFirst();
        if(room.isPresent()){
            return room.get();
        }
        else{
            throw new InvalidParameterException("There is no room with such a UUID: " + uuid);
        }
    }

    // SETTERS

    /**
     * Sets the currently displayed room.
     * 
     * @param currentlyDisplayedRoom The room to be set as currently displayed.
     */
    public void setCurrentlyDisplayedRoom(Room currentlyDisplayedRoom) {
        this.currentlyDisplayedRoom = currentlyDisplayedRoom;
    }

    // PUBLIC METHODS

    /**
     * Handles the reception of a ping message: sends back to the peer who sent the message a Pong response
     * and adds the new peer in the peers list if it is not already there.
     * 
     * @param peer The peer that sent the ping message.
     * @throws IOException For any wrong input.
     * @throws PeerAlreadyPresentException If the peer is already inside the discovered peers list.
     */
    public void handlePing(Peer peer) throws IOException, PeerAlreadyPresentException {
        if(!peer.getIdentifier().equals(myself.getIdentifier())) {
            Message pongMessage = new PongMessage(peer.getIpAddress(), NetworkUtils.UNICAST_PORT_NUMBER, myself, null);
            sender.sendMessage(pongMessage);
            addPeer(peer);
        }
    }

    /**
     * Handles the reception of a pong message: adds the new peer in the peers list if it is not already there.
     * 
     * @param peer The peer that sent the pong message.
     * @throws PeerAlreadyPresentException If the peer is already inside the discovered peers list.
     */
    public void handlePong(Peer peer) throws PeerAlreadyPresentException{
        addPeer(peer);
    }

    /**
     * 
     * Handles the reception of a room membership message: prepares and send an acknowledgement back to the sender.
     * Then, adds the room to the local list of participating rooms, adds a listener to the room and (if necessary) the discovered peers.
     * 
     * @param room The room information
     * @param ackID The acknowledgement ID
     * @param senderID The ID of the sender of the message
     * @throws Exception if something goes wrong
     */
    public void handleRoomMembership(Room room, UUID ackID, UUID senderID) throws Exception {

        Optional<Peer> dstPeer = room.getRoomMembers().stream().filter(x -> x.getIdentifier().equals(senderID)).findFirst();
        AckMessage ack = new AckMessage(MessageType.ACK_UNI, myself.getIdentifier(), dstPeer.isPresent() ? dstPeer.get().getIpAddress() : broadcastAddress, NetworkUtils.UNICAST_PORT_NUMBER, ackID);
        sender.sendMessage(ack);

        if(participatingRooms.stream().noneMatch(x->x.getIdentifier().toString().equals(room.getIdentifier().toString()))){

            participatingRooms.add(room);
            addMulticastListener(room);

            // if some of the peers that are in the newly created room are not part of the known peers, add them
            Set<UUID> peersUUIDs = new HashSet<>();
            for(Peer peer : peers) {
                peersUUIDs.add(peer.getIdentifier());
            }

            for (Peer peerRoom : room.getRoomMembers()) {
                if(!peersUUIDs.contains(peerRoom.getIdentifier()) && !peerRoom.getIdentifier().equals(myself.getIdentifier())) {
                    peers.add(peerRoom);
                }
            }
            CLI.appendNotification(new Notification(NotificationType.SUCCESS, "You have been inserted into the room '" + room.getName() + "' (UUID: " + room.getIdentifier() + ")"));
        }
    }

    /**
     * This method handles a text message received in a room. Sends back an acknowledgement, checks the vector clock status and decides whether to accept,
     * discard or queue the message.
     * @param roomTextMessage The received message
     * @throws Exception if something goes wrong
     */
    public void handleRoomText(RoomTextMessage roomTextMessage) throws Exception {
        try {
            Room room = getRoom(roomTextMessage.getRoomText().roomUUID());

            Optional<Peer> dstPeer = room.getRoomMembers().stream().filter(x -> x.getIdentifier().equals(roomTextMessage.getSenderUUID())).findFirst();
            AckMessage ack = new AckMessage(MessageType.ACK_MULTI, myself.getIdentifier(), dstPeer.isPresent() ? dstPeer.get().getIpAddress() : broadcastAddress, NetworkUtils.UNICAST_PORT_NUMBER, roomTextMessage.getAckID());
            sender.sendMessage(ack);

            MessageCausalityStatus status = checkMessageCausality(room.getRoomVectorClock(), roomTextMessage);
            switch (status) {
                case ACCEPTED -> {
                    room.addRoomText(roomTextMessage.getRoomText());
                    room.updateVectorClock(roomTextMessage.getVectorClock());
                    checkDeferredMessages(room);
                }
                case QUEUED -> room.getMessageToDeliverQueue().add(roomTextMessage);
                case DISCARDED -> {
                }
            }
        }
        catch (InvalidParameterException ignored) {}
    }

    /**
     * This method handles the reception of a delete room message. If the room is present in the participatingRoom list, deletes all the logs of
     * acknowledgements the client is waiting to receive, removes the room from participatingRooms, if necessary, sets the currently displayed room
     * to null, and in the end sends an acknowledgement message back to the sender.
     * 
     * @param roomUUID The ID of the room to be deleted.
     * @param ackID The acknowledgement ID.
     * @param senderID The ID of the sender of the message.
     * @throws Exception if something goes wrong
     */
    public void handleDeleteRoom(UUID roomUUID, UUID ackID, UUID senderID) throws Exception {
        Optional<Room> room = participatingRooms.stream()
                .filter(x -> x.getIdentifier().equals(roomUUID)).findFirst();
        if (room.isPresent()) {
            Room roomToBeRemoved = room.get();

            Optional<Peer> dstPeer = roomToBeRemoved.getRoomMembers().stream().filter(x -> x.getIdentifier().equals(senderID)).findFirst();
            AckMessage ack = new AckMessage(MessageType.ACK_MULTI, myself.getIdentifier(), dstPeer.isPresent()?dstPeer.get().getIpAddress():broadcastAddress, NetworkUtils.UNICAST_PORT_NUMBER, ackID);

            for( AckWaitingListMulticast awl: ackWaitingListsMulti ) {
                InetAddress dstAddress = awl.getMessageToResend().getDestinationAddress();
                if (roomToBeRemoved.getMulticastAddress().equals(dstAddress)) {
                    awl.onRoomDeletion();
                    ackWaitingListsMulti.remove(awl);
                }
            }
            
            participatingRooms.remove(roomToBeRemoved);
            if (currentlyDisplayedRoom.getIdentifier().toString().equals(roomToBeRemoved.getIdentifier().toString())) {
                currentlyDisplayedRoom = null;
            }

            CLI.appendNotification(new Notification(NotificationType.INFO, "The room '" + roomToBeRemoved.getName() + "' has been deleted."));

            sender.sendMessage(ack);
        }
    }

    /**
     * This method handles a leave network message, it sends back an acknowledgement, deletes the room from the participating rooms list or the created rooms list,
     * removes the logs for acknowledgements not yet received relatively to the room, removes the peer who sent the message, and if necessary sets to null
     * the currently displayed room.
     * 
     * @param peer The peer who sent the message.
     * @param ackID The acknowledgement ID.
     * @param senderID The ID of the sender of the message.
     * @throws IOException if there's an I/O error
     */
    public void handleLeaveNetwork(Peer peer, UUID ackID, UUID senderID) throws IOException{

        Optional<Peer> dstPeer = peers.stream().filter(x -> x.getIdentifier().equals(senderID)).findFirst();
        AckMessage ack = new AckMessage(MessageType.ACK_UNI, myself.getIdentifier(), dstPeer.isPresent()?dstPeer.get().getIpAddress():broadcastAddress , NetworkUtils.UNICAST_PORT_NUMBER, ackID);
        sender.sendMessage(ack);

        deleteRoomAfterLeaveNetwork(peer, createdRooms);
        deleteRoomAfterLeaveNetwork(peer, participatingRooms);

        ackWaitingListsMulti.removeIf(awl -> awl.getAckingPeers().contains(peer));

        for (AckWaitingListUnicast awl : ackWaitingListsUni) {
            for (Message m : awl.getMessagesToResend()) {
                if (peer.getIpAddress().equals(m.getDestinationAddress())) {
                    ackWaitingListsUni.remove(awl);
                    break;
                }
            }
        }

        peers.removeIf(p -> p.getIdentifier().toString().equals(peer.getIdentifier().toString()));

        if (!(participatingRooms.contains(currentlyDisplayedRoom) || createdRooms.contains(currentlyDisplayedRoom))) {
            currentlyDisplayedRoom = null;
        }
    }

    /**
     * This private method deletes all the rooms in which a specified peer is present.
     * This is done for a specified set of rooms.
     * 
     * @param peer The peer whose rooms are to be deleted
     * @param rooms The set of rooms in which rooms are to be removed
     */
    private void deleteRoomAfterLeaveNetwork(Peer peer, Set<Room> rooms) {
        List<Room> roomsToDelete = new ArrayList<>();
        for (Room r : rooms) {
            for (Peer p : r.getRoomMembers()) {
                if (p.getIdentifier().toString().equals(peer.getIdentifier().toString())) {
                    roomsToDelete.add(r);
                    CLI.appendNotification(new Notification(NotificationType.INFO, "The room '"+r.getName()+"' has been deleted because "+peer.getUsername()+" has left the network!"));
                    break;
                }
            }
        }
        roomsToDelete.forEach(rooms::remove);
    }

    /**
     * This method handles the reception of an acknowledgement message sent in unicast. It removes the sender's ID from the list of pending acknowledgements
     * and eventually, if there are no more pending acks, stops the timer for re-sending the message.
     * @param ackID The ID of the acknowledgement
     * @param senderID The ID of the sender
     */
    public void handleAckUni(UUID ackID, UUID senderID) {

        for(AckWaitingListUnicast awl: ackWaitingListsUni) {
            if(awl.getAckID().equals(ackID)) {

                Optional<Peer> dstPeer = peers.stream().filter(x -> x.getIdentifier().equals(senderID)).findFirst();
                awl.update(dstPeer.map(Peer::getIpAddress).orElse(null));

                if (awl.isComplete()) {
                    ackWaitingListsUni.remove(awl);
                }

                break;
            }
        }
    }

    /**
     * This method handles the reception of an acknowledgement message sent in multicast. It removes the sender's ID from the list of pending acknowledgements
     * and eventually, if there are no more pending acks, stops the timer for re-sending the message.
     * 
     * @param ackID The ID of the acknowledgement
     * @param senderID The ID of the sender
     */
    public void handleAckMulti(UUID ackID, UUID senderID) {

        for(AckWaitingListMulticast awl: ackWaitingListsMulti) {
            if(awl.getAckID().equals(ackID)) {
                Optional<Peer> dstPeer = peers.stream().filter(x -> x.getIdentifier().equals(senderID)).findFirst();
                awl.update(dstPeer.orElse(null));

                if (awl.isComplete()) {
                    ackWaitingListsMulti.remove(awl);
                }

                break;
            }
        }
    }

    /**
     * This method sends a ping message in order to discover if new peers are connected to the network
     * 
     * @throws IOException if there's an I/O error
     */
    public void discoverNewPeers() throws IOException{
        Message pingMessage = new PingMessage(broadcastAddress, NetworkUtils.UNICAST_PORT_NUMBER, myself, null);
        sender.sendMessage(pingMessage);
    }

    /**
     * This method creates a new room with the specified peers and adds it to the list of created rooms, 
     * then sends a message to the peers to tell them that they have been added to the room.
     * Sets the pending acknowledgements list, and starts the timer. 
     * 
     * @param roomName The name of the room.
     * @param peerIds The list of ID of the chosen peers
     * @throws IOException if there's an I/O error
     */
    public void createRoom(String roomName, String[] peerIds) throws IOException {

        // creates the set of peer members
        Set<Peer> roomMembers = new HashSet<>();
        // adds myself to the set
        roomMembers.add(myself);

        // iterates over the set and understands which peers the user picked
        Iterator<Peer> iterator = peers.iterator();
        Set<Integer> choices = new HashSet<>();
        for(String peerId: peerIds){
            choices.add(Integer.parseInt(peerId));
        }
        int index = 1;
        while(iterator.hasNext()){
            Peer peer = iterator.next();
            if(choices.contains(index)){
                roomMembers.add(peer);
            }
            index++;
        }

        List<Message> messagesToResend = new ArrayList<>();
        UUID ackID = UUID.randomUUID();

        // creates the room and the associated multicast listener
        Room room = new Room(roomName, roomMembers, NetworkUtils.generateRandomMulticastAddress());

        for (Peer p : room.getRoomMembers()) {
            if(!p.getIdentifier().equals(myself.getIdentifier())) {
                Message roomMembershipMessage = new RoomMembershipMessage(myself.getIdentifier(), p.getIpAddress(), NetworkUtils.UNICAST_PORT_NUMBER, room, ackID);
                messagesToResend.add(roomMembershipMessage);
            }
        }

        scheduleAckUni(ackID, messagesToResend);
        createdRooms.add(room);
        addMulticastListener(room);

        for(Message m : messagesToResend) {
            sender.sendMessage(m);
        }
    }

    /**
     * This method deletes one of the created rooms and sends a delete message to the participating peers.
     * Then, it sets up the pending acknowledgements list, and starts the timer. 
     * 
     * @param room The selected room to be deleted
     * @throws IOException if there's an I/O error
     */
    public void deleteCreatedRoom(Room room) throws IOException {
        UUID ackID = UUID.randomUUID();

        InetAddress multicastAddress = room.getMulticastAddress();
        UUID roomID = room.getIdentifier();

        Message deleteRoomMessage = new DeleteRoomMessage(myself.getIdentifier(),
                multicastAddress, NetworkUtils.MULTICAST_PORT_NUMBER, roomID, ackID);
        
        Set<Peer> peers = new HashSet<>(room.getRoomMembers());
        peers.removeIf(p -> p.getIdentifier().toString().equals(myself.getIdentifier().toString()));
        
        createdRooms.remove(room);

        scheduleAckMulti(ackID, peers, deleteRoomMessage);
        sender.sendMessage(deleteRoomMessage);
    }

    /**
     * This method deletes one of the created rooms and sends a delete message to the participating peers.
     * Then, it sets up the pending acknowledgements list, and starts the timer. 
     * 
     * @param roomName The name of the room to be deleted.
     * @throws InvalidParameterException Thrown when there is no room with the chosen name
     * @throws SameRoomNameException Thrown when there are multiple rooms with the chosen name
     * @throws IOException if there's an I/O error
     */
    public void deleteCreatedRoom(String roomName) throws InvalidParameterException, SameRoomNameException, IOException {
        List<Room> filteredRooms = createdRooms.stream()
                .filter(x -> x.getName().equals(roomName)).toList();

        int numberOfElements = filteredRooms.size();

        if (numberOfElements == 0) {
            throw new InvalidParameterException("There is no room that can be deleted with the name '" + roomName + "'");
        } else if (numberOfElements > 1) {
            throw new SameRoomNameException("There is more than one room that can be deleted with the name '" + roomName + "'", filteredRooms);
        } else {
            Room room = filteredRooms.get(0);
            deleteCreatedRoom(room);
        }
    }

    /**
     * Sends a text in the room chat, thus to all the members of the room.
     * Then, it sets up the pending acknowledgements list, and starts the timer.
     * 
     * @param roomText The text to be sent to the room
     * @throws IOException if there's an I/O error
     */
    public void sendRoomText(RoomText roomText) throws IOException {

        UUID ackID = UUID.randomUUID();

        currentlyDisplayedRoom.addRoomText(roomText);
        currentlyDisplayedRoom.incrementVectorClock(myself.getIdentifier());
        VectorClock vc = new VectorClock(currentlyDisplayedRoom.getRoomVectorClock().getMap());
        Message message = new RoomTextMessage(vc, myself.getIdentifier(),
                currentlyDisplayedRoom.getMulticastAddress(), NetworkUtils.MULTICAST_PORT_NUMBER, roomText, ackID);
        
        Set<Peer> peers = new HashSet<>(currentlyDisplayedRoom.getRoomMembers());
        peers.removeIf(p -> p.getIdentifier().toString().equals(myself.getIdentifier().toString()));
        
        scheduleAckMulti(ackID, peers, message);
        sender.sendMessage(message);
    }

    /**
     * This method sends a message to all the peers in the network, then it deletes all the acknowledgement locks and tears down the connection.
     *  
     * @throws IOException if there's an I/O error
     */
    public void close() throws IOException {

        List<Message> messagesToResend = new ArrayList<>();
        UUID ackID = UUID.randomUUID();

        for (Peer p : peers) {
            if(!p.getIdentifier().equals(myself.getIdentifier())) {
                Message leaveNetworkMessage = new LeaveNetworkMessage(p.getIpAddress(), NetworkUtils.UNICAST_PORT_NUMBER, myself, ackID);
                messagesToResend.add(leaveNetworkMessage);
                sender.sendMessage(leaveNetworkMessage);
            }
        }

        if(!messagesToResend.isEmpty()) {
            scheduleAckUni(ackID, messagesToResend);
            AckWaitingListUnicast awl = null;
            for (AckWaitingListUnicast a : ackWaitingListsUni) {
                if (a.getAckID().equals(ackID)) {
                    awl = a;
                    break;
                }
            }
            do {
                CLI.printToExit();
            } while (awl == null || !awl.isComplete());
            // closes the sockets and the input scanner
            CLI.printDebug("You are now exiting the system!");
            CLI.printDebug("Farewell, space cowboy...");
        }

        unicastListener.close();
        for (MulticastListener multicastListener : multicastListeners) {
            multicastListener.close();
        }

    }

    /**
     * This method looks for the presence of a room in the created rooms and participating rooms lists
     * @param roomName the name of the room
     * @return True if a room is found, False otherwise.
     * @throws SameRoomNameException if there's a room with the same name
     */
    public boolean existsRoom(String roomName) throws SameRoomNameException {
        List<Room> allRooms = new ArrayList<>();
        allRooms.addAll(participatingRooms);
        allRooms.addAll(createdRooms);
        List<Room> matchingRooms = allRooms.stream().filter(x -> x.getName().equals(roomName)).toList();

        if(matchingRooms.isEmpty()){
            return false;
        }
        else if (matchingRooms.size() > 1){
            throw new SameRoomNameException("There are " + matchingRooms.size() + " rooms with the same name.", matchingRooms);
        }

        return true;
    }

    // PRIVATE METHODS

    /**
     * This method adds a peer in the peers list, if it is not already present
     * @param p the peer to add
     * @throws PeerAlreadyPresentException if the peer is already present
     */
    private void addPeer(Peer p) throws PeerAlreadyPresentException {
        for (Peer peer : this.peers) {
            if (p.getIdentifier().toString().equals(peer.getIdentifier().toString())) {
                throw new PeerAlreadyPresentException("There's already a peer with such an ID.");
            }
        }
        peers.add(p);
    }

    /**
     * This method adds a multicast listener to a specified room.
     * 
     * @param room The room to which the multicast listener has to be added.
     * @throws IOException if there's an I/O error
     */
    private void addMulticastListener(Room room) throws IOException {
        MulticastSocket multicastSocket = new MulticastSocket(NetworkUtils.MULTICAST_PORT_NUMBER);
        InetSocketAddress inetSocketAddress = new InetSocketAddress(room.getMulticastAddress(), NetworkUtils.MULTICAST_PORT_NUMBER);
        NetworkInterface networkInterface = NetworkUtils.getAvailableMulticastIPv4NetworkInterface();
        multicastSocket.joinGroup(inetSocketAddress, networkInterface);
        multicastListeners.add(new MulticastListener(multicastSocket, new MulticastMessageHandler(this), inetSocketAddress, networkInterface));
    }

    /**
     * Method used to check if the causality between messages is respected.
     * @param message The message to analyze.
     *
     * @return ACCEPTED if the message respects the causality and thus can be processed, QUEUED if the message cannot
     * be processed because a message is missing (and we have to wait for it), DISCARDED if it's an old message that should be discarded.
     */
    private MessageCausalityStatus checkMessageCausality(VectorClock roomVectorClock, RoomTextMessage message) throws InvalidParameterException {
        CLI.printDebug("Message timestamp: " + message.getVectorClock().getValues());
        CLI.printDebug("Room timestamp: " + roomVectorClock.getValues());

        if(message.getVectorClock().isLessThanOrEqual(roomVectorClock)){ // received <= room
            CLI.printDebug("DISCARDED duplicate");
            return MessageCausalityStatus.DISCARDED;
        }

        if(!roomVectorClock.isLessThan(message.getVectorClock()) && !message.getVectorClock().isLessThan(roomVectorClock)){
            
            VectorClock sliceReceived = message.getVectorClock().copySlice(myself.getIdentifier());
            VectorClock sliceRoom = roomVectorClock.copySlice(myself.getIdentifier());

            if (Math.abs(sliceReceived.sum() - sliceRoom.sum()) <= 1) {
                CLI.printDebug("ACCEPTED concurrent");
                return MessageCausalityStatus.ACCEPTED; // events are concurrent
            }
            else {
                CLI.printDebug("QUEUED possible concurrency");
                return MessageCausalityStatus.QUEUED;
            }
            
        }

        // events are causally related
        UUID senderID = message.getSenderUUID();
        VectorClock sliceReceived = message.getVectorClock().copySlice(senderID);
        VectorClock sliceRoom = roomVectorClock.copySlice(senderID);

        if(sliceReceived.isLessThanOrEqual(sliceRoom) && message.getVectorClock().getValue(senderID).equals(roomVectorClock.getValue(senderID) + 1)){
            CLI.printDebug("ACCEPTED correct");
            return MessageCausalityStatus.ACCEPTED;
        }
        else {
            CLI.printDebug("QUEUED");
            return MessageCausalityStatus.QUEUED;
        }
    }

    /**
     * Method to check and process deferred messages (i.e. messages that wait to be processed)
     *
     * @param room The room from which the messages are fetched
     * @throws Exception if there is any problem when handling the message.
     */
    private void checkDeferredMessages(Room room) throws Exception {
        Iterator<RoomTextMessage> iterator = room.getMessageToDeliverQueue().iterator();
        while (iterator.hasNext()) {
            RoomTextMessage roomTextMessage = iterator.next();
            MessageCausalityStatus status = checkMessageCausality(room.getRoomVectorClock(), roomTextMessage);
            if (status.equals(MessageCausalityStatus.ACCEPTED)) {
                iterator.remove();
                handleRoomText(roomTextMessage);
                checkDeferredMessages(room);
            }
        }
    }

    /**
     * Method to build an AckWaitingList for unicast acks and start the related timer.
     *
     * @param ackID unique ID of the AckWaitingList
     * @param messagesToResend list of messages to resend at timeout
     */
    private void scheduleAckUni(UUID ackID, List<Message> messagesToResend){
        AckWaitingListUnicast awl = new AckWaitingListUnicast(ackID, sender, messagesToResend);
        ackWaitingListsUni.add(awl);
        awl.startTimer();
    }

    /**
     * Method to build an AckWaitingList for multicast acks and start the related timer.
     *
     * @param ackID unique ID of the AckWaitingList
     * @param peers set of peers who have to send their ack
     */
    private void scheduleAckMulti(UUID ackID, Set<Peer> peers, Message message){
        AckWaitingListMulticast awl = new AckWaitingListMulticast(ackID, sender, peers, message);
        ackWaitingListsMulti.add(awl);
        if (message.getType().equals(MessageType.ROOM_TEXT)) {
            RoomTextMessage m = (RoomTextMessage)message;
            CLI.printDebug("awl instantiated for: " + m.getRoomText());
        }
        awl.startTimer();
    }

}
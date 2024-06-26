package project.Communication.Messages;

import project.Model.Room;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.UUID;

/**
 * This class represents the message that is sent by a peer who has created a room to notify the participants that they
 * have been added to that room. The related {@link Room} instance is also sent, so that everyone can access all the
 * necessary data.
 */
public class RoomMembershipMessage extends Message implements Serializable {

    private final Room room;

    /**
     * Builds an instance of {@link RoomMembershipMessage}.
     *
     * @param senderUUID The UUID of the sender.
     * @param destinationAddress The destination address of the message.
     * @param destinationPort The destination port of the message.
     * @param room The created room.
     * @param ackID The ackID of the message.
     */
    public RoomMembershipMessage(UUID senderUUID, InetAddress destinationAddress, int destinationPort, Room room, UUID ackID) {
        super(MessageType.ROOM_MEMBERSHIP, senderUUID, destinationAddress, destinationPort, ackID);
        this.room = room;
    }

    /**
     * Returns the created room.
     *
     * @return The created room.
     */
    public Room getRoom() {
        return room;
    }

}

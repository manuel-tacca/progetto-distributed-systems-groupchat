package project.Communication.Messages;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.UUID;

/**
 * This class represents the message that is sent by the creator of a room when they want to notify other peers that
 * the room has been deleted.
 */
public class DeleteRoomMessage extends Message implements Serializable {
    private final UUID roomUUID;

    /**
     * Builds an instance of {@link DeleteRoomMessage}.
     *
     * @param senderUUID The UUID of the sender.
     * @param destinationAddress The destination address of the message.
     * @param destinationPort The destination port of the message.
     * @param roomUUID The UUID of the room to delete.
     * @param ackID The ackID of the message.
     */
    public DeleteRoomMessage(UUID senderUUID, InetAddress destinationAddress, int destinationPort, UUID roomUUID, UUID ackID) {
        super(MessageType.DELETE_ROOM, senderUUID, destinationAddress, destinationPort, ackID);
        this.roomUUID = roomUUID;
    }

    /**
     * Returns the UUID of the room to delete.
     *
     * @return The UUID of the room to delete.
     */
    public UUID getRoomUUID() { return roomUUID; }
}

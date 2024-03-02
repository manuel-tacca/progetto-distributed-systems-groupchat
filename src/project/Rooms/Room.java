package project.Rooms;

import project.Exceptions.PeerAlreadyPresentException;
import project.Peer;

import java.io.PrintStream;
import java.util.List;
import java.util.UUID;

public class Room {
    protected UUID identifier;
    protected String name;
    protected List<Peer> otherRoomMembers; // all the member but the client itself!

    protected final PrintStream out = System.out;

    public Room(String uuid, String name){
        this.identifier = UUID.fromString(uuid);
        this.name = name;
    }

    public void addPeer(Peer newPeer) throws PeerAlreadyPresentException{
        for (Peer peer: otherRoomMembers){
            if (peer.getIdentifier() == newPeer.getIdentifier()){
                throw new PeerAlreadyPresentException("Peer (" + newPeer.getIdentifier() + ", " + newPeer.getUsername() + ") is already a member of the " + name + " room.");
            }
        }
        otherRoomMembers.add(newPeer);
    }

    public String getName() {
        return name;
    }

    public List<Peer> getOtherRoomMembers() {
        return otherRoomMembers;
    }

    public UUID getIdentifier(){
        return identifier;
    }
}
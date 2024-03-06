package project.CLI;

import project.Client;
import project.Peer;
import project.Rooms.CreatedRoom;
import project.Rooms.Room;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class CLI {

    private static final PrintStream out = System.out;

    private static final String BOLD = "\033[1m";
    private static final String YELLOW = "\033[33m";
    private static final String GREEN = "\033[32m";
    private static final String RED = "\033[31m";
    private static final String BLUE = "\033[34m";
    private static final String VIOLET = "\033[35m\033[45m";
    private static final String RESET = "\033[0m";

    private static final List<String> notifications = new ArrayList<>();

    public static void printJoin(){
        //clearConsole();
        out.println(BOLD + "Welcome to the highly available, causally ordered group chat!" + RESET);
        out.println("Please, enter a nickname:");
    }

    public static void printMenu(Client user){
        //clearConsole();
        out.println();
        out.println(BOLD + "Welcome to the highly available, causally ordered group chat!" + RESET);
        printInfo("You are using the application as: " + user.getPeerData().getUsername());
        printInfo("Discovered peers: " + user.getPeers().size());
        printInfo("Available rooms: " + (user.getCreatedRooms().size() + user.getParticipatingRooms().size()));
        out.println(BOLD + "Notifications:" + RESET);
        printNotifications();
        out.println(BOLD + "What do you want to do?" + RESET);
        out.println("Available commands: " +
                BOLD + "create " + RESET + "[room_name], " +
                BOLD + "join " + RESET + "[room_name], " +
                BOLD + "delete " + RESET + "[room_name], " +
                BOLD + "list " + RESET + "[peers|rooms], " +
                BOLD + "quit" + RESET);
    }

    public static void printPeers(List<Peer> peers){
        int i=1;
        for (Peer peer : peers) {
            out.println();
            out.println("Peer"+i+":");
            out.println("\tID: " + peer.getIdentifier());
            out.println("\tUsername: " + peer.getUsername());
            out.println();
            i++;
        }
    }

    public static void printRooms(List<CreatedRoom> createdRooms, List<Room> participatingRooms){
        int index = 1;
        if (!createdRooms.isEmpty()) {
            out.println("Created Rooms: ");
            for (Room r : createdRooms){
                out.println("Room "+index+" : "+r.getName());
                out.println("ID: "+r.getIdentifier());
                index++;
            }
            out.println();
        }
        if (!participatingRooms.isEmpty()) {
            out.println("Participating Rooms: ");
            for (Room r : participatingRooms){
                out.println("Room "+index+" : "+r.getName());
                out.println("ID: "+r.getIdentifier());
                index++;
            }
            out.println();
        }
        if (participatingRooms.isEmpty() && createdRooms.isEmpty()) {
            out.println("There are no rooms yet!");
        }
    }

    public static void printQuestion(String string){
        out.println(BOLD + string + RESET);
    }

    public static void printInfo(String string){
        out.println(BOLD + BLUE + string + RESET);
    }

    public static void printSuccess(String string){
        out.println(BOLD + GREEN + string + RESET);
    }

    public static void printWarning(String string){
        out.println(BOLD + YELLOW + string + RESET);
    }

    public static void printError(String string){
        out.println(BOLD + RED + string + RESET);
    }

    public static void printNotifications(){
        if(notifications.size() != 0) {
            for (String notification : notifications) {
                out.println(BOLD + VIOLET + notification + RESET);
            }
            notifications.clear();
        }
        else{
            out.println("No notification yet.");
        }
    }

    private static void clearConsole() {
        try {
            final String os = System.getProperty("os.name");

            if (os.contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                Runtime.getRuntime().exec("clear");
            }
        } catch (final Exception e) {
            printError("Could not clean the console: " + e.getMessage());
        }
    }

    public static void appendNotification(String message){
        notifications.add(message);
    }

}
package project.Exceptions;

public class EmptyRoomException extends Exception{
    private String message;

    public EmptyRoomException(String message){
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }
}

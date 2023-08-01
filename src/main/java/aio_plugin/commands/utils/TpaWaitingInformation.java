package aio_plugin.commands.utils;

public class TpaWaitingInformation {
    private long timeStamp;
    private boolean direction; //true for "RequestMaker to ReceivingPlayer", false for "ReceivingPlayer to RequestMaker"

    public TpaWaitingInformation(long timeStamp, boolean direction) {
        this.timeStamp = timeStamp;
        this.direction = direction;
    }

    public TpaWaitingInformation() {

    }

    public void setDirection(boolean direction) {
        this.direction = direction;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public boolean getDirection() {
        return direction;
    }
}

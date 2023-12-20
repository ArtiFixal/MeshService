package meshservice;

/**
 * Describes status of the {@code Service}.
 * 
 * @author ArtiFixal
 */
public enum ServiceStatus {
    STARTING(0),
    RUNNING(1),
    CLOSING(2),
    CLOSED(3);
    
    private final int statusCode;
    
    private ServiceStatus(int statusCode)
    {
        this.statusCode=statusCode;
    }

    public int getStatusCode(){
        return statusCode;
    }
    
    public static ServiceStatus interperFromNumber(int number)
    {
        return switch (number) {
            case 0 -> ServiceStatus.STARTING;
            case 1 -> ServiceStatus.RUNNING;
            case 2 -> ServiceStatus.CLOSING;
            case 3 -> ServiceStatus.CLOSED;
            default -> throw new IllegalArgumentException("Unknown status");
        };
    }

    @Override
    public String toString(){
        return switch(statusCode){
            case 0 -> "starting";
            case 1 -> "running";
            case 2 -> "closing";
            case 3 -> "closed";
            default -> throw new IllegalArgumentException("Unknown status");
        };
    }
}
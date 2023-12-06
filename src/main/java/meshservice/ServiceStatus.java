package meshservice;

/**
 * Describes status of the {@code Service}.
 * 
 * @author ArtiFixal
 */
public enum ServiceStatus {
    STARTING,
    RUNNING,
    CLOSING,
    CLOSED;
    
    public static ServiceStatus interperFromNumber(int number)
    {
        return switch (number) {
            case 0 -> ServiceStatus.STARTING;
            case 1 -> ServiceStatus.RUNNING;
            case 2 -> ServiceStatus.CLOSING;
            case 3 -> ServiceStatus.CLOSED;
            default -> throw new AssertionError();
        };
    }
}
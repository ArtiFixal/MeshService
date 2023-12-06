package meshservice.services.manager;

/**
 * Exception thrown when sought service was not found.
 * 
 * @author ArtiFixal
 */
public class ServiceNotFoundException extends Exception{

    private final String serviceName;

    public ServiceNotFoundException(String serviceName){
        super("Unable to find service: "+serviceName);
        this.serviceName=serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}

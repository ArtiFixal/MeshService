package meshservice.services;

import meshservice.ServiceStatus;

/**
 * Class responsible for storing data about given {@code Service}.
 * 
 * @author ArtiFixal
 */
public class ServiceData {
    /**
     * What this service does.
     */
    public String serviceType;
    
    /**
     * Fields which request must contain.
     */
    public String[] serviceRequestRequiredFields;
    
    /**
     * Current status of the service.
     */
    private ServiceStatus status;
    
    /**
     * Time since service was inactive.
     */
    private long inactiveTimer;
    
    /**
     * Port on which service listens.
     */
    private final int port;

    public ServiceData(String serviceType,int port) {
        this.serviceType=serviceType;
        status=ServiceStatus.STARTING;
        inactiveTimer=0;
        this.port=port;
    }

    public ServiceData(String serviceType,int port,ServiceStatus status) {
        this.serviceType=serviceType;
        this.status=status;
        inactiveTimer=0;
        this.port=port;
    }

    public String getServiceType() {
        return serviceType;
    }

    public ServiceStatus getStatus() {
        return status;
    }

    public int getPort() {
        return port;
    }

    public long getInactiveTimer() {
        return inactiveTimer;
    }

    public String[] getServiceRequestRequiredFields(){
        return serviceRequestRequiredFields;
    }
    
    public void setStatus(ServiceStatus status) {
        this.status=status;
    }

    public void setInactiveTimer(long newValue) {
        inactiveTimer=newValue;
    }
    
    public void increaseInactiveTimer(long value)
    {
        inactiveTimer+=value;
    }
}

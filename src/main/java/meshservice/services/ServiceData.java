package meshservice.services;

import meshservice.ServiceStatus;

/**
 * Class responsible for storing data about given {@code Service}.
 * 
 * @author ArtiFixal
 */
public class ServiceData {
    public String serviceName;
    private ServiceStatus status;
    private long inactiveTimer;
    private final int port;

    public ServiceData(String serviceName,int port) {
        this.serviceName=serviceName;
        status=ServiceStatus.STARTING;
        inactiveTimer=0;
        this.port=port;
    }

    public ServiceData(String serviceName,int port,ServiceStatus status) {
        this.serviceName=serviceName;
        this.status=status;
        inactiveTimer=0;
        this.port=port;
    }

    public String getServiceName() {
        return serviceName;
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

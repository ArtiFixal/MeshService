package meshservice.services;

import meshservice.ServiceStatus;

/**
 * Class responsible for storing data about given {@code Service}.
 * 
 * @author ArtiFixal
 */
public class ServiceData {
    public static final String[] EMPTY_ARRAY=new String[0];
    /**
     * What this service does.
     */
    public String serviceType;
    
    /**
     * Fields which request must contain.
     */
    public String[] serviceRequestRequiredFields;
    
    /**
     * Fields which response to the client should contain.
     */
    public String[] serviceAdditionalResponseFields;
    
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

    public ServiceData(String serviceType,int port)
    {
        this(serviceType,port,ServiceStatus.STARTING,EMPTY_ARRAY,EMPTY_ARRAY);
    }
    
    public ServiceData(String serviceType,int port,String[] serviceRequestRequiredFields)
    {
        this(serviceType,port,ServiceStatus.STARTING,
            serviceRequestRequiredFields,EMPTY_ARRAY);
    }
    
    public ServiceData(String serviceType,int port,ServiceStatus status,String[] serviceRequestRequiredFields){
        this(serviceType,port,status,serviceRequestRequiredFields,EMPTY_ARRAY);
    }

    public ServiceData(String serviceType,int port,ServiceStatus status,String[] serviceRequestRequiredFields,String[] serviceAdditionalResponseFields)
    {
        this.serviceType=serviceType;
        this.status=status;
        inactiveTimer=0;
        this.port=port;
        this.serviceRequestRequiredFields=serviceRequestRequiredFields;
        this.serviceAdditionalResponseFields=serviceAdditionalResponseFields;
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

    public String[] getServiceAdditionalResponseFields(){    
        return serviceAdditionalResponseFields;
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

package meshservice;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import meshservice.services.ServiceData;

/**
 * Stores data about {@code ServiceAgent} available services.
 *
 * @author ArtiFixal
 */
public class AgentServicesInfo{
    
    /**
     * UUID of an agent.
     */
    private final UUID agentID;
    
    /**
     * Services which agent can run.
     */
    ArrayList<String> availableServices;
    
    /**
     * Already running services.
     */
    ConcurrentHashMap<String,ConcurrentHashMap<String,ServiceData>> runningServices;
    
    /**
     * Agent host.
     */
    private String host;
    
    /**
     * Agent port.
     */
    private int port;
    
    public AgentServicesInfo(String agentID,String host,int port,ArrayList<String> availableServices){
        this.agentID=UUID.fromString(agentID);
        this.host=host;
        this.port=port;
        this.availableServices=availableServices;
        runningServices=new ConcurrentHashMap<>();
    }

    public UUID getAgentID(){
        return agentID;
    }

    public String getHost(){
        return host;
    }

    public int getPort(){
        return port;
    }
    
    /**
     * Adds new service to the {@code runningServices}.
     * 
     * @param serviceType New service type. 
     * @param serviceUUID New service UUID.
     * @param serviceData New service data.
     */
    public void addNewService(String serviceType,String serviceUUID,ServiceData serviceData)
    {
        if(!runningServices.containsKey(serviceType))
        {
            ConcurrentHashMap<String,ServiceData> services=new ConcurrentHashMap(4);
            services.put(serviceUUID,serviceData);
            runningServices.put(serviceType,services);
        }
        else
            runningServices.get(serviceType).put(serviceUUID,serviceData);
    }

    public ConcurrentHashMap<String,ConcurrentHashMap<String,ServiceData>> getRunningServices(){
        return runningServices;
    }

    public ArrayList<String> getAvailableServices(){
        return availableServices;
    }
    
    public ServiceStatus getServiceStatus(String service,String serviceUUID){
        if(!runningServices.containsKey(service))
            return ServiceStatus.CLOSED;
        return runningServices.get(service).get(serviceUUID).getStatus();
    }

    public void setServiceStatus(String serviceType,String serviceUUID,ServiceStatus newStatus){
        if(!runningServices.containsKey(serviceType))
            throw new IllegalArgumentException("Given service type never started");
        runningServices.get(serviceType).get(serviceUUID).setStatus(newStatus);
    }
}

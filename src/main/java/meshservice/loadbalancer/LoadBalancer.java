package meshservice.loadbalancer;

import meshservice.AgentServicesInfo;
import meshservice.communication.Hostport;
import meshservice.services.ServiceData;
import meshservice.services.manager.ServiceNotFoundException;

/**
 * Distributes traffic through available service instances.
 * 
 * @author ArtiFixal
 */
public interface LoadBalancer {
    
    /**
     * Selects best traffic destination.
     * 
     * @param serviceType Which service type to select.
     * 
     * @return Service address.
     * 
     * @throws ServiceNotFoundException If there is no running service of given 
     * type.
     */
    public Hostport balance(String serviceType) throws ServiceNotFoundException;
    
    /**
     * Adds new agent to the balancer pool.
     * 
     * @param agentName New agent name.
     * @param agent New agent info.
     */
    public void addNewAgentDestination(String agentName,AgentServicesInfo agent);
    
    /**
     * Adds new service to the agent pool.
     * 
     * @param agentName Where to add.
     * @param serviceType New service type.
     * @param service New service info.
     */
    public void addNewServiceDestination(String agentName,String serviceType,ServiceData service);
    
    /**
     * Removes agent from the balance pool.
     * 
     * @param agentName What to remove.
     */
    public void removeAgentDestination(String agentName);
    
    /**
     * Removes service from given agent balance pool.
     * 
     * @param agent From where to remove.
     * @param serviceType From what type to remove.
     * @param service What to remove.
     */
    public void removeServiceDestination(String agent,String serviceType,ServiceData service);
}

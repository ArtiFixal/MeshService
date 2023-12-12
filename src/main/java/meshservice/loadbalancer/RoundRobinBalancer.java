package meshservice.loadbalancer;

import java.util.ArrayList;
import java.util.HashMap;
import meshservice.AgentServicesInfo;
import meshservice.communication.Hostport;
import meshservice.services.ServiceData;
import meshservice.services.manager.ServiceNotFoundException;

/**
 * Load ballancer which distributes traffic evenly and cyclically.
 * 
 * @author ArtiFixal
 */
public class RoundRobinBalancer implements LoadBalancer{
    
    /**
     * Iterates over agents.
     */
    private final AgentIterator agentIterator;
    
    /**
     * Stores agent balance info.
     */
    private HashMap<String,ServiceBalancerInfo> balanceInfo;

    public RoundRobinBalancer(HashMap<String,AgentServicesInfo> runningAgents){
        balanceInfo=new HashMap<>();
        ArrayList<String> agentData=new ArrayList<>();
        agentIterator=new AgentIterator(agentData);
        runningAgents.forEach((agent,agentInfo)->{
            agentData.add(agent);
            balanceInfo.put(agent,new ServiceBalancerInfo(agentInfo));
        });
        
    }

    @Override
    public Hostport balance(String serviceType) throws ServiceNotFoundException{
        return balanceInfo.get(agentIterator.getNextAgent())
                .getNextService(serviceType);
    }
    
    @Override
    public void addNewAgentDestination(String agentName,AgentServicesInfo agent){
        balanceInfo.put(agentName,new ServiceBalancerInfo(agent));
        agentIterator.addNewAgent(agentName);
    }

    @Override
    public void addNewServiceDestination(String agentName,String serviceType,
            ServiceData service)
    {
        ArrayList<ServiceData> data=new ArrayList<>();
        data.add(service);
        balanceInfo.get(agentName).getServices().putIfAbsent(serviceType,
                new BalancerInfoIterator(data));
    }

    @Override
    public void removeAgentDestination(String agentName){
        agentIterator.removeAgent(agentName);
        balanceInfo.remove(agentName);
    }

    @Override
    public void removeServiceDestination(String agent,String serviceType,
            ServiceData service)
    {
        balanceInfo.get(agent).getServices().get(serviceType)
                .removeService(service);
    }
    
    /**
     * Stores info about single agent services.
     */
    private class ServiceBalancerInfo{
        /**
         * Stores info about agent.
         */
        private final AgentServicesInfo agentInfo;
        
        /**
         * Used to keep track of which services of specific type will be next.
         */
        private HashMap<String,BalancerInfoIterator> services;

        public ServiceBalancerInfo(AgentServicesInfo agentInfo){
            this.agentInfo=agentInfo;
            services=new HashMap<>();
            // Fill balance info iterator with data about services
            agentInfo.getRunningServices().forEach((serviceType,service)->{
                ArrayList<ServiceData> servicesData=new ArrayList<>();
                service.values().forEach((data)->{
                    servicesData.add(data);
                });
                services.put(serviceType,
                        new BalancerInfoIterator(servicesData));
            });
        }
        
        public Hostport getNextService(String serviceType) throws ServiceNotFoundException{
            int port=services.get(serviceType).getNextService().getPort();
            return new Hostport(agentInfo.getHost(),port);
        }
        
        public HashMap<String,BalancerInfoIterator> getServices(){
            return services;
        }
    }
    
    /**
     * Iterator over agent services.
     */
    private class BalancerInfoIterator{
        private final ArrayList<ServiceData> data;
        private int lastPosition;

        public BalancerInfoIterator(ArrayList<ServiceData> data){
            this.data=data;
            this.lastPosition=0;
        }
        
        public void addNewService(ServiceData newServiceData)
        {
            data.add(newServiceData);
        }
        
        public ServiceData getNextService() throws ServiceNotFoundException
        {
            if(data.isEmpty())
                throw new ServiceNotFoundException("There is no services");
            if(lastPosition<data.size())
                lastPosition=0;
            ServiceData curr=data.get(lastPosition);
            lastPosition++;
            return curr;
        }
        
        public void removeService(ServiceData toRemove)
        {
            data.remove(toRemove);
        }
    }

    private class AgentIterator{
        private final ArrayList<String> data;
        private int lastPosition;

        public AgentIterator(ArrayList<String> data){
            this.data=data;
            lastPosition=0;
        }
        
        public void addNewAgent(String agentName){
            data.add(agentName);
        }
        
        public String getNextAgent() throws ServiceNotFoundException
        {
            if(data.isEmpty())
                throw new ServiceNotFoundException("There is no agents");
            if(lastPosition<data.size())
                lastPosition=0;
            String curr=data.get(lastPosition);
            lastPosition++;
            return curr;
        }
        
        public void removeAgent(String agentName){
            data.remove(agentName);
        }
    }
}

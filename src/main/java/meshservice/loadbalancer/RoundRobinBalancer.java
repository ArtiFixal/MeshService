package meshservice.loadbalancer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import meshservice.AgentServicesInfo;
import meshservice.communication.AgentHostport;
import meshservice.communication.ServiceHostport;
import meshservice.services.ServiceData;
import meshservice.services.manager.ServiceNotFoundException;

/**
 * Load balancer which distributes traffic evenly and cyclically.
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

    public RoundRobinBalancer(ConcurrentHashMap<String,AgentServicesInfo> runningAgents){
        balanceInfo=new HashMap<>();
        ArrayList<String> agentData=new ArrayList<>();
        agentIterator=new AgentIterator(agentData);
        runningAgents.forEach((agent,agentInfo)->{
            agentData.add(agent);
            balanceInfo.put(agent,new ServiceBalancerInfo(agentInfo));
        });
        
    }

    @Override
    public ServiceHostport balanceService(String serviceType) throws ServiceNotFoundException{
        return balanceInfo.get(lookForAgentAbleToRun(serviceType))
                .getNextService(serviceType);
    }
    
    /**
     * Searches for agent able to run given service type.
     * 
     * @param serviceType What to test for.
     * 
     * @return Agent name.
     * 
     * @throws ServiceNotFoundException If there is no agent able to run given 
     * service.
     */
    protected String lookForAgentAbleToRun(String serviceType) throws ServiceNotFoundException
    {
        int retries=agentIterator.getAgentNames().size();
        while(retries!=0)
        {
            String agentName=agentIterator.getNextAgent();
            if(balanceInfo.get(agentName).getAgentInfo().getAvailableServices()
                .contains(serviceType))
                return agentName;
            retries--;
        }
        throw new ServiceNotFoundException("There is no agent able to run given service: "+serviceType);
    }
    
    @Override
    public AgentHostport balanceAgent(String serviceType) throws ServiceNotFoundException
    {
        final String agentName=lookForAgentAbleToRun(serviceType);
        final AgentServicesInfo agentInfo=balanceInfo
            .get(agentName).getAgentInfo();
        return new AgentHostport(agentName,agentInfo.getHost(),agentInfo.getPort());
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
        
        public ServiceHostport getNextService(String serviceType) throws ServiceNotFoundException{
            if(!services.containsKey(serviceType))
                throw new ServiceNotFoundException(serviceType);
            ServiceData data=services.get(serviceType).getNextService();
            return new ServiceHostport(data.getServiceRequestRequiredFields(),
                data.getServiceAdditionalResponseFields(),
                agentInfo.getHost(),data.getPort());
        }
        
        public HashMap<String,BalancerInfoIterator> getServices(){
            return services;
        }
        
        public AgentServicesInfo getAgentInfo(){
            return agentInfo;
        }
    }
    
    /**
     * Iterator over agent services.
     */
    private class BalancerInfoIterator{
        /**
         * Stores info about agent service type.
         */
        private final ArrayList<ServiceData> data;
        
        /**
         * Last used service index.
         */
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
        /**
         * Stores available agent names.
         */
        private final ArrayList<String> agentNames;
        
        /**
         * Last used agent index.
         */
        private int lastPosition;

        public AgentIterator(ArrayList<String> agentNames){
            this.agentNames=agentNames;
            lastPosition=0;
        }
        
        public void addNewAgent(String agentName){
            agentNames.add(agentName);
        }
        
        public String getNextAgent() throws ServiceNotFoundException
        {
            if(agentNames.isEmpty())
                throw new ServiceNotFoundException("There is no agents");
            if(lastPosition<agentNames.size())
                lastPosition=0;
            String curr=agentNames.get(lastPosition);
            lastPosition++;
            return curr;
        }
        
        protected ArrayList<String> getAgentNames(){
            return agentNames;
        }
        
        public void removeAgent(String agentName){
            agentNames.remove(agentName);
        }
    }
}

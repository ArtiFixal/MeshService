package meshservice.services.manager;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import meshservice.AgentServicesInfo;
import meshservice.ServiceStatus;
import meshservice.communication.Hostport;
import meshservice.communication.JsonBuilder;
import meshservice.communication.JsonReader;
import meshservice.communication.RequestException;
import meshservice.loadbalancer.LoadBalancer;
import meshservice.loadbalancer.RoundRobinBalancer;
import meshservice.services.Service;
import meshservice.services.ServiceData;

/**
 * Class responsible for managing {@code Services} lifespan.
 *
 * @author ArtiFixal
 */
public class ServiceManager extends Service{

    /**
     * Stores running agents, where: <br>
     * Key - agent name <br>
     * Value - agent info
     */
    private HashMap<String,AgentServicesInfo> runningAgents;
    
    /**
     * Distributes traffic across service instances.
     */
    private LoadBalancer loadBalancer;
    
    /**
     * Current processed client socket.
     */
    private Socket currentClient;

    /**
     * Max service inactivity time in miliseconds.
     */
    private static final long MAX_INACTIVITY=120000;

    public ServiceManager() throws IOException{
        this(0);
    }

    public ServiceManager(int port) throws IOException{
        super(port);
        runningAgents=new HashMap<>();
        loadBalancer=new RoundRobinBalancer(runningAgents);
    }

    @Override
    protected Socket prepareSocket() throws IOException{
        currentClient=super.prepareSocket();
        return currentClient;
    }

    /**
     * Increases inactivity timers of running services
     *
     * @param value
     */
    public void processInactivityTimers(long value)
    {
        runningAgents.forEach((agentKey,agent)->{
            // Iterate over
            agent.getRunningServices().forEach((serviceKey,serviceArray)->{
                serviceArray.forEach((serviceUUID,service)->{
                    if(service.getStatus().equals(ServiceStatus.RUNNING))
                        service.increaseInactiveTimer(value);
                    else if(service.getStatus().equals(ServiceStatus.CLOSED))
                        runningAgents.get(agentKey).getRunningServices().remove(serviceKey);
                    if(service.getInactiveTimer()>=MAX_INACTIVITY)
                    {
                        final JsonBuilder closeRequest=new JsonBuilder("closeService")
                                .addField("service",service.getServiceType())
                                .addField("type","request");
                        try{
                            communicateWithServiceAgent(agentKey,closeRequest);
                            loadBalancer.removeServiceDestination(agentKey,serviceKey,service);
                        }catch(Exception e){
                            System.out.println(e);
                            e.printStackTrace();
                        }
                        System.out.println("[Info]: Closed service: "+serviceKey);
                    }
                });
            });
        });
    }

    /**
     * @return Port on which new service will listen for requests. If 0 let OS
     * decide which one to assign.
     */
    protected int assignPort(){
        return 0;
    }

    /**
     * Registers new service.
     * 
     * @param agentName Agent responsible for new service.
     * @param serviceUUID New service UUID.
     * @param serviceType New service type.
     * @param servicePort New service port.
     */
    protected void registerService(String agentName,String serviceUUID,String serviceType,int servicePort)
    {
        ServiceData newService=new ServiceData(serviceType,servicePort);
        runningAgents.get(agentName).getRunningServices().get(serviceType).put(serviceUUID,newService);
        loadBalancer.addNewServiceDestination(agentName,serviceType,newService);
        System.out.println("[Info]: New service registered: "+serviceType+" in agent:"+agentName);
    }

    @Override
    public void processRequest(BufferedInputStream request,JsonBuilder response)
            throws IOException,RequestException{
        long start=System.currentTimeMillis();
        final JsonReader reader=new JsonReader(request);
        System.out.println(reader.getRequestNode().toPrettyString());
        String type=reader.readString("type").toLowerCase();
        String action=reader.readString("action").toLowerCase();
        String agentName=reader.readString("agent");
        switch(type){
            case "request" -> {
                switch(action){
                    case "registeragent" -> {
                        String serviceUUID=reader.readString("serviceID");
                        AgentServicesInfo agent=new AgentServicesInfo(serviceUUID,
                                currentClient.getInetAddress().getHostName(),
                                currentClient.getPort(),
                                reader.readArrayOf("availableServices"));
                        runningAgents.put(agentName,agent);
                        loadBalancer.addNewAgentDestination(agentName,agent);
                        System.out.println("[Info]: New agent registered: "+agentName);
                    }
                    case "servicestatuschange" -> {
                        String serviceUUID=reader.readString("serviceID");
                        String serviceType=reader.readString("service");
                        int newStatus=reader.readNumber("newStatus",Integer.class);
                        runningAgents.get(agentName).setServiceStatus(serviceType,serviceUUID,
                                ServiceStatus.interperFromNumber(newStatus));
                    }
                    case "renewtimer" -> {
                        String serviceUUID=reader.readString("serviceID");
                        String serviceType=reader.readString("service");
                        runningAgents.get(agentName).getRunningServices()
                                .get(serviceType).get(serviceUUID).setInactiveTimer(0);
                        start=System.currentTimeMillis();
                    }
                    case "askforservice" -> {
                        String serviceType=reader.readString("service");
                        processServiceAsk(serviceType,response);
                    }
                    default ->
                        throw new RequestException("Unknown request action");
                }
            }
            default ->
                throw new RequestException("Unknown request type");
        }
        response.addField("type","response");
        response.setStatus(200);
        long passed=start-System.currentTimeMillis();
        processInactivityTimers(passed);
    }

    /**
     * Searches for agent responsible for given service type
     * 
     * @param serviceType What service does.
     * 
     * @return Found agent.
     * 
     * @throws ServiceNotFoundException 
     */
    protected String findAgentResponsibleForService(String serviceType) throws ServiceNotFoundException
    {
        for(Map.Entry<String,AgentServicesInfo> agentEntry:runningAgents.entrySet()){
            if(agentEntry.getValue().getAvailableServices().contains(serviceType)){
                return agentEntry.getKey();
            }
        }
        throw new ServiceNotFoundException(serviceType);
    }

    /**
     * Sends request to the given service agent.
     * 
     * @param agentName Where to send request.
     * @param request What to send.
     * 
     * @return Service agent response.
     * 
     * @throws IOException If any socket error occures.
     * @throws RequestException If request was malformed.
     */
    protected JsonReader communicateWithServiceAgent(String agentName,JsonBuilder request) throws IOException,RequestException
    {
        AgentServicesInfo agentInfo=runningAgents.get(agentName);
        return communicateWithHost(agentInfo.getHost(),agentInfo.getPort(),request);
    }
    
    /**
     * Sends request to given service agent to start given service.
     * 
     * @param agentName Where to start.
     * @param serviceType What to start.
     * 
     * @return Agent response.
     * 
     * @throws IOException If any socket error occures.
     * @throws RequestException If request was malformed.
     */
    protected JsonReader sendServiceStartRequest(String agentName,String serviceType) throws IOException,RequestException
    {
        final JsonBuilder request=new JsonBuilder("run")
                .addField("type","request")
                .addField("port",assignPort())
                .addField("service",serviceType);
        return communicateWithServiceAgent(agentName,request);
    }

    /**
     * Processes API Gateway agent request for service hostport.
     * 
     * @param serviceType What type service to send.
     * @param response Response to agent.
     * 
     * @throws RequestException If request was malformed.
     * @throws IOException If any socket error occurred.
     */
    private void processServiceAsk(String serviceType,JsonBuilder response) throws RequestException,IOException
    {
        Hostport askedFor;
        try{
            askedFor=loadBalancer.balance(serviceType);
        }catch(ServiceNotFoundException e){
            try{
                String agentKey=findAgentResponsibleForService(serviceType);
                JsonReader agentResponse=sendServiceStartRequest(agentKey,serviceType);
                askedFor=new Hostport(runningAgents.get(agentKey).getHost(),
                        agentResponse.readNumber("port",Integer.class));
                String serviceUUID=agentResponse.readString("serviceID");
                registerService(agentKey,serviceUUID,serviceType,askedFor.getPort());
            }catch(ServiceNotFoundException ex){
                throw new RequestException("Service not found");
            }
        }
        response.addField("host",askedFor.getHost())
                .addField("port",askedFor.getPort());
    }

    protected class SearchedService{

        private String agentHost;
        private ServiceData data;

        public SearchedService(String agentHost,ServiceData data){
            this.agentHost=agentHost;
            this.data=data;
        }

        public String getAgentHost(){
            return agentHost;
        }

        public ServiceData getData(){
            return data;
        }
    }

    public static void main(String[] args){
        try{
            ServiceManager m=new ServiceManager(9000);
            m.join();
        }catch(Exception e){
            System.out.println(e);
            e.printStackTrace();
        }
    }
}

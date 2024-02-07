package meshservice.services.manager;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import meshservice.AgentServicesInfo;
import meshservice.ServiceStatus;
import meshservice.communication.AgentHostport;
import meshservice.communication.Hostport;
import meshservice.communication.JsonBuilder;
import meshservice.communication.JsonReader;
import meshservice.communication.RequestException;
import meshservice.communication.ServiceHostport;
import meshservice.loadbalancer.LoadBalancer;
import meshservice.loadbalancer.RoundRobinBalancer;
import meshservice.services.Service;
import meshservice.services.ServiceData;
import meshservice.services.ServiceInvoke;
import meshservice.services.ServiceTraffic;

/**
 * Class responsible for managing {@code Services} lifespan.
 *
 * @author ArtiFixal
 */
public class ServiceManager extends Service{
    public static final String[] REQUEST_REQUIRED_FIELDS=new String[]{"action","agentName"};

    /**
     * Max service inactivity time in miliseconds.
     */
    private static final long MAX_INACTIVITY=2000;
    
    /**
     * Request per second ratio over/at which new service instance will be created.
     */
    private static final float SERVICE_INVOKE_RATIO=1.2f;
    
    /**
     * Stores running agents and their services.
     */
    private final RunningAgentsContainer agentContainer;
    
    /**
     * Stores info about traffic to the service types.
     */
    private final HashMap<String,ServiceTraffic> serviceTypeTraffic;
    
    /**
     * Distributes traffic across service instances.
     */
    private LoadBalancer loadBalancer;
    
    /**
     * Current processed client socket.
     */
    private Socket currentClient;
    
    /**
     * Thread managing services lifespan.
     */
    private TimerThread timerThread;

    public ServiceManager() throws IOException{
        this(0);
    }

    public ServiceManager(int port) throws IOException{
        super(port);
        agentContainer=new RunningAgentsContainer();
        loadBalancer=new RoundRobinBalancer(agentContainer.getRunningAgents());
        serviceTypeTraffic=new HashMap<>();
        timerThread=new TimerThread();
    }

    @Override
    protected Socket prepareSocket() throws IOException{
        currentClient=super.prepareSocket();
        return currentClient;
    }

    /**
     * Increases inactivity timers of running services
     *
     * @param value How many milliseconds passed.
     */
    public void processInactivityTimers(long value)
    {
        agentContainer.getRunningAgents().forEach((agentName,agent)->{
            // Iterate over
            agent.getRunningServices().forEach((serviceType,serviceArray)->{
                serviceArray.forEach((serviceUUID,service)->{
                    if(service.getStatus().equals(ServiceStatus.RUNNING)){
                        service.increaseInactiveTimer(value);
                    }else if(service.getStatus().equals(ServiceStatus.CLOSED)){
                        agentContainer.removeService(agentName,serviceType,serviceUUID);
                    }
                    if(service.getInactiveTimer()>=MAX_INACTIVITY){
                        final JsonBuilder closeRequest=new JsonBuilder("closeService")
                                .addField("type","request")
                                .addField("serviceID",serviceUUID)
                                .addField("service",serviceType);
                        try{
                            communicateWithServiceAgent(agentName,closeRequest);
                            loadBalancer.removeServiceDestination(agentName,serviceType,service);
                            // Lower timer to avoid duplicated close requests
                            service.setInactiveTimer((service.getInactiveTimer()*TimerThread.SLEEP_FOR*4));
                        }catch(Exception e){
                            System.out.println(e);
                            e.printStackTrace();
                        }
                        System.out.println("[Info]: Closed service: "+serviceType);
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

    @Override
    public String[] getRequiredRequestFields(){
        return REQUEST_REQUIRED_FIELDS;
    }
    
    @Override
    public String[] getAdditionalResponseFields(){
        return EMPTY_ARRAY;
    }

    @Override
    public void processRequest(BufferedInputStream request,JsonBuilder response)
            throws IOException,RequestException{
        final JsonReader reader=new JsonReader(request);
        System.out.println("Manager request: "+reader.getRequestNode().toPrettyString());
        String action=reader.readString("action").toLowerCase();
        String agentName=reader.readString("agent");
        switch(action){
            case "registeragent" -> {
                String agentUUID=reader.readString("serviceID");
                AgentServicesInfo agent=new AgentServicesInfo(agentUUID,
                        currentClient.getInetAddress().getHostName(),
                        reader.readNumberPositive("port",Integer.class),
                        reader.readArrayOf("availableServices"));
                agentContainer.registerAgent(agentName,agent);
                System.out.println("[Info]: New agent registered: "+agentName);
            }
            case "servicestatuschange" -> {
                String serviceUUID=reader.readString("serviceID");
                String serviceType=reader.readString("service").toLowerCase();
                int newStatusCode=reader.readNumber("newStatus",Integer.class);
                ServiceStatus newStatus=ServiceStatus.interperFromNumber(newStatusCode);
                agentContainer.changeServiceStatus(agentName,serviceType,
                        serviceUUID,newStatus);
            }
            case "renewtimer" -> {
                String serviceUUID=reader.readString("serviceID");
                String serviceType=reader.readString("service").toLowerCase();
                agentContainer.renewServiceTimer(agentName,serviceType,serviceUUID);
            }
            case "askforservice" -> {
                String serviceType=reader.readString("service").toLowerCase();
                processServiceAsk(serviceType,response);
            }
            default ->
                throw new RequestException("Unknown request action");
        }
        response.addField("type","response");
        response.setStatus(200);
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
        AgentServicesInfo agentInfo=agentContainer.getAgentInfo(agentName);
        return communicateWithHost(agentInfo.getHost(),agentInfo.getPort(),request);
    }
    
    private JsonBuilder createServiceStartRequest(String serviceType){
        return new JsonBuilder("run")
            .addField("type","request")
            .addField("port",assignPort())
            .addField("service",serviceType);
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
        final JsonBuilder request=createServiceStartRequest(serviceType);
        return communicateWithServiceAgent(agentName,request);
    }
    
    /**
     * Sends request to given service agent to start given service.
     * 
     * @param agentDestination Where to start.
     * @param serviceType What to start.
     * 
     * @return Agent response.
     * 
     * @throws IOException If any socket error occures.
     * @throws RequestException If request was malformed.
     */
    protected JsonReader sendServiceStartRequest(Hostport agentDestination,String serviceType) throws IOException,RequestException
    {
        final JsonBuilder request=createServiceStartRequest(serviceType);
        return communicateWithHost(agentDestination.getHost(),agentDestination.getPort(),request);
    }
    
    /**
     * Registers just started service to the manager.
     * 
     * @param agentName Owner of the service.
     * @param serviceType Type of service.
     * @param agentResponse Response from agent.
     * 
     * @throws RequestException If request was malformed.
     */
    private void registerStartedService(String agentName,String serviceType,JsonReader agentResponse) throws RequestException
    {
        int servicePort=agentResponse.readNumber("port",Integer.class);
        String serviceUUID=agentResponse.readString("serviceID");
        String[] requestRequiredFields=agentResponse.readArrayOf("requiredFields")
            .toArray(String[]::new);
        String[] additionalFields=agentResponse.readArrayOf("additionalFields")
            .toArray(String[]::new);
        agentContainer.registerService(agentName,serviceUUID,serviceType,servicePort,requestRequiredFields,additionalFields);
    }

    /**
     * Requests best {@code Agent} candidate to start given service type.
     * 
     * @param serviceType What to start.
     * 
     * @return Agent response.
     * 
     * @throws IOException If any socket error occures.
     * @throws RequestException If request was malformed.
     */
    protected JsonReader requestServiceStart(String serviceType) throws IOException,RequestException
    {
        try{
            AgentHostport agentDestination=loadBalancer.balanceAgent(serviceType);
            JsonReader agentResponse=sendServiceStartRequest(agentDestination,serviceType);
            registerStartedService(agentDestination.getAgentName(),serviceType,agentResponse);
            return agentResponse;
        }catch(ServiceNotFoundException e){
            throw new RequestException("Service not found");
        }
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
        synchronized(loadBalancer){
            if(serviceTypeTraffic.containsKey(serviceType))
                serviceTypeTraffic.get(serviceType).increaseCurrentRPS();
            else
            {
                ServiceInvoke invokeCallback=()->{
                    AgentHostport agentDestination=loadBalancer.balanceAgent(serviceType);
                    JsonReader agentResponse=sendServiceStartRequest(agentDestination,serviceType);
                    registerStartedService(agentDestination.getAgentName(),serviceType,agentResponse);
                };
                serviceTypeTraffic.put(serviceType,new ServiceTraffic(SERVICE_INVOKE_RATIO,invokeCallback));
            }
        }
        ServiceHostport askedFor;
        try{
            askedFor=loadBalancer.balanceService(serviceType);
            // renew timer
            System.out.println("[Info]: Reused service: "+serviceType+" at: "+askedFor);
        }catch(ServiceNotFoundException e){
            JsonReader agentResponse=requestServiceStart(serviceType);
            String[] requiredFields=agentResponse.readArrayOf("requiredFields")
                    .toArray(String[]::new);
            String[] additionalFields=agentResponse.readArrayOf("additionalFields")
                    .toArray(String[]::new);
            askedFor=new ServiceHostport(requiredFields,additionalFields,
                agentResponse.readString("host"),
                agentResponse.readNumber("port",Integer.class));
            System.out.printf("[Info]: Started new service: %s at: %s\n",serviceType,askedFor);
        }
        response.addField("host",askedFor.getHost())
                .addField("port",askedFor.getPort())
                .addArray("requiredFields",askedFor.getRequestRequiredFields())
                .addArray("additionalFields",askedFor.getAdditionalResponseFields());
    }

    @Override
    public void closeService() throws IOException{
        timerThread.stopTimer();
        super.closeService();
    }
    
    /**
     * Manages services lifespan.
     */
    private class TimerThread extends Thread{

        private static final int SLEEP_FOR=100;
        private boolean isAlive;
        private int toSecond;
        
        public TimerThread(){
            isAlive=true;
            start();
        }
        
        @Override
        public void run(){
            while(isAlive)
            {
                try{
                    long start=System.currentTimeMillis();
                    sleep(SLEEP_FOR);
                    processInactivityTimers(SLEEP_FOR);
                    long stop=System.currentTimeMillis();
                    toSecond+=stop-start;
                    if(toSecond>=1000)
                    {
                        serviceTypeTraffic.forEach((serviceType,trafficInfo)->{
                            try{
                                trafficInfo.secondPassed();
                            }catch(Exception e){
                                System.out.println(e);
                            }
                        });
                        toSecond=0;
                    }
                }catch(Exception e){
                    System.out.println(e);
                }
            }
        }
        
        public void stopTimer(){
            isAlive=false;
        }   
    }
    
    /**
     * Stores running agents and synchronizes operation on them.
     */
    private class RunningAgentsContainer{
        /**
         * Stores running agents, where: <br>
         * Key - agent name <br>
         * Value - agent info
         */
        private final ConcurrentHashMap<String,AgentServicesInfo> runningAgents;

        public RunningAgentsContainer(){
            this.runningAgents=new ConcurrentHashMap<>();
        }

        public ConcurrentHashMap<String,AgentServicesInfo> getRunningAgents(){
            return runningAgents;
        }
        
        /**
         * @param agentName Which agent info to get.
         * 
         * @return Given agent info.
         */
        public AgentServicesInfo getAgentInfo(String agentName)
        {
            return runningAgents.get(agentName);
        }
        
        /**
         * Removes service from given agent.
         * 
         * @param agentName From where to remove.
         * @param serviceUUID What to remove
         */
        public void removeService(String agentName,
            String serviceType,String serviceUUID)
        {
            runningAgents.get(agentName).getRunningServices()
                .get(serviceType).remove(serviceUUID);
        }
        
        /**
         * Register new agent in the manager and adds it to the load balancer.
         * 
         * @param agentName New agent name.
         * @param agent New agent data.
         */
        public void registerAgent(String agentName,AgentServicesInfo agent){
            runningAgents.put(agentName,agent);
            synchronized(loadBalancer){
                loadBalancer.addNewAgentDestination(agentName,agent);
            }
        }
        
        /**
         * Registers new service in the manager and adds it to the load balancer.
         *
         * @param agentName Agent responsible for new service.
         * @param serviceUUID New service UUID.
         * @param serviceType New service type.
         * @param servicePort New service port.
         */
        public void registerService(String agentName,String serviceUUID,String serviceType,int servicePort,String[] requiredRequestFields,String[] additionalFields)
        {
            ServiceData newService=new ServiceData(serviceType,servicePort,ServiceStatus.RUNNING,requiredRequestFields,additionalFields);
            runningAgents.get(agentName).addNewService(serviceType,serviceUUID,newService);
            synchronized(loadBalancer){
                loadBalancer.addNewServiceDestination(agentName,serviceType,newService);
            }
            System.out.println("[Info]: New service registered: "+serviceType+" in agent: "+agentName);
        }
        
        /**
         * Updates given service status.
         * 
         * @param agentName Where service belongs.
         * @param serviceType What type it is.
         * @param serviceUUID Which service it is.
         * @param newStatus To what update.
         */
        public void changeServiceStatus(String agentName,
            String serviceType,String serviceUUID,ServiceStatus newStatus)
        {
            if(runningAgents.get(agentName).getRunningServices()
                    .get(serviceType).containsKey(serviceUUID))
            {
                runningAgents.get(agentName).setServiceStatus(serviceType,
                    serviceUUID,newStatus);
                System.out.printf("[Info]: Changed service: %s (%s) status to: %s\n",
                            serviceUUID,serviceType,newStatus);
            }
        }
        
        /**
         * Resets given service inactivity timer.
         * 
         * @param agentName Where service belongs.
         * @param serviceType What type it is.
         * @param serviceUUID Which service it is.
         */
        public void renewServiceTimer(String agentName,
            String serviceType,String serviceUUID)
        {
            runningAgents.get(agentName).getRunningServices()
                .get(serviceType).get(serviceUUID).setInactiveTimer(0);
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

package meshservice.services.manager;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
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
    private static final long MAX_INACTIVITY=120000;
    
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
        synchronized(agentContainer.getRunningAgents()){
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
                                .addField("service",service.getServiceType())
                                .addField("type","request");
                            try{
                                communicateWithServiceAgent(agentName,closeRequest);
                                loadBalancer.removeServiceDestination(agentName,serviceType,service);
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
    public void processRequest(BufferedInputStream request,JsonBuilder response)
            throws IOException,RequestException{
        final JsonReader reader=new JsonReader(request);
        System.out.println(reader.getRequestNode().toPrettyString());
        String action=reader.readString("action").toLowerCase();
        String agentName=reader.readString("agent");
        switch(action){
            case "registeragent" -> {
                String agentUUID=reader.readString("serviceID");
                AgentServicesInfo agent=new AgentServicesInfo(agentUUID,
                        currentClient.getInetAddress().getHostName(),
                        currentClient.getPort(),
                        reader.readArrayOf("availableServices"));
                agentContainer.registerAgent(agentName,agent);
                System.out.println("[Info]: New agent registered: "+agentName);
            }
            case "servicestatuschange" -> {
                String serviceUUID=reader.readString("serviceID");
                String serviceType=reader.readString("service");
                int newStatus=reader.readNumber("newStatus",Integer.class);
                agentContainer.changeServiceStatus(agentName,serviceType,
                        serviceUUID,ServiceStatus.interperFromNumber(newStatus));
            }
            case "renewtimer" -> {
                String serviceUUID=reader.readString("serviceID");
                String serviceType=reader.readString("service");
                agentContainer.renewServiceTimer(agentName,serviceType,serviceUUID);
            }
            case "askforservice" -> {
                String serviceType=reader.readString("service");
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
     * Requests best {@code Agent} candidate to start given service type.
     * 
     * @param serviceType What to start.
     * 
     * @return Agent response.
     * 
     * @throws IOException If any socket error occures.
     * @throws RequestException If request was malformed.
     */
    protected JsonReader requestServiceStart(String serviceType) throws IOException,RequestException{
        try{
            AgentHostport agentDestination=loadBalancer.balanceAgent(serviceType);
            JsonReader agentResponse=sendServiceStartRequest(agentDestination,serviceType);
            Hostport askedFor=new Hostport(agentContainer.getAgentInfo(agentDestination.getAgentName()).getHost(),
                agentResponse.readNumber("port",Integer.class));
            String serviceUUID=agentResponse.readString("serviceID");
            agentContainer.registerService(agentDestination.getAgentName(),serviceUUID,serviceType,askedFor.getPort());
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
        synchronized(agentContainer){
            if(serviceTypeTraffic.containsKey(serviceType))
                serviceTypeTraffic.get(serviceType).increaseCurrentRPS();
            else
            {
                ServiceInvoke invokeCallback=()->{
                    AgentHostport agentDestination=loadBalancer.balanceAgent(serviceType);
                    JsonReader agentResponse=sendServiceStartRequest(agentDestination,serviceType);
                    String serviceUUID=agentResponse.readString("serviceID");
                    int servicePort=agentResponse.readNumber("port",Integer.class);
                    agentContainer.registerService(agentDestination.getAgentName(),serviceUUID,serviceType,servicePort);
                };
                serviceTypeTraffic.put(serviceType,new ServiceTraffic(SERVICE_INVOKE_RATIO,invokeCallback));
            }
        }
        ServiceHostport askedFor;
        try{
            askedFor=loadBalancer.balanceService(serviceType);
        }catch(ServiceNotFoundException e){
            JsonReader agentResponse=requestServiceStart(serviceType);
            String[] requiredFields=agentResponse.readArrayOf("requiredFields")
                .toArray(String[]::new);
            askedFor=new ServiceHostport(requiredFields,
                agentResponse.readString("host"),
                agentResponse.readNumber("port",Integer.class));
        }
        response.addField("host",askedFor.getHost())
                .addField("port",askedFor.getPort())
                .addArray("requiredFields",askedFor.getRequestRequiredFields());
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
                    sleep(SLEEP_FOR);
                    long start=System.currentTimeMillis();
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
        private final HashMap<String,AgentServicesInfo> runningAgents;

        public RunningAgentsContainer(){
            this.runningAgents=new HashMap<>();
        }

        public HashMap<String,AgentServicesInfo> getRunningAgents(){
            return runningAgents;
        }
        
        /**
         * @param agentName Which agent info to get.
         * 
         * @return Given agent info.
         */
        public synchronized AgentServicesInfo getAgentInfo(String agentName)
        {
            return runningAgents.get(agentName);
        }
        
        /**
         * Removes service from given agent.
         * 
         * @param agentName From where to remove.
         * @param serviceUUID What to remove
         */
        public synchronized void removeService(String agentName,
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
        public synchronized void registerAgent(String agentName,AgentServicesInfo agent){
            runningAgents.put(agentName,agent);
            loadBalancer.addNewAgentDestination(agentName,agent);
        }
        
        /**
         * Registers new service in the manager and adds it to the load balancer.
         *
         * @param agentName Agent responsible for new service.
         * @param serviceUUID New service UUID.
         * @param serviceType New service type.
         * @param servicePort New service port.
         */
        public void registerService(String agentName,String serviceUUID,String serviceType,int servicePort)
        {
            ServiceData newService=new ServiceData(serviceType,servicePort);
            synchronized(runningAgents){
                runningAgents.get(agentName).getRunningServices().get(serviceType).put(serviceUUID,newService);
                loadBalancer.addNewServiceDestination(agentName,serviceType,newService);
            }
            System.out.println("[Info]: New service registered: "+serviceType+" in agent:"+agentName);
        }
        
        /**
         * Updates given service status.
         * 
         * @param agentName Where service belongs.
         * @param serviceType What type it is.
         * @param serviceUUID Which service it is.
         * @param newStatus To what update.
         */
        public synchronized void changeServiceStatus(String agentName,
            String serviceType,String serviceUUID,ServiceStatus newStatus)
        {
            runningAgents.get(agentName).setServiceStatus(serviceType,
                serviceUUID,newStatus);
        }
        
        /**
         * Resets given service inactivity timer.
         * 
         * @param agentName Where service belongs.
         * @param serviceType What type it is.
         * @param serviceUUID Which service it is.
         */
        public synchronized void renewServiceTimer(String agentName,
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

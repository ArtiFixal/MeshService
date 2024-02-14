package meshservice.services.manager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import meshservice.AgentServicesInfo;
import meshservice.ServiceStatus;
import meshservice.communication.AgentHostport;
import meshservice.communication.Connection;
import meshservice.communication.ConnectionThread;
import meshservice.communication.JsonBuilder;
import meshservice.communication.JsonReader;
import meshservice.communication.RequestException;
import meshservice.communication.ServiceHostport;
import meshservice.loadbalancer.LoadBalancer;
import meshservice.loadbalancer.RoundRobinBalancer;
import meshservice.services.ControlPlaneService;
import meshservice.services.ServiceData;
import meshservice.services.ServiceInvoke;
import meshservice.services.ServiceTraffic;

/**
 * Class responsible for managing {@code Services} lifespan.
 *
 * @author ArtiFixal
 */
public class ServiceManager extends ControlPlaneService{
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
     * Stores service mesh active connections.
     */
    private ActiveConnectionContainer activeConnections;
    
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
        activeConnections=new ActiveConnectionContainer();
        loadBalancer=new RoundRobinBalancer(agentContainer.getRunningAgents());
        serviceTypeTraffic=new HashMap<>();
        timerThread=new TimerThread();
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
                        if(service.getInactiveTimer()>=MAX_INACTIVITY){
                            final JsonBuilder closeRequest=new JsonBuilder("closeService")
                                    .addField("type","request")
                                    .addField("serviceID",serviceUUID)
                                    .addField("service",serviceType);
                            try{
                                // Lower timer to avoid duplicated close requests
                                service.setInactiveTimer((service.getInactiveTimer()-TimerThread.SLEEP_FOR*4));
                                communicateWithServiceAgent(getOrConnect(agentName),closeRequest);
                                synchronized(loadBalancer){
                                    loadBalancer.removeServiceDestination(agentName,serviceType,service);
                                }
                                
                            }catch(Exception e){
                                System.out.println(e);
                                e.printStackTrace();
                            }
                            System.out.println("[Info]: Closed service: "+serviceType);
                        }
                    }else if(service.getStatus().equals(ServiceStatus.CLOSED)){
                        agentContainer.removeService(agentName,serviceType,serviceUUID);
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
            throws IOException,RequestException
    {
        final JsonReader reader=new JsonReader(request);
        System.out.println("Manager request: "+reader.getRequestNode().toPrettyString());
        String action=reader.readString("action").toLowerCase();
        String agentName=reader.readString("agent");
        switch(action){
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
     * @param agentConnection Where to send request.
     * @param request What to send.
     * 
     * @return Service agent response.
     * 
     * @throws IOException If any socket error occures.
     * @throws RequestException If request was malformed.
     */
    protected JsonReader communicateWithServiceAgent(ConnectionThread agentConnection,JsonBuilder request) throws IOException,RequestException
    {
        return agentConnection.sendRequest(request);
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
        AgentServicesInfo agentInfo=agentContainer.getAgentInfo(agentName);
        ConnectionThread agentConnection=getOrConnect(agentName,agentInfo.getHost(),agentInfo.getPort());
        return communicateWithServiceAgent(agentConnection,request);
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
    protected JsonReader sendServiceStartRequest(AgentHostport agentDestination,String serviceType) throws IOException,RequestException
    {
        final JsonBuilder request=createServiceStartRequest(serviceType);
        System.out.println("Manager request sent to ServiceAgent:"+request.getJson().toPrettyString());
        return communicateWithServiceAgent(
                getOrConnect(agentDestination.getAgentName(),
                agentDestination.getHost(),
                agentDestination.getPort()),request);
    }
    
    /**
     * Gets existing agent connection or reconects to it.
     * 
     * @param agentName Which to look for.
     * 
     * @return Connection thread to the given agent.
     * 
     * @throws IOException Any socket error occurred.
     * @throws RequestException If request was malformed.
     */
    private ConnectionThread getOrConnect(String agentName) throws IOException, RequestException{
        AgentServicesInfo agentInfo=agentContainer.getAgentInfo(agentName);
        return getOrConnect(agentName,agentInfo.getHost(),agentInfo.getPort());
    }
    
    /**
     * Gets existing agent connection or reconects to it.
     * 
     * @param agentName Which to look for.
     * @param host To which host connect if there is no connection.
     * @param port To which port connect if there is no connection.
     * 
     * @return Connection thread to the given agent.
     * 
     * @throws IOException Any socket error occurred.
     * @throws RequestException If request was malformed.
     */
    private ConnectionThread getOrConnect(String agentName,String host,int port) throws IOException, RequestException{
        ConnectionThread agentThread;
        if(activeConnections.getControlPlaneConnections().containsKey(agentName)){
            System.out.println("[Info]: Reused active connection to: "+agentName);
            agentThread=activeConnections.getControlPlaneConnection(agentName);
        }
        else
        {
            agentThread=reconectToAgent(agentName,host,port);
            System.out.println("[Info]: Created new connection to: "+agentName);
        }
        return agentThread;
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
    
    
    /**
     * Reconects to the given agent.
     * 
     * @param agentName Agent to which reconect.
     * 
     * @throws IOException Any socket error occurred.
     * @throws RequestException If request was malformed.
     */
    private ConnectionThread reconectToAgent(String agentName) throws IOException, RequestException{
        final JsonBuilder reconectRequest=new JsonBuilder("renewmanagerconnection")
                .addField("type","request");
        ConnectionThread oldAgentThread=activeConnections.getControlPlaneConnection(agentName);
        oldAgentThread.close();
        AgentServicesInfo agentInfo=agentContainer.getAgentInfo(agentName);
        ConnectionThread newAgentThread=new ConnectionThread(new Connection(
                new Socket(agentInfo.getHost(),agentInfo.getPort())),this);
        newAgentThread.sendRequest(reconectRequest);
        newAgentThread.start();
        activeConnections.replaceControlPlaneConnection(agentName,newAgentThread);
        return newAgentThread;
    }
    
    /**
     * Reconects to the given agent.
     * 
     * @param agentName Agent to which reconect.
     * 
     * @throws IOException Any socket error occurred.
     * @throws RequestException If request was malformed.
     */
    private ConnectionThread reconectToAgent(String agentName,String host,int port) throws IOException, RequestException{
        final JsonBuilder reconectRequest=new JsonBuilder("renewmanagerconnection")
                .addField("type","request");
        ConnectionThread oldAgentThread=activeConnections.getControlPlaneConnection(agentName);
        oldAgentThread.close();
        ConnectionThread newAgentThread=new ConnectionThread(new Connection(
                new Socket(host,port)),this);
        newAgentThread.sendRequest(reconectRequest);
        newAgentThread.start();
        activeConnections.replaceControlPlaneConnection(agentName,newAgentThread);
        return newAgentThread;
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
        private int secondsPassed;
                
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
                        secondsPassed++;
                    }
                    if(secondsPassed>=20)
                    {
                        activeConnections.getControlPlaneConnections().forEach(((agentName,agentConnectionThred)->{
                            if(!activeConnections.testAgentConnection(agentName))
                            {
                                try{
                                    reconectToAgent(agentName);
                                }catch(Exception e){
                                    System.out.println("[Error]: Manager failed to reconecto to the agent: "+agentName);
                                }
                            }
                        }));
                        activeConnections.getDataPlaneConnections().forEach((serviceID,connectionInfo)->{
                            try{
                                if(!activeConnections.testServiceConnection(serviceID))
                                    activeConnections.requestAgentToReconectService(serviceID);
                            }catch(Exception e){
                                System.out.println("[Error]: Manager failed to make agent reconect its service: "+serviceID);
                            }
                        });
                        secondsPassed=0;
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
            System.out.printf("[Info]: Registered new service: %s (%s) in agent: %s\n",serviceUUID,serviceType,agentName);
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
    
    private class ConnectionInfo{
        /**
         * Name of agent which owns this service.
         */
        String agentName;
        
        /**
         * Info about this service.
         */
        ServiceData serviceInfo;
        
        /**
         * Connection state of this service with its agent.
         */
        private boolean works;

        public ConnectionInfo(String agent,ServiceData serviceInfo){
            this.agentName=agent;
            this.serviceInfo=serviceInfo;
            works=true;
        }
        
        public String getAgentName(){
            return agentName;
        }
        
        public String getHost(){
            return agentContainer.getAgentInfo(agentName).getHost();
        }
        
        public int getPort(){
            return serviceInfo.getPort();
        }

        public boolean isWorking(){
            return works;
        }
    }
    
    private class ActiveConnectionContainer{
        /**
         * Timeout after which it is assumed connection is not valid.
         */
        public static final int DEFAULT_TIMEOUT=500;
        
        /**
         * Stores connections to the control plane services.
         */
        private final ConcurrentHashMap<String,ConnectionThread> controlPlaneConnections;
        
        /**
         * Stores connections to the data plane services.
         */
        private final ConcurrentHashMap<String,ConnectionInfo> dataPlaneConnections;

        public ActiveConnectionContainer(){
            controlPlaneConnections=new ConcurrentHashMap<>();
            dataPlaneConnections=new ConcurrentHashMap<>();
        }

        public ConcurrentHashMap<String,ConnectionThread> getControlPlaneConnections(){
            return controlPlaneConnections;
        }

        public ConcurrentHashMap<String,ConnectionInfo> getDataPlaneConnections(){
            return dataPlaneConnections;
        }
        
        public ConnectionThread getControlPlaneConnection(String agentName){
            return controlPlaneConnections.get(agentName);
        }
        
        public ConnectionThread getDataPlaneConnection(String agentName){
            return controlPlaneConnections.get(agentName);
        }
        
        public void addControlPlaneConnection(String agentName,ConnectionThread agentConnection){
            controlPlaneConnections.put(agentName,agentConnection);
        }
        
        public void addDataPlaneConnection(String serviceID,ConnectionInfo serviceConnectionInfo){
            dataPlaneConnections.put(serviceID,serviceConnectionInfo);
        }
        
        public void removeControlPlaneConnection(String agentName){
            controlPlaneConnections.remove(agentName);
        }
        
        public void removeDataPlaneConnection(String serviceID){
            dataPlaneConnections.remove(serviceID);
        }
        
        public void replaceControlPlaneConnection(String agentName,ConnectionThread newAgentConnection){
            controlPlaneConnections.replace(agentName,newAgentConnection);
        }
        
        private JsonBuilder createTestRequest(String ServiceID,boolean isAgent){
            JsonBuilder testRequest=new JsonBuilder()
                    .addField("serviceID",ServiceID)
                    .addField("timeout",DEFAULT_TIMEOUT)
                    .addField("type","request");
            if(isAgent)
                testRequest.addField("action","testConnection");
            else
                testRequest.addField("action","testServiceConnection");
            return testRequest;
        }
        
        /**
         * Tests connection to given data plane service.
         * 
         * @param serviceID What to test.
         * 
         * @return True if connection works, false otherwise.
         * 
         * @throws IOException Any socket error occurred.
         * @throws RequestException If request was malformed.
         */
        public boolean testServiceConnection(String serviceID) throws IOException, RequestException{
            ConnectionInfo serviceConnectionInfo=dataPlaneConnections.get(serviceID);
            JsonReader response=controlPlaneConnections.get(serviceConnectionInfo.getAgentName())
                    .sendRequest(createTestRequest(serviceID,false));
            return response.readNumberPositive("status",Integer.class)==200;
        }
        
        /**
         * Tests connection to given agent.
         * 
         * @param agentName What to test.
         * 
         * @return True if connection works, false otherwise.
         */
        public boolean testAgentConnection(String agentName){
            ConnectionThread agentConnection=controlPlaneConnections.get(agentName);
            // Complete before DEFAULT_TIMEOUT or error occurred
            CompletableFuture<Integer> test=CompletableFuture.supplyAsync(()->{
                int status;
                try{
                    JsonReader response=agentConnection.sendRequest(createTestRequest(agentContainer.getAgentInfo(agentName).getAgentID().toString(),true));
                    status=response.readNumberPositive("status",Integer.class);
                }catch(Exception e){
                    status=-2;
                }
                return status;
            });
            int status;
            try{
                status=test.get(DEFAULT_TIMEOUT,TimeUnit.MILLISECONDS);
            }catch(Exception e){
                status=-1;
            }
            return status==200;
        }
        
        /**
         * Requests given service agent to reconect with it.
         * 
         * @param serviceID With what to reconect.
         * 
         * @throws IOException Any socket error occurred.
         * @throws RequestException If request was malformed.
         */
        public void requestAgentToReconectService(String serviceID) throws IOException, RequestException{
            String agentName=dataPlaneConnections.get(serviceID).getAgentName();
            final JsonBuilder reconectRequest=new JsonBuilder("reconectService")
                    .addField("serviceID",serviceID)
                    .addField("type","request");
            ConnectionThread agentThread=activeConnections.getControlPlaneConnection(agentName);
            agentThread.sendRequest(reconectRequest);
        }
    }
    
    @Override
    protected void processFirstConnection(Socket clientSocket) throws IOException{
        BufferedInputStream requestStream=new BufferedInputStream(clientSocket.getInputStream());
        BufferedOutputStream responseStream=new BufferedOutputStream(clientSocket.getOutputStream());
        final JsonBuilder response=new JsonBuilder();
        try{
            JsonReader request=new JsonReader(requestStream);
            String action=request.readString("action").toLowerCase();
            String agentName=request.readString("agent");
            String serviceUUID=request.readString("serviceID");
            switch(action){
                case "registeragent" -> {
                    AgentServicesInfo agent=new AgentServicesInfo(serviceUUID,
                            clientSocket.getInetAddress().getHostName(),
                            request.readNumberPositive("port",Integer.class),
                            request.readArrayOf("availableServices"));
                    agentContainer.registerAgent(agentName,agent);
                    System.out.println("[Info]: New agent registered: "+agentName);
                    ConnectionThread agentThread=new ConnectionThread(new Connection(clientSocket),this);
                    activeConnections.addControlPlaneConnection(agentName,agentThread);
                    agentThread.start();
                }
                case "servicestatuschange" -> {
                    String serviceType=request.readString("service").toLowerCase();
                    int newStatusCode=request.readNumber("newStatus",Integer.class);
                    ServiceStatus newStatus=ServiceStatus.interperFromNumber(newStatusCode);
                    agentContainer.changeServiceStatus(agentName,serviceType,
                            serviceUUID,newStatus);
                }
                case "registerserviceconnection"->{
                    String serviceType=request.readString("service").toLowerCase();
                    ServiceData data=agentContainer.getAgentInfo(agentName)
                            .getRunningServices().get(serviceType).get(serviceUUID);
                    if(data==null)
                        throw new RequestException("There is no registerd service with ID: "+serviceUUID);
                    ConnectionInfo serviceConnectionInfo=new ConnectionInfo(agentName,data);
                    activeConnections.addDataPlaneConnection(serviceUUID,serviceConnectionInfo);
                }
                default ->
                    throw new RequestException("Unknown request action!");
            }
            response.setStatus(200);
        }catch(RequestException e){
            processException(response,e);
        }
        responseStream.write(response.toBytes());
        responseStream.flush();
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

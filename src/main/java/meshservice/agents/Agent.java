package meshservice.agents;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import meshservice.communication.Connection;
import meshservice.communication.ConnectionThread;
import meshservice.communication.JsonBuilder;
import meshservice.communication.JsonReader;
import meshservice.communication.RequestException;
import meshservice.config.AgentConfig;
import meshservice.config.ConfigException;
import meshservice.services.ControlPlaneService;
import meshservice.services.Service;

/**
 * Base class for the agents.
 * 
 * @author ArtiFixal
 * @author ApolLuck
 */
public abstract class Agent extends ControlPlaneService{

    /**
     * Config of this agent.
     */
    protected AgentConfig config;
    
    /**
     * Services running on this agent.
     */
    protected HashMap<UUID,Service> runningServices;
    
    protected ConcurrentHashMap<UUID,ConnectionThread> activeConnections;
    
    protected ConnectionThread connectionToManager;

    public Agent(AgentConfig config) throws IOException,ConfigException{
        super(config.getAgentPort());
        this.config=config;
        initAgent();
    }

    public Agent(String name,int port,String managerHost,int managerPort) throws IOException{
        super(port);
        config=new AgentConfig(name,managerPort,managerHost,managerPort);
        initAgent();
    }
    
    private void initAgent()
    {
        activeConnections=new ConcurrentHashMap<>();
        runningServices=new HashMap<>();
        registerAgentAtManager();
    }

    public AgentConfig getConfig(){
        return config;
    }
    
    /**
     * @return Array of available services to run.
     */
    public abstract String[] getAvailableServices();

    /**
     * Creates new service instance of a given type on a given port. If port 
     * equals 0 OS will choose available port.
     * 
     * @param serviceType Service type to start.
     * @param port On which port to start new service.
     * 
     * @return Started service.
     * 
     * @throws IOException If any socket error occurres.
     * @throws RequestException If request was malformed.
     * @throws SQLException If SQL error occurred.
     */
    protected abstract Service startService(String serviceType,int port) throws IOException,RequestException,SQLException;
    
    /**
     * Runs new service instance of a given type on a given port.
     * 
     * @param serviceType Service type to run.
     * @param port Port on which to run new service.
     * 
     * @return Running service.
     * 
     * @throws IOException If any socket error occurres.
     * @throws RequestException If request was malformed.
     * @throws SQLException If SQL error occurred.
     */
    protected Service runService(String serviceType,int port) throws IOException,RequestException,SQLException
    {
        Service serv=startService(serviceType,port);
        runningServices.put(serv.getServiceID(),serv);
        //updateServiceStatusAtManager(serv.getServiceID(),serviceType,ServiceStatus.RUNNING);
        return serv;
    }
    
    protected void processFirstConnection(Socket clientSocket) throws IOException{
        BufferedInputStream requestStream=new BufferedInputStream(clientSocket.getInputStream());
        BufferedOutputStream responseStream=new BufferedOutputStream(clientSocket.getOutputStream());
        final JsonBuilder response=new JsonBuilder();
        try{
            JsonReader request=new JsonReader(requestStream);
            String action=request.readString("action").toLowerCase();
            String serviceUUID=request.readString("serviceID");
            switch(action){
                case "registerconnection" -> {
                    ConnectionThread serviceThread=new ConnectionThread(new Connection(clientSocket),this);
                    activeConnections.put(UUID.fromString(serviceUUID),serviceThread);
                    responseStream.write(response.toBytes());
                    responseStream.flush();
                    serviceThread.start();
                }
                case "renewmanagerconnection" -> {
                    connectionToManager.close();
                    connectionToManager=new ConnectionThread(new Connection(clientSocket),this);
                    connectionToManager.start();
                }
                default ->
                    throw new RequestException("Unknown connection register action!");
            }
            response.setStatus(200);
        }catch(RequestException e){
            processException(response,e);
        }
        responseStream.write(response.toBytes());
        responseStream.flush();
    }

    /**
     * Resets service inactivity timer.
     *
     * @param serviceID Service which timer will be reset.
     * @param serviceType Type of service.
     * 
     * @throws IOException If any socket error occurres.
     * @throws RequestException
     */
    protected void renewTimer(String serviceID,String serviceType) throws IOException, RequestException
    {
        JsonBuilder renewRequest=new JsonBuilder("renewtimer")
                .addField("agent",config.getAgentName())
                .addField("service",serviceType)
                .addField("serviceID",serviceID);
        communicateWithManager(renewRequest);
    }

    /**
     * Sends request to the given service.
     * 
     * @param microService Where to send request.
     * @param request What to send.
     * 
     * @return Service response.
     * 
     * @throws IOException If any socket error occurred.
     * @throws RequestException If request was malformed.
     */
    protected JsonReader communicateWithService(final Service microService,
            JsonBuilder request) throws IOException,RequestException
    {
        try(Connection serviceConnection=new Connection(new Socket("localhost",microService.getPort())))
        {
            return serviceConnection.sendRequest(request);
        }
    }
    
    /**
     * Sends request to the manager.
     * 
     * @param request What to send.
     * 
     * @return Manager response.
     * 
     * @throws IOException If any socket error occurred.
     * @throws RequestException If request was malformed.
     */
    protected JsonReader communicateWithManager(JsonBuilder request) throws IOException,RequestException
    {
        return connectionToManager.sendRequest(request);
    }
    
    private Socket createServiceSocket(UUID serviceID) throws IOException{
        Service serv=runningServices.get(serviceID);
        return new Socket("localhost",serv.getPort());
    }
    
    /**
     * Tests connection to given service.
     * 
     * @param serviceID Service to which test connection.
     * 
     * @return Is connection working properly.
     * 
     * @throws IOException If any socket error occurred.
     * @throws RequestException If request was malformed.
     */
    protected boolean testServiceConnection(UUID serviceID) throws IOException,RequestException{
        
        final JsonBuilder testRequest=new JsonBuilder("testConnection")
                .addField("type","request");
        try(Connection testConnection=new Connection(createServiceSocket(serviceID))){
            JsonReader response=testConnection.sendRequest(testRequest);
            return response.readNumberPositive("status",Integer.class)==200;
        }
    }
    
    protected void reconectService(UUID serviceID) throws IOException{
        ConnectionThread oldThread=activeConnections.get(serviceID);
        oldThread.close();
        ConnectionThread newThread=new ConnectionThread(new Connection(createServiceSocket(serviceID)),this);
        activeConnections.replace(serviceID,newThread);
        newThread.start();
    }

    /**
     * Registers this agent at the manager.
     */
    private void registerAgentAtManager()
    {
        final JsonBuilder request=new JsonBuilder("registerAgent")
                .addField("type","request")
                .addField("agent",config.getAgentName())
                .addField("serviceID",getServiceID())
                .addField("port",getPort())
                .addArray("availableServices",getAvailableServices());
        try{
            connectionToManager=new ConnectionThread(new Connection(new Socket(config.getManagerHost(),config.getManagerPort())),this);
            communicateWithManager(request);
            connectionToManager.start();
        }catch(Exception e){
            System.out.println("Failed to register at Manager due to: "+e);
            try{
                closeService();
            }catch(IOException ex){
                System.out.println(ex);
            }
        }
    }
}

package meshservice.agents;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.UUID;
import meshservice.ServiceStatus;
import meshservice.communication.JsonBuilder;
import meshservice.communication.JsonReader;
import meshservice.communication.RequestException;
import meshservice.config.AgentConfig;
import meshservice.config.ConfigException;
import meshservice.services.MultithreadService;
import meshservice.services.Service;

/**
 * Base class for the agents.
 * 
 * @author ArtiFixal
 * @author ApolLuck
 */
public abstract class Agent extends MultithreadService{

    /**
     * Config of this agent.
     */
    protected AgentConfig config;
    
    /**
     * Services running on this agent.
     */
    protected HashMap<String,Service> runningServices;

    public Agent(AgentConfig config) throws IOException,ConfigException{
        super(config.getAgentPort());
        this.config=config;
        registerAgentAtManager();
    }

    public Agent(String name,int port,String managerHost,int managerPort) throws IOException{
        super(port);
        config=new AgentConfig(name,managerPort,managerHost,managerPort);
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
        runningServices.put(serv.getServiceID().toString(),serv);
        updateServiceStatusAtManager(serv.getServiceID(),ServiceStatus.RUNNING);
        return serv;
    }

    /**
     * Resets service inactivity timer.
     *
     * @param serviceName Service which timer will be reset
     * 
     * @throws IOException If any socket error occurres.
     */
    protected void renewTimer(String serviceName) throws IOException
    {
        try(Socket toManager=new Socket(config.getManagerHost(),config.getManagerPort()))
        {
            try(BufferedOutputStream out=new BufferedOutputStream(toManager.getOutputStream())){
                JsonBuilder renewRequest=new JsonBuilder("renewtimer");
                renewRequest.addField("agent",config.getAgentName());
                renewRequest.addField("service",serviceName);
                out.write(renewRequest.toBytes());
            }
        }
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
        return communicateWithHost("localhost",microService.getPort(),request);
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
        return communicateWithHost(config.getManagerHost(),config.getManagerPort(),request);
    }

    /**
     * Registers this agent at the manager.
     */
    private void registerAgentAtManager()
    {
        final JsonBuilder request=new JsonBuilder("registerAgent")
                .addField("agent",config.getAgentName())
                .addField("serviceID",getServiceID())
                .addField("port",getPort())
                .addArray("availableServices",getAvailableServices());
        try{
            communicateWithManager(request);
        }catch(Exception e){
            System.out.println("Failed to register at Manager due to: "+e);
            try{
                closeService();
            }catch(IOException ex){
                System.out.println(ex);
            }
        }
    }

    /**
     * Updates given service status at the manager.
     * 
     * @param serviceID UUID of service to change status.
     * @param status New status.
     * 
     * @throws IOException If any socket error occurred.
     * @throws RequestException If request was malformed.
     */
    public void updateServiceStatusAtManager(UUID serviceID,ServiceStatus status)
            throws IOException,RequestException
    {
        final JsonBuilder request=new JsonBuilder("serviceStatusChange")
                .addField("agent",config.getAgentName())
                .addField("serviceID",serviceID)
                .addField("newStatus",status);
        communicateWithManager(request);
    }
}

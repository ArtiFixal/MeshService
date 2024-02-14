package meshservice.agents;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.UUID;
import meshservice.ServiceStatus;
import meshservice.communication.Connection;
import meshservice.communication.JsonBuilder;
import meshservice.communication.JsonReader;
import meshservice.communication.RequestException;
import meshservice.services.*;

/**
 * Agent responsible for services.
 * 
 * @author ArtiFixal
 * @author ApolLuck
 */
public class ServiceAgent extends Agent{
    public static final String[] REQUEST_REQUIRED_FIELDS=new String[]{"action"};
    
    public ServiceAgent(String name,int port,String managerHost,int managerPort) throws IOException{
        super(name,port,managerHost,managerPort);
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
    public void processRequest(BufferedInputStream request,JsonBuilder response) throws IOException,RequestException,SQLException{
        final JsonReader reader=new JsonReader(request);
        System.out.println("Service agent request: "+reader.getRequestNode().toPrettyString());
        String action=reader.readString("action").toLowerCase();
        switch(action){
            case "run" -> {
                String serviceType=reader.readString("service").toLowerCase();
                int port=reader.readNumberPositive("port",Integer.class);
                Service serv=runService(serviceType,port);
                System.out.println("[Info]: Agent started service: "+serviceType);
                synchronized(runningServices){
                    runningServices.put(serv.getServiceID(),serv);
                    response.addField("serviceID",serv.getServiceID())
                            .addField("host",serverSocket.getInetAddress().getHostName())
                            .addField("port",serv.getPort())
                            .addArray("requiredFields",serv.getRequiredRequestFields())
                            .addArray("additionalFields",serv.getAdditionalResponseFields());
                }
                response.addField("service",serviceType);
            }
            case "closeservice" -> {
                String serviceType=reader.readString("service").toLowerCase();
                UUID serviceUUID=UUID.fromString(reader.readString("serviceID"));
                try(Connection updateRequest=new Connection(new Socket(config.getManagerHost(),config.getManagerPort()))){
                    synchronized(runningServices){
                        Service serv=runningServices.get(serviceUUID);
                        updateServiceStatusAtManager(updateRequest,serviceUUID,serviceType,ServiceStatus.CLOSING);
                        serv.closeService();
                        updateServiceStatusAtManager(updateRequest,serviceUUID,serviceType,ServiceStatus.CLOSED);
                        runningServices.remove(serviceUUID);
                    }
                }
                response.addField("service",serviceType);
            }
            case "testserviceconnection" -> {
                UUID serviceUUID=UUID.fromString(reader.readString("serviceID"));
                synchronized(runningServices){
                    testServiceConnection(serviceUUID);
                }
            }
            case "testconnection" -> {}
            case "reconectservice" -> {
                UUID serviceUUID=UUID.fromString(reader.readString("serviceID"));
                reconectService(serviceUUID);
            }
            default ->
                throw new RequestException("Unknown request action.");
        }
        // Manager request to run given service
        // Setting response fields
        response.addField("type","response");
        response.setStatus(200);
    }
    
    @Override
    protected Service startService(String serviceName,int port) throws IOException,RequestException,SQLException{
        return switch(serviceName){
            case "login" ->
                new UserAuthenticationService(port);
            case "register" ->
                new UserRegisterService(port);
            case "addpost" ->
                new AddPostService(port);
            case "getposts" ->
                new GetPostsService(port);
            case "uploadfile" ->
                new FileUploadService(port);
            case "getfile" ->
                new FileDownloadService(port);
            default ->
                throw new RequestException("Unknown service name: "+serviceName);
        };
    }

    @Override
    public String[] getAvailableServices(){
        final String[] services=new String[]{"login","register","addpost",
            "getposts","uploadfile","getfile"};
        return services;
    }
    
    /**
     * Updates given service status at the manager.
     * 
     * @param connection Connection to the manager.
     * @param serviceID UUID of service to change status.
     * @param serviceType What the service does.
     * @param status New status.
     * 
     * @throws IOException If any socket error occurred.
     * @throws RequestException If request was malformed.
     */
    public void updateServiceStatusAtManager(Connection connection,UUID serviceID,String serviceType,
            ServiceStatus status) throws IOException,RequestException
    {
        final JsonBuilder request=new JsonBuilder("serviceStatusChange")
                .addField("type","request")
                .addField("agent",config.getAgentName())
                .addField("serviceID",serviceID)
                .addField("service",serviceType)
                .addField("newStatus",status.getStatusCode());
        connection.sendRequest(request);
    }
    
    public static void main(String[] args){
        try{
            ServiceAgent agent=new ServiceAgent("Agent1",8000,"localhost",9000);
            agent.join();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}

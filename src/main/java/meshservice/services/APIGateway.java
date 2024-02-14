package meshservice.services;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;
import meshservice.communication.Connection;
import meshservice.communication.ConnectionThread;
import meshservice.communication.JsonBuilder;
import meshservice.communication.JsonReader;
import meshservice.communication.RequestException;

/**
 * Gateway by which client communicates with services.
 * 
 * @author ArtiFixal
 */
public class APIGateway extends MultithreadService{
    public static final String[] REQUEST_REQUIRED_FIELDS=new String[]{"action"};
    
    /**
     * Socket with established connection to it's Agent.
     */
    private ConnectionThread agentConnection;
    
    /**
     * ID of current client message.
     */
    private final AtomicLong currentMessageID = new AtomicLong(0);
    
    public APIGateway() throws IOException {
        this(0);
    }
    
    public APIGateway(int port) throws IOException {
        super(port);
        registerConnectionAtAgent();
    }
    
    private void registerConnectionAtAgent() throws IOException{
        agentConnection=new ConnectionThread(new Connection(new Socket("localhost", 10000)),this);
        agentConnection.start();
        JsonBuilder request=new JsonBuilder("registerConnection");
        request.addField("serviceID",getServiceID());
        try{
            sendToAgent(request);
        }catch(Exception e){
            System.out.println("[API Gateway error]: "+e);
        }
    }

    /**
     * Sends given request to the {@code APIGateway} agent.
     * 
     * @param request What to send to agent.
     * 
     * @return Response from the agent.
     * 
     * @throws IOException
     * @throws RequestException 
     */
    protected JsonReader sendToAgent(JsonBuilder request) throws IOException, RequestException
    {
        System.out.println("Request sent to the ApiGateway agent: "+request.getJson().toPrettyString());
        
        return agentConnection.sendRequest(request);
    }
    
    /**
     * Assigns messageID to a given request.
     * 
     * @param requestMessage Where to assign messageID.
     * 
     * @return assigned ID.
     */
    public long assignMessageID(JsonBuilder requestMessage)
    {
        long assignedID=currentMessageID.getAndIncrement();
        requestMessage.addField("messageID", assignedID);
        currentMessageID.compareAndSet(Long.MAX_VALUE,0);
        return assignedID;
    }
    
    public void resetMessageID()
    {
        currentMessageID.set(0);
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
    public void processRequest(BufferedInputStream request, JsonBuilder response)
            throws IOException, RequestException
    {
        final JsonReader reader=new JsonReader(request);
        String action=reader.readString("action");
        System.out.println("ApiGateway request: "+reader.getRequestNode().toPrettyString());
        // Ask API Gateway agent for service host and port
        final JsonBuilder agentRequest=new JsonBuilder("getServiceInfo");
        assignMessageID(agentRequest);
        agentRequest.addField("service", action)
            .addField("type", "request");
        JsonReader agentResponse=sendToAgent(agentRequest);
        // Communicate with service
        String serviceHost=agentResponse.readString("host");
        int servicePort=agentResponse.readNumber("port", Integer.class);
        final JsonBuilder serviceRequest=new JsonBuilder();
        // Forward only request required fields and drop unwanted
        for(String field:agentResponse.readArrayOf("requiredFields"))
        {
            serviceRequest.setNode(field,reader.getNode(field));
        }
        try(Connection serviceConnection=new Connection(new Socket(serviceHost,servicePort))){
            JsonReader serviceResponse=serviceConnection.sendRequest(serviceRequest);
            // Forward additional response fields
            final int serviceResponseStatus=serviceResponse.readNumber("status",Integer.class);
            if(serviceResponseStatus==200)
            {
                for(String field:agentResponse.readArrayOf("additionalFields"))
                    response.setNode(field,serviceResponse.getNode(field));
            }
            String responseText=serviceResponse.readString("responseText");
            response.setStatus(responseText,serviceResponseStatus);
        }
    }

    @Override
    public void closeService() throws IOException {
        if(!agentConnection.isAlive())
            agentConnection.close();
        super.closeService();
    }
    
    public static void main(String[] args) throws IOException {
        try {
            APIGateway a = new APIGateway(10000);
            a.join();
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}

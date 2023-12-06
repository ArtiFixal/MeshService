package meshservice.services;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;
import meshservice.communication.JsonBuilder;
import meshservice.communication.JsonReader;
import meshservice.communication.RequestException;

/**
 * Gateway by which client communicates with services.
 * 
 * @author ArtiFixal
 */
public class APIGateway extends MultithreadService{
    /**
     * Socket with established connection to it's Agent.
     */
    private Socket agentConnection;
    
    /**
     * ID of current client message.
     */
    private final AtomicLong currentMessageID = new AtomicLong(0);
    
    public APIGateway() throws IOException {
        this(0);
    }
    
    public APIGateway(int port) throws IOException {
        super(port);
        agentConnection=new Socket("localhost", 8000);
        agentConnection.setKeepAlive(true);
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
        try(BufferedInputStream in = new BufferedInputStream(agentConnection.getInputStream());
                BufferedOutputStream out = new BufferedOutputStream(agentConnection.getOutputStream()))
        {
            out.write(request.toBytes());
            out.flush();
            System.out.println("Request sent to the agent: " + request.getJson().toPrettyString());
            return new JsonReader(in);
        }
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
    public void processRequest(BufferedInputStream request, JsonBuilder response)
            throws IOException, RequestException
    {
        final JsonReader reader=new JsonReader(request);
        String action=reader.readString("action");
        System.out.println("Received: "+reader.getRequestNode().toPrettyString());
        // Ask API Gateway agent for service host and port
        final JsonBuilder agentRequest=new JsonBuilder("process");
        assignMessageID(agentRequest);
        agentRequest.addField("action", "getServiceInfo");
        agentRequest.addField("service", action);
        agentRequest.addField("type", "request");
        JsonReader agentResponse=sendToAgent(agentRequest);
        // Communicate with service
        String serviceHost=agentResponse.readString("host");
        int servicePort=agentResponse.readNumber("port", Integer.class);
        final JsonBuilder serviceRequest=new JsonBuilder();
        JsonReader serviceResponse=communicateWithHost(serviceHost, servicePort, serviceRequest);
        String responseText=serviceResponse.readString("responseText");
        response.setStatus(responseText,200);
    }

    @Override
    public void closeService() throws IOException {
        if(!agentConnection.isClosed())
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

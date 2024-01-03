package meshservice.agents;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.UUID;
import meshservice.communication.JsonBuilder;
import meshservice.communication.JsonReader;
import meshservice.communication.RequestException;
import meshservice.communication.ServiceHostport;
import meshservice.config.AgentConfig;
import meshservice.config.ConfigException;
import meshservice.services.APIGateway;
import meshservice.services.Service;

/**
 * Agent responsible for API Gateway instances.
 * 
 * @author ArtiFixal
 */
public class APIGatewayAgent extends Agent{
    public static final String[] REQUEST_REQUIRED_FIELDS=new String[]{"action"};
    
    public APIGatewayAgent(AgentConfig config) throws IOException, ConfigException {
        super(config);
        int firstApiGatewayInstancePort=config.getAgentPort()+1;
        runningServices.put(UUID.randomUUID(),new APIGateway(firstApiGatewayInstancePort));
    }

    @Override
    protected Service startService(String serviceName,int port) throws IOException, RequestException {
        return new APIGateway(port);
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
    public String[] getAvailableServices(){
        final String[] services=new String[]{"apigateway"};
        return services;
    }
    
    @Override
    public void processRequest(BufferedInputStream request, JsonBuilder response)
            throws IOException, RequestException
    {
        final JsonReader reader=new JsonReader(request);
        System.out.println("ApiGateway Agent: "+reader.getRequestNode().toPrettyString());
        String action=reader.readString("action").toLowerCase();
        switch (action) {
            case "getserviceinfo" -> {
                String serviceType=reader.readString("service");
                ServiceHostport managerResponse=askManagerForServiceHostport(serviceType);
                response.addField("host", managerResponse.getHost())
                    .addField("port", managerResponse.getPort())
                    .addArray("requiredFields",managerResponse.getRequestRequiredFields())
                    .addArray("additionalFields",managerResponse.getAdditionalResponseFields());
            }
            default -> throw new RequestException("Unknown request action!");
        }
        response.setStatus(200);
    }
    
    /**
     * Retrieves {@code Hostport} of given service from manager.
     * 
     * @param ServiceName Which service to ask for.
     * 
     * @return Service hostport.
     * 
     * @throws IOException If any socket error occurres.
     * @throws RequestException If request was malformed.
     */
    protected ServiceHostport askManagerForServiceHostport(String ServiceName)
            throws IOException,RequestException
    {
        final JsonBuilder request=new JsonBuilder("askForService")
                .addField("type","request")
                .addField("agent", config.getAgentName())
                .addField("service", ServiceName);
        JsonReader response=communicateWithManager(request);
        return new ServiceHostport(response.readArrayOf("requiredFields")
                .toArray(String[]::new),
            response.readArrayOf("additionalFields").toArray(String[]::new),
            response.readString("host"),
            response.readNumberPositive("port",Integer.class));
    }

}

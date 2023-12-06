package meshservice.agents;

import java.io.BufferedInputStream;
import java.io.IOException;
import meshservice.communication.Hostport;
import meshservice.communication.JsonBuilder;
import meshservice.communication.JsonReader;
import meshservice.communication.RequestException;
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
    
    public APIGatewayAgent(AgentConfig config) throws IOException, ConfigException {
        super(config);
        runningServices.put("APIGateway", new APIGateway());
    }

    @Override
    protected Service runService(String serviceName,int port) throws IOException, RequestException {
        return new APIGateway(port);
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
        String action=reader.readString("action").toLowerCase();
        switch (action) {
            case "getserviceinfo" -> {
                String serviceName=reader.readString("service");
                Hostport toService=askManagerForServiceHostport(serviceName);
                response.addField("host", toService.getHost());
                response.addField("port", toService.getPort());
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
    protected Hostport askManagerForServiceHostport(String ServiceName)
            throws IOException,RequestException
    {
        final JsonBuilder request=new JsonBuilder("askForService")
                .addField("agent", config.getAgentName())
                .addField("service", ServiceName);
        JsonReader response=communicateWithManager(request);
        return new Hostport(response.readString("host"),
                response.readNumberPositive("port",Integer.class));
    }

}

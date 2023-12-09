package meshservice.agents;

import java.io.BufferedInputStream;
import java.io.IOException;
import meshservice.ServiceStatus;
import meshservice.communication.JsonBuilder;
import meshservice.communication.JsonReader;
import meshservice.communication.RequestException;
import meshservice.services.MicroService1;
import meshservice.services.MicroService2;
import meshservice.services.Service;

/**
 * Agent responsible for services.
 * 
 * @author ArtiFixal
 * @author ApolLuck
 */
public class ServiceAgent extends Agent{

    public ServiceAgent(String name,int port,String managerHost,int managerPort) throws IOException{
        super(name,port,managerHost,managerPort);
    }

    @Override
    public void processRequest(BufferedInputStream request,JsonBuilder response) throws IOException,RequestException{
        final JsonReader reader=new JsonReader(request);
        System.out.println("Received: "+reader.getRequestNode().toPrettyString());
        String type=reader.readString("type").toLowerCase();
        switch(type){
            case "request" ->{
                int messageID=reader.readNumber("messageID",Integer.class);
                response.addField("responseText",messageID);
                String action=reader.readString("action").toLowerCase();
                String serviceType=reader.readString("service");
                switch(action){
                    // Manager request to run given service
                    case "run" -> {
                        int port=reader.readNumberPositive("port",Integer.class);
                        Service serv=getOrRun(serviceType,port);
                        runningServices.put(serviceType,serv);
                        response.addField("serviceID",serv.getServiceID());
                        response.addField("port",port);
                    }
                    case "closeservice" -> {
                        Service serv=runningServices.get(serviceType);
                        updateServiceStatusAtManager(serv.getServiceID(),ServiceStatus.CLOSING);
                        serv.closeService();
                        updateServiceStatusAtManager(serv.getServiceID(),ServiceStatus.CLOSED);
                    }
                    default ->
                        throw new RequestException("Nieprawidłowe zapytanie.");
                }
                // Setting response fields
                response.addField("service",serviceType);
                response.addField("type","response");
                response.setStatus(200);
            }
            case "response" -> 
                System.out.println("Received: "+reader.getRequestNode().toPrettyString());
            default -> 
                throw new RequestException("Nieprawidłowe zapytanie.");
        }
    }

    @Override
    protected Service runService(String serviceName,int port) throws IOException,RequestException{
        return switch(serviceName){
            case "MS1" ->
                new MicroService1(port);
            case "MS2" ->
                new MicroService2(port);
            default ->
                throw new RequestException("Nieprawidłowe zapytanie.");
        };
    }

    @Override
    public String[] getAvailableServices(){
        final String[] services=new String[]{"ms1","ms2"};
        return services;
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

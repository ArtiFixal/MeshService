package meshservice;

import meshservice.agents.APIGatewayAgent;
import meshservice.agents.ServiceAgent;
import meshservice.config.AgentConfig;
import meshservice.services.manager.ServiceManager;

public class MeshService{
	
	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args){
            try{
                int managerPort=9000;
                ServiceManager manager=new ServiceManager(managerPort);
                ServiceAgent agent=new ServiceAgent("Agent1", 8000, "localhost", managerPort);
                APIGatewayAgent apiAgent=new APIGatewayAgent(new AgentConfig("apiConfig.cfg"));
            }catch(Exception e){
                System.out.println(e);
            }
	}
	
}

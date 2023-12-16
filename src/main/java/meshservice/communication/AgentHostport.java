package meshservice.communication;

/**
 * Class responsible for storing {@code Agent} hostport.
 * 
 * @author ArtiFixal
 * @see Hostport
 */
public class AgentHostport extends Hostport{
    private String agentName;
    
    public AgentHostport(String agentName,int port){
        this(agentName,"localhost",port);
    }

    public AgentHostport(String agentName,String host,int port){
        super(host,port);
        this.agentName=agentName;
    }

    public String getAgentName(){
        return agentName;
    }
}

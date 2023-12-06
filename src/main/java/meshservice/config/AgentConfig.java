package meshservice.config;

import java.io.File;
import java.io.IOException;

/**
 * Class which stores {@code Agent} config.
 *
 * @author ArtiFixal
 */
public class AgentConfig{

    /**
     * Agent name.
     */
    public String agentName;

    /**
     * Port on which agent runs.
     */
    private int agentPort;

    /**
     * Host on which runs manager.
     */
    private String managerHost;

    /**
     * Port on which manager runs.
     */
    private int managerPort;

    public AgentConfig(String path) throws IOException,ConfigException{
        ConfigIO config=new ConfigIO(new File(path));
        agentName=config.readOptionValue("<agentName>");
        agentPort=readInt(config,"<agentPort>");
        managerHost=config.readOptionValue("<managerHost>");
        managerPort=readInt(config,"<managerPort>");
    }

    public AgentConfig(String agentName,int agentPort,String managerHost,int managerPort){
        this.agentName=agentName;
        this.agentPort=agentPort;
        this.managerHost=managerHost;
        this.managerPort=managerPort;
    }

    public String getAgentName(){
        return agentName;
    }

    public int getAgentPort(){
        return agentPort;
    }

    public String getManagerHost(){
        return managerHost;
    }

    public int getManagerPort(){
        return managerPort;
    }

    /**
     * Reads int from given option.
     * 
     * @param config File containing config.
     * @param optionName Option to read from.
     * 
     * @return Option int value.
     * 
     * @throws IOException If any error occurred during read.
     * @throws ConfigException If any config error occurred.
     */
    private int readInt(ConfigIO config,String optionName) throws IOException,ConfigException{
        String stringVal=config.readOptionValue(optionName);
        try{
            return Integer.parseInt(stringVal);
        }catch(NumberFormatException e){
            throw new ConfigException("Malformed config format: Menager port unreadable",
                    config.getConfigFile().getPath());
        }
    }
}

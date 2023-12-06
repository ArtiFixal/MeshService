package meshservice.config;

/**
 * Exception thrown when an error related to config occured.
 * 
 * @author ArtiFixal
 */
public class ConfigException extends Exception{
    /**
     * Path to a config file.
     */
    protected final String configFilePath;
    
    public ConfigException(String msg,String pathToConfigFile) {
        super(msg);
        configFilePath=pathToConfigFile;
    }

    public String getConfigFilePath() {
        return configFilePath;
    }
}

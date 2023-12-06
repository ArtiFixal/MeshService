package meshservice.config;

/**
 * Exception thrown when searched option wasn't found in a file.
 *
 * @author ArtiFixal
 */
public class OptionNotFoundException extends ConfigException {

    /**
     * For what we searched in the file.
     */
    private final String soughtOption;

    public OptionNotFoundException(String option,String pathToAConfigFile) {
        super("Option: "+option+" was not found in the given file: "+pathToAConfigFile,pathToAConfigFile);
        soughtOption=option;
    }
    
    public String getSoughtOption() {
        return soughtOption;
    }
}

package meshservice.communication;

/**
 * Class responsible for storing {@code Service} hostport.
 * 
 * @author ArtiFixal
 * @see Hostport
 */
public class ServiceHostport extends Hostport{
    /**
     * Fields which request must contain.
     */
    public String[] requestRequiredFields;
    
    public ServiceHostport(String[] requestRequiredFields,int port){
        this(requestRequiredFields,"localhost",port);
    }
    
    public ServiceHostport(String[] requestRequiredFields,String host,int port){
        super(host,port);
        this.requestRequiredFields=requestRequiredFields;
    }

    public String[] getRequestRequiredFields(){
        return requestRequiredFields;
    }
}

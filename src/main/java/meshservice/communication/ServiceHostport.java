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
    
    /**
     * Fields which response contain in addition to responseText.
     */
    public String[] additionalResponseFields;
    
    public ServiceHostport(String[] requestRequiredFields,String[] additionalResponseFields,int port){
        this(requestRequiredFields,additionalResponseFields,"localhost",port);
    }
    
    public ServiceHostport(String[] requestRequiredFields,String[] additionalResponseFields,String host,int port){
        super(host,port);
        this.requestRequiredFields=requestRequiredFields;
        this.additionalResponseFields=additionalResponseFields;
    }

    public String[] getRequestRequiredFields(){
        return requestRequiredFields;
    }

    public String[] getAdditionalResponseFields(){
        return additionalResponseFields;
    }
}

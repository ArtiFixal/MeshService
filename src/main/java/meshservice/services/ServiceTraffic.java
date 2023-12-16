package meshservice.services;

import java.io.IOException;
import meshservice.communication.RequestException;
import meshservice.services.manager.ServiceNotFoundException;

/**
 * Stores information about current traffic to the {@code Service} type.
 * 
 * @author ArtiFixal
 */
public class ServiceTraffic{
    /**
     * Current request per second.
     */
    private long currentRPS;
    
    /**
     * Previous request per second.
     */
    private long previousRPS;
    
    /**
     * Ratio at which new service instance should be invoked.
     */
    private float invokeRatio;
    
    /**
     * Callback called when new service instance should be invoked.
     */
    private ServiceInvoke invokeCallback;

    public ServiceTraffic(float invokeRatio,ServiceInvoke invokeCallback){
        this.invokeRatio=invokeRatio;
        this.invokeCallback=invokeCallback;
        currentRPS=2;
    }

    public long getCurrentRPS(){
        return currentRPS-1;
    }

    public long getPreviousRPS(){
        return previousRPS-1;
    }
    
    public float getInvokeRatio(){
        return invokeRatio;
    }
    
    public void setInvokeRatio(float invokeRatio){
        this.invokeRatio=invokeRatio;
    }
    
    public long increaseCurrentRPS()
    {
        currentRPS++;
        return currentRPS;
    }
    
    public void secondPassed() throws RequestException,IOException,ServiceNotFoundException
    {
        if(isInvokeRequired())
            invokeCallback.invoke();
        previousRPS=currentRPS;
        currentRPS=1;
    }
    
    public boolean isInvokeRequired()
    {
        return currentRPS/previousRPS>=invokeRatio;
    }
}

package meshservice.services;

import java.io.IOException;
import meshservice.communication.RequestException;
import meshservice.services.manager.ServiceNotFoundException;

/**
 * Interface adding {@code Service} invoke functionality.
 * 
 * @author ArtiFixal
 */
public interface ServiceInvoke{
    /**
     * Invokes new service.
     * 
     * @throws RequestException If invoke request was malformed.
     * @throws IOException If socket error occurred.
     * @throws ServiceNotFoundException If service was not found.
     */
    public void invoke() throws RequestException,IOException,ServiceNotFoundException;
}

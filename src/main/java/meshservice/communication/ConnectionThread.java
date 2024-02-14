package meshservice.communication;

import java.io.IOException;
import meshservice.services.Service;

/**
 * Class responsible for processing accepted sockets by the {@code ServerSocket}.
 * 
 * @author ArtiFixal
 */
public class ConnectionThread extends Thread implements AutoCloseable{
    
    /**
     * Connection used by this thread.
     */
    private final Connection connection;
    
    /**
     * Service which will process requests.
     */
    private Service handle;

    public ConnectionThread(Connection connection,Service handle){
        this.connection=connection;
        this.handle=handle;
    }

    public Connection getConnection(){
        return connection;
    }

    @Override
    public void run(){
        while(connection.isAlive())
        {
            synchronized(connection){
                try{
                    // Avoid lock during read in processRequest
                    if(connection.getRequestStream().available()<6){
                        continue;
                    }
                    handle.processConnection(connection);
                }catch(Exception e){
                    System.out.println("[Connection error]: "+e);
                    e.printStackTrace();
                }
            }
        }
        try{
            // Finish shutdown
            connection.getConnectionSocket().shutdownOutput();
            connection.closeSocket();
        }catch(IOException e){
            // Already closed
        }
    }
    
    /**
     * Sends given request to the service.
     * 
     * @param request What to send.
     * 
     * @return Response from the service.
     * 
     * @throws IOException Any socket error occurred.
     * @throws RequestException If request was malformed.
     */
    public JsonReader sendRequest(JsonBuilder request) throws IOException, RequestException{
        synchronized(connection){
            return connection.sendRequest(request);
        }
    }
    
    /**
     * Shuts down this connection (last request will be processed).
     * 
     * @throws IOException Any socket error occurred.
     */
    public synchronized void shutdownConnection() throws IOException{
        connection.shutdown();
    }
    
    /**
     * Closes this connection (last request will <b>NOT</b> be processed).
     * 
     * @throws IOException Any socket error occurred.
     */
    @Override
    public synchronized void close() throws IOException{
        if(connection.isAlive())
        {
            connection.close();
        }
    }
}

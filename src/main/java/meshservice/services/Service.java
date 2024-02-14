package meshservice.services;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.UUID;
import meshservice.communication.Connection;
import meshservice.communication.RequestException;
import meshservice.communication.JsonBuilder;

/**
 * Base class for the single threaded services.
 *
 * @author ArtiFixal
 */
public abstract class Service extends Thread{
    public static final String[] EMPTY_ARRAY=new String[0];
    
    /**
     * Socket on which service will listen for clients.
     */
    protected ServerSocket serverSocket;
    
    /**
     * Determines whether {@link #serverSocket} is alive.
     */
    protected boolean isAlive;
    
    /**
     * Unique service identifier.
     */
    protected UUID serviceID;

    public Service() throws IOException{
        this(0);
    }

    public Service(int port) throws IOException{
        serverSocket=new ServerSocket(port);
        isAlive=true;
        serviceID=UUID.randomUUID();
        start();
    }

    /**
     * Processes client request.
     *
     * @param request Request data.
     * @param response Response which client will receive.
     *
     * @throws IOException If any I/O error occurs.
     * @throws RequestException If client request is invalid.
     * @throws SQLException If SQL error occurs.
     */
    public abstract void processRequest(BufferedInputStream request,JsonBuilder response)
            throws IOException,RequestException,SQLException;

    /**
     * @return Fields which must be included in a valid request.
     */
    public abstract String[] getRequiredRequestFields();
    
    /**
     * @return Fields which in addition to responseText create response.
     */
    public abstract String[] getAdditionalResponseFields();
    
    /**
     * Sets up client socket.
     *
     * @return Configured socket.
     * @throws IOException If any I/O error occurs.
     */
    protected Socket prepareSocket() throws IOException
    {
        final Socket clientSocket=serverSocket.accept();
        // 3 min blocking IO timeout
        clientSocket.setSoTimeout(180000);
        return clientSocket;
    }
    
    public void processConnection(Connection clientConnection) throws IOException{
        final JsonBuilder responseToSend=new JsonBuilder();
        try{
            processRequest(clientConnection.getRequestStream(),responseToSend);
        }catch(SQLException|RequestException e){
            System.out.println(e);
            processException(responseToSend,e);
            e.printStackTrace();
        }
        clientConnection.getResponseStream().write(responseToSend.toBytes());
        clientConnection.getResponseStream().flush();
        clientConnection.close();
    }

    public void processSocket(Socket clientSocket) throws IOException
    {
        try(final Connection clientConnection=new Connection(clientSocket))
        {
            processConnection(clientConnection);
        }
    }

    @Override
    public void run()
    {
        while(isAlive)
        {
            try{
                Socket clientSocket=prepareSocket();
                processSocket(clientSocket);
            }catch(IOException e){
                System.out.println(e);
                e.printStackTrace();
            }
        }
    }

    /**
     * Processes occured exception.
     *
     * @param response An error which will be sent to the client.
     * @param error What happend.
     */
    protected void processException(JsonBuilder response,Exception error)
    {
        System.out.println("[Error]: "+error);
        response.clear();
        if(error instanceof RequestException e)
            response.setStatus(e.getMessage(),e.getResponseStatus());
        else
            response.setStatus(error.getMessage(),HttpURLConnection.HTTP_INTERNAL_ERROR);
    }

    public UUID getServiceID(){
        return serviceID;
    }

    public int getPort(){
        return serverSocket.getLocalPort();
    }

    /**
     * Closes this service (last request will still be processed).
     * 
     * @throws IOException 
     */
    public void closeService() throws IOException
    {
        if(isAlive)
        {
            if(!serverSocket.isClosed())
                serverSocket.close();
            isAlive=false;
        }
    }

    public void sleepFor(int milis)
    {
        try{
            sleep(milis);
        }catch(InterruptedException e){
            System.out.println(e);
        }
    }
}

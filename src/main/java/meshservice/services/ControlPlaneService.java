package meshservice.services;

import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import meshservice.communication.Connection;
import meshservice.communication.JsonBuilder;
import meshservice.communication.RequestException;

/**
 * Base class for control plane services.
 * 
 * @author ArtiFixal
 */
public abstract class ControlPlaneService extends MultithreadService{

    public ControlPlaneService() throws IOException{
        super();
    }
    
    public ControlPlaneService(int port) throws IOException{
        super(port);
    }

    /**
     * Processes first client connection. Ex connection register.
     * 
     * @param clientSocket From what to process.
     * 
     * @throws IOException Any socket error occurred.
     */
    protected abstract void processFirstConnection(Socket clientSocket) throws IOException;
    
    @Override
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
    }

    @Override
    public void processSocket(Socket clientSocket){
        try{
            processFirstConnection(clientSocket);
        }catch(Exception e){
            System.out.println(e);
            e.printStackTrace();
        }
    }
    
    @Override
    protected Socket prepareSocket() throws IOException{
        final Socket clientSocket=serverSocket.accept();
        clientSocket.setKeepAlive(true);
        return clientSocket;
    }
}

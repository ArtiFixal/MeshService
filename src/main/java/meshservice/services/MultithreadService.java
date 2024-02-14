package meshservice.services;

import java.io.IOException;
import java.net.Socket;
import meshservice.communication.Connection;
import meshservice.communication.ConnectionThread;

/**
 * Base class for the multithreaded services.
 *
 * @author ArtiFixal
 */
public abstract class MultithreadService extends Service{

    public MultithreadService() throws IOException{
        this(0);
    }

    public MultithreadService(int port) throws IOException{
        super(port);
    }

    @Override
    public void processSocket(Socket clientSocket) throws IOException{
        Connection clientConnection=new Connection(clientSocket);
        ConnectionThread processThread=new ConnectionThread(clientConnection,this);
        processThread.start();
    }
}
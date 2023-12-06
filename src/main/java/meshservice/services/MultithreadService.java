package meshservice.services;

import java.io.IOException;
import java.net.Socket;

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
    protected void processSocket(Socket clientSocket){
        // Autoclose socket
        try(clientSocket){
            super.processSocket(clientSocket);
        }catch(Exception e){
            System.out.println(e);
            e.printStackTrace();
        }
    }

    @Override
    public void run(){
        while(isAlive){
            try{
                Socket clientSocket=prepareSocket();
                Thread t=new Thread(()->processSocket(clientSocket));
                t.start();
            }catch(IOException e){
                System.out.println(e);
            }
            sleepFor(50);
        }
    }
}

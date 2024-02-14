package meshservice.communication;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Class representing connection between client and server sockets. 
 * Uses buffered streams to reduce number of I/O operations.
 * 
 * @author ArtiFixal
 */
public class Connection implements AutoCloseable{

    /**
     * Determines if connection is alive.
     */
    protected boolean isAlive;
    
    /**
     * Socket being used by this connection.
     */
    protected Socket connectionSocket;
    
    /**
     * Stream to read from.
     */
    protected BufferedInputStream requestStream;
    
    /**
     * Stream to write into.
     */
    protected BufferedOutputStream responseStream;
    
    public Connection(Socket sockedUsed) throws IOException{
        isAlive=true;
        this.connectionSocket=sockedUsed;
        requestStream=new BufferedInputStream(connectionSocket.getInputStream());
        responseStream=new BufferedOutputStream(connectionSocket.getOutputStream());
    }

    public Socket getConnectionSocket(){
        return connectionSocket;
    }

    public BufferedInputStream getRequestStream(){
        return requestStream;
    }

    public BufferedOutputStream getResponseStream(){
        return responseStream;
    }
    
    /**
     * Starts shuting down of this socket. Use {@link #closeSocket()} to finish 
     * shutting down.
     * 
     * @throws IOException Any socket error occurred.
     */
    protected void shutdown() throws IOException{
        connectionSocket.shutdownInput();
        isAlive=false;
    }
    
    /**
     * Closes this connection socket and its buffers.
     * 
     * @throws IOException Any socket error occurred.
     */
    protected void closeSocket() throws IOException{
        requestStream.close();
        responseStream.close();
        connectionSocket.close();
    }

    public boolean isAlive(){
        return isAlive;
    }
    
    public JsonReader sendRequest(JsonBuilder request) throws IOException,RequestException{
        responseStream.write(request.toBytes());
        responseStream.flush();
        return new JsonReader(requestStream);
    }

    @Override
    public void close() throws IOException{
        if(isAlive)
        {
            connectionSocket.shutdownInput();
            closeSocket();
            isAlive=false;
        }
    }
}

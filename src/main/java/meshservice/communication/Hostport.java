package meshservice.communication;

/**
 * Class responsible for storing <b>URL</b> hostport (both host and port).
 * 
 * @author ArtiFixal
 */
public class Hostport {
    public String host;
    public int port;

    public Hostport(int port) {
        host="localhost";
        this.port=port;
    }
    
    public Hostport(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return host+":"+Integer.toString(port);
    }
}

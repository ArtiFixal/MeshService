package meshservice.services;

import java.io.BufferedInputStream;
import java.io.IOException;
import meshservice.communication.RequestException;
import meshservice.communication.JsonReader;
import meshservice.communication.JsonBuilder;

public class MicroService1 extends Service{
	
	public MicroService1() throws IOException{
		super();
	}
	
	public MicroService1(int port) throws IOException{
		super(port);
	}
	
    public String reverse(String message) {
        StringBuilder reverseMessage = new StringBuilder(message);
        reverseMessage.reverse();
        return reverseMessage.toString();
    }
	
	@Override
	public void processRequest(BufferedInputStream request,JsonBuilder response) throws IOException,RequestException{
		final JsonReader reader=new JsonReader(request);
		String message=reader.readString("message");
		message=reverse(message);
		response.setStatus(message,200);
	}
}

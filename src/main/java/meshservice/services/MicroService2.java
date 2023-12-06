package meshservice.services;

import java.io.BufferedInputStream;
import java.io.IOException;
import meshservice.communication.RequestException;
import meshservice.communication.JsonReader;
import meshservice.communication.JsonBuilder;


public class MicroService2 extends Service{

	public MicroService2() throws IOException{
		super();
	}
	
	public MicroService2(int port) throws IOException{
		super(port);
	}
	
    public static String toUpperCase(String message) {
        return message.toUpperCase();
    }

	@Override
	public void processRequest(BufferedInputStream request,JsonBuilder response) throws IOException,RequestException{
		final JsonReader reader=new JsonReader(request);
		String message=reader.readString("message");
		message=toUpperCase(message);
		response.setStatus(message,200);
	}
}

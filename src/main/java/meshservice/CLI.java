package meshservice;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import meshservice.communication.JsonReader;
import meshservice.communication.JsonBuilder;
import meshservice.communication.RequestException;

/**
 * Command line interface used by client to connect to the API Gateway.
 * 
 * @author ArtiFixal
 * @author ApolLuck
 */
public class CLI {
    public static final String API_GATEWAY_HOSTNAME = "localhost";
    private boolean loop;

    public CLI() throws IOException {
        loop = true;
    }

    public void actionLoop() throws IOException, RequestException, ReflectiveOperationException {
        while (loop) {
            final JsonBuilder jsonRequest = new JsonBuilder();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Choose option:");
            System.out.println("1. Microservice 1 (invert string)");
            System.out.println("2. Microservice 2 (uppercase string)");
            System.out.println("3. Exit");
            String choice = reader.readLine();
            switch (choice) {
                case "1" -> {
                    System.out.println("Enter string to invert:");
                    jsonRequest.addField("action", "invert");
                }
                case "2" -> {
                    System.out.println("Enter string to uppercase:");
                    jsonRequest.addField("action", "uppercase");
                }
                case "3" -> {
                    System.out.println("Exiting...");
                    loop = false;
                    return;
                }
                default -> {
                    System.out.println("Invalid option. Choose 1, 2 or 3.");
                    continue;
                }
            }
            try(Socket serviceSocket = new Socket("localhost", 10000)){
                String userInput = reader.readLine();
                jsonRequest.addField("message", userInput);
                // Send request to API Gateway
                BufferedOutputStream toAPI = new BufferedOutputStream(serviceSocket.getOutputStream());
                toAPI.write(jsonRequest.toBytes());
                toAPI.flush();
                // Receive response from API Gateway
                JsonReader response = new JsonReader(serviceSocket.getInputStream());
                int status = response.readNumber("status", Integer.class);
                //String responseMsg = response.readString("responseText");
                if (status == 200)
                    System.out.println("Odpowiedź od serwisu: " + response.getRequestNode().toPrettyString());
                else {
                    System.out.println("Wystąpił błąd: " + response);
                    System.out.println("Kod błędu: " + status);
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        CLI cli = new CLI();
        try {
            cli.actionLoop();
        } catch (Exception e) {
            System.out.println("[client error]: " + e);
            e.printStackTrace();
        }
    }
}

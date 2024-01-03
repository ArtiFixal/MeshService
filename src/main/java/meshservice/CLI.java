package meshservice;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

import meshservice.communication.JsonReader;
import meshservice.communication.JsonBuilder;
import meshservice.communication.RequestException;

/**
 * Command line interface used by client to connect to the API Gateway.
 *
 * @author ArtiFixal
 * @author ApolLuck
 * @author RGeresz
 */
public class CLI {
    public static final String API_GATEWAY_HOSTNAME = "localhost";
    private boolean loop;

    private long userID;

    public CLI() throws IOException {
        loop = true;
        userID = -1;
    }

    public void actionLoop() throws IOException, RequestException {
        while (loop) {
            final JsonBuilder jsonRequest = new JsonBuilder();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Choose option:");
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.println("3. Add post");
            System.out.println("4. Get posts");
            System.out.println("5. Upload file");
            System.out.println("6. Download file");
            System.out.println("7. Exit");
            String choice = reader.readLine();
            switch (choice) {
                case "1" -> {
                    jsonRequest.addField("action", "login");
                    System.out.println("Enter login:");
                    jsonRequest.addField("login", reader.readLine());
                    System.out.println("Enter password:");
                    jsonRequest.addField("password", reader.readLine());
                }
                case "2" -> {
                    jsonRequest.addField("action", "register");
                    System.out.println("Enter login:");
                    jsonRequest.addField("login", reader.readLine());
                    System.out.println("Enter password:");
                    jsonRequest.addField("password", reader.readLine());
                }
                case "3" -> {
                    if (userID == -1) {
                        System.out.println("You need to login first.");
                        continue;
                    }
                    jsonRequest.addField("action", "addPost");
                    jsonRequest.addField("ownerID", userID);
                    System.out.println("Enter post content:");
                    jsonRequest.addField("content", reader.readLine());
                }
                case "4" -> {
                    if (userID == -1) {
                        System.out.println("You need to login first.");
                        continue;
                    }
                    jsonRequest.addField("action", "getPosts");
                    jsonRequest.addField("ownerID", userID);
                }
                case "5" -> {
                    if (userID == -1) {
                        System.out.println("You need to login first.");
                        continue;
                    }
                    jsonRequest.addField("action", "uploadFile");
                    jsonRequest.addField("ownerID", userID);
                    System.out.println("Enter path to file:");
                    String path = reader.readLine();
                    jsonRequest.addField("filename", path.substring(path.lastIndexOf('/') + 1));
                    byte[] file = Files.readAllBytes(Paths.get(path));
                    jsonRequest.addField("file", Base64.getEncoder().encodeToString(file));
                }
                case "6" -> {
                    if (userID == -1) {
                        System.out.println("You need to login first.");
                        continue;
                    }
                    jsonRequest.addField("action", "getFile");
                    jsonRequest.addField("ownerID", userID);
                    System.out.println("Enter filename:");
                    jsonRequest.addField("filename", reader.readLine());
                }
                default -> {
                    System.out.println("Invalid option.");
                    continue;
                }
            }
            try (Socket serviceSocket = new Socket("localhost", 10001)) {
                // Send request to API Gateway
                BufferedOutputStream toAPI = new BufferedOutputStream(serviceSocket.getOutputStream());
                toAPI.write(jsonRequest.toBytes());
                toAPI.flush();
                // Receive response from API Gateway
                JsonReader response = new JsonReader(serviceSocket.getInputStream());
                int status = response.readNumber("status", Integer.class);
                if (status == 200) {
                    switch (choice) {
                        case "1" -> {
                            userID = response.readNumber("userID", Long.class);
                            System.out.println("Logged in successfully.");
                        }
                        case "2" -> System.out.println("Registered successfully.");
                        case "3" -> System.out.println("Post added successfully.");
                        case "4" -> {
                            System.out.println("Posts:");
                            // TODO: print posts
                        }
                        case "5" -> System.out.println("File uploaded successfully.");
                        case "6" -> {
                            byte[] file = Base64.getDecoder().decode(response.readString("file"));
                            Files.write(Paths.get(response.readString("filename")), file);
                            System.out.println("File downloaded successfully.");
                        }
                    }
                } else {
                    System.out.println("Error code: " + status);
                    System.out.println("Description " + response.readString("responseText"));
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

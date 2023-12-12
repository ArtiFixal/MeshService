package meshservice.services;

import meshservice.communication.JsonBuilder;
import meshservice.communication.JsonReader;
import meshservice.communication.RequestException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * This class provides services for file operations such as uploading and downloading files.
 * It extends the Service class and overrides the processRequest method to handle file-specific requests.
 *
 * @author RGeresz
 */
public class FileService extends Service {
    /**
     * Default constructor that initializes the FileService with a default port.
     * @throws IOException if an I/O error occurs when opening the socket.
     */
    public FileService() throws IOException {
        this(0);
    }

    /**
     * Constructor that initializes the FileService with a specific port.
     * @param port The port number.
     * @throws IOException if an I/O error occurs when opening the socket.
     */
    public FileService(int port) throws IOException {
        super(port);
    }

    /**
     * Processes file-specific requests. Depending on the action specified in the request,
     * it either uploads a file to the server or downloads a file from the server.
     * @param request The incoming request.
     * @param response The response to be sent back.
     * @throws IOException if an I/O error occurs.
     * @throws RequestException if the request cannot be processed.
     */
    @Override
    public void processRequest(BufferedInputStream request, JsonBuilder response) throws IOException, RequestException {
        final JsonReader reader = new JsonReader(request);
        final String action = reader.readString("action");
        response.addField("action", action);
        final Path path = Paths.get(reader.readNumber("ownerID", Long.class) + "/" + reader.readString("filename"));
        try {
            switch (action) {
                // If the action is "getFile", read the file from the specified path and add it to the response.
                case "getFile" -> {
                    byte[] file = Files.readAllBytes(path);
                    response.addField("file", Base64.getEncoder().encodeToString(file));
                    response.setStatus("File downloaded successfully", 200);
                }
                // If the action is "uploadFile", write the file to the specified path.
                case "uploadFile" -> {
                    byte[] file = Base64.getDecoder().decode(reader.readString("file"));
                    Files.write(path, file);
                    response.setStatus("File uploaded successfully", 200);
                }
                // If the action is not supported, throw a RequestException.
                default -> throw new RequestException("Unsupported method");
            }
        } catch (IOException e) {
            response.setStatus("An error occurred during processing file request: " + e.getMessage(), 500);
        }
    }
}
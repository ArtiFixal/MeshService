package meshservice.services;

import meshservice.communication.JsonBuilder;
import meshservice.communication.JsonReader;
import meshservice.communication.RequestException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * This class provides services for file operations such as uploading and downloading files.
 * It extends the Service class and overrides the processRequest method to handle file-specific requests.
 *
 * @author RGeresz
 */
public class FileDownloadService extends Service {
    public static final String[] REQUEST_REQUIRED_FIELDS=new String[]{"action","ownerID","filename"};
    
    /**
     * Root directory where user files are stored.
     */
    private File filesRootDirectory;
    
    /**
     * Default constructor that initializes the FileService with a default port.
     * @throws IOException if an I/O error occurs when opening the socket.
     */
    public FileDownloadService() throws IOException {
        this(0);
    }

    /**
     * Constructor that initializes the FileService with a specific port.
     * @param port The port number.
     * @throws IOException if an I/O error occurs when opening the socket.
     */
    public FileDownloadService(int port) throws IOException {
        super(port);
        filesRootDirectory=new File("UserData");
    }

    @Override
    public String[] getRequiredRequestFields(){
        return REQUEST_REQUIRED_FIELDS;
    }
    
    @Override
    public String[] getAdditionalResponseFields(){
        return new String[]{"file", "filename"};
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
    public void processRequest(InputStream request, JsonBuilder response) throws IOException, RequestException {
        final JsonReader reader = new JsonReader(request);
        final String action = reader.readString("action");
        final File userDirectory=new File(filesRootDirectory+"/"+reader.readString("ownerID"));
        if(!userDirectory.exists())
            throw new RequestException("User has no files");
        final Path path = userDirectory.toPath().resolve(reader.readString("filename"));
        if(!path.toFile().exists())
            throw new RequestException("Given file doesn't exist");
        try {
            switch (action) {
                // If the action is "getFile", read the file from the specified path and add it to the response.
                case "getFile" -> {
                    byte[] file = Files.readAllBytes(path);
                    response.addField("file", Base64.getEncoder().encodeToString(file));
                    response.addField("filename", path.getFileName().toString());
                    response.setStatus("File downloaded successfully", 200);
                }
                // If the action is not supported, throw a RequestException.
                default -> throw new RequestException("Unsupported method");
            }
        } catch (IOException e) {
            response.setStatus("An error occurred during processing file request: " + e.getMessage(), 500);
        }
    }

}
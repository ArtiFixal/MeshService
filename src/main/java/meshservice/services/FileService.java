package meshservice.services;

import meshservice.communication.JsonBuilder;
import meshservice.communication.JsonReader;
import meshservice.communication.RequestException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

public class FileService extends Service {
    public FileService() throws IOException {
        this(0);
    }

    public FileService(int port) throws IOException {
        super(port);
    }

    @Override
    public void processRequest(BufferedInputStream request, JsonBuilder response) throws IOException, RequestException {
        final JsonReader reader = new JsonReader(request);
        final String action = reader.readString("action");
        response.addField("action", action);
        try {
            switch (action) {
                case "getFile" -> {
                    String path = reader.readNumber("ownerID", Long.class) + "/" + reader.readString("filename");
                    byte[] file = Files.readAllBytes(Paths.get(path));
                    response.addField("file", Base64.getEncoder().encodeToString(file));
                    response.setStatus("File downloaded successfully", 200);
                }
                case "uploadFile" -> {
                    String path = reader.readNumber("ownerID", Long.class) + "/" + reader.readString("filename");
                    byte[] file = Base64.getDecoder().decode(reader.readString("file"));
                    Files.write(Paths.get(path), file);
                    response.setStatus("File uploaded successfully", 200);
                }
                default -> throw new RequestException("Unsupported method");
            }
        } catch (IOException e) {
            response.setStatus("An error occurred during processing file request: " + e.getMessage(), 500);
        }
    }
}

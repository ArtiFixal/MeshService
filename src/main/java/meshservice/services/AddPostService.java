package meshservice.services;

import meshservice.communication.JsonBuilder;
import meshservice.communication.JsonReader;
import meshservice.communication.RequestException;
import meshservice.communication.daos.PostDAO;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

/**
 * This class extends the Service class and provides functionality to add posts.
 * It uses the PostDAO to interact with the database.
 *
 * @author RGeresz
 */
public class AddPostService extends Service {
    public static final String[] REQUEST_REQUIRED_FIELDS=new String[]{"action","ownerID","content"};
    
    private PostDAO dao;

    /**
     * Default constructor that creates an AddPostService listening on a random port.
     * @throws IOException if an I/O error occurs when opening the socket.
     * @throws SQLException if a database access error occurs.
     */
    public AddPostService() throws IOException, SQLException {
        this(0);
    }

    /**
     * Constructor that creates an AddPostService listening on the specified port.
     * @param port The port to listen on.
     * @throws IOException if an I/O error occurs when opening the socket.
     * @throws SQLException if a database access error occurs.
     */
    public AddPostService(int port) throws IOException, SQLException {
        super(port);
        dao = new PostDAO();
    }

    @Override
    public String[] getRequiredRequestFields(){
        return REQUEST_REQUIRED_FIELDS;
    }
    
    @Override
    public String[] getAdditionalResponseFields(){
        return EMPTY_ARRAY;
    }

    /**
     * Processes a request to add a post. The request is read from the provided BufferedInputStream,
     * and the response is written to the provided JsonBuilder.
     * @param request The BufferedInputStream to read the request from.
     * @param response The JsonBuilder to write the response to.
     * @throws IOException if an I/O error occurs.
     * @throws RequestException if the request cannot be processed.
     */
    @Override
    public void processRequest(InputStream request, JsonBuilder response) throws IOException, RequestException {
        final JsonReader reader = new JsonReader(request);
        final String action = reader.readString("action");
        response.addField("action", action);
        long ownerID = reader.readNumber("ownerID", Long.class);
        String content = reader.readString("content");
        try {
            if (action.equals("addPost")) {
                if (dao.insertPost(ownerID, content) != -1)
                    response.setStatus("Post added successfully", 200);
            } else {
                throw new RequestException("Unsupported method");
            }
        } catch (SQLException e) {
            response.clear();
            response.setStatus("An error occurred during processing DB request: "
                    + e.getMessage(), 500);
        }
    }
}
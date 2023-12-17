package meshservice.services;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.sql.SQLException;
import meshservice.User;
import meshservice.communication.daos.UserDAO;
import meshservice.communication.RequestException;
import meshservice.communication.JsonReader;
import meshservice.communication.JsonBuilder;

/**
 * Service responsible for user authentication.
 *
 * @author ArtiFixal
 * @author ApolLuck
 */
public class UserAuthenticationService extends Service{
    public static final String[] REQUEST_REQUIRED_FIELDS=new String[]{"action","login","password"};
    
    protected UserDAO dao;

    public UserAuthenticationService(int port) throws IOException,SQLException{
        super(port);
        dao=new UserDAO();
    }

    @Override
    public String[] getRequiredRequestFields(){
        return REQUEST_REQUIRED_FIELDS;
    }

    @Override
    public void processRequest(BufferedInputStream request,JsonBuilder response) throws IOException,RequestException
    {
        final JsonReader reader=new JsonReader(request);
        final String action=reader.readString("action");
        response.addField("action",action);
        String login=reader.readString("login"),
                password=reader.readString("password");
        try{
            if(action.equals("login"))
            {
                User loggedIn=dao.loginAs(login,password);
                if(loggedIn!=null)
                {
                    int apiKey=loggedIn.hashCode();
                    response.addField("userID",loggedIn.getId())
                            .addField("apiKey",apiKey)
                            .setStatus(200);
                }else{
                    throw new RequestException(400,"There is no user with given credentials");
                }
            }else{
                throw new RequestException("Unsuported method");
            }
        }catch(SQLException e){
            response.clear();
            response.setStatus("An error occured during processing DB request: "
                    +e.getMessage(),HttpURLConnection.HTTP_INTERNAL_ERROR);
        }
    }
}

package meshservice.services;

import java.io.IOException;
import java.io.InputStream;
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
    public String[] getAdditionalResponseFields(){
        return new String[]{"userID","apiKey"};
    }

    @Override
    public void processRequest(InputStream request,JsonBuilder response) throws IOException,RequestException,SQLException
    {
        final JsonReader reader=new JsonReader(request);
        final String action=reader.readString("action");
        response.addField("action",action);
        String login=reader.readString("login"),
                password=reader.readString("password");
        if(action.equals("login"))
        {
            User loggedIn=dao.loginAs(login,password);
            if(loggedIn!=null)
            {
                int apiKey=loggedIn.hashCode();
                response.addField("userID",loggedIn.getId())
                    .addField("apiKey",apiKey)
                    .setStatus("User logged in successfully",200);
            }
            else
                throw new RequestException("There is no user with given credentials");
        }
        else
            throw new RequestException("Unsuported method");
    }
}

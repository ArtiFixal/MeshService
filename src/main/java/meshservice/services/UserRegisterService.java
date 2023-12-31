package meshservice.services;

import meshservice.communication.JsonBuilder;
import meshservice.communication.JsonReader;
import meshservice.communication.RequestException;
import meshservice.communication.daos.UserDAO;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Service responsible for registering new users.
 *
 * @author ApolLuck
 */
public class UserRegisterService extends Service{
    public static final String[] REQUEST_REQUIRED_FIELDS=new String[]{"action","login","password"};
    
    protected UserDAO dao;

    public UserRegisterService(int port) throws IOException,SQLException{
        super(port);
        dao=new UserDAO();
    }

    @Override
    public String[] getRequiredRequestFields(){
        return REQUEST_REQUIRED_FIELDS;
    }
    
    @Override
    public String[] getAdditionalResponseFields(){
        return EMPTY_ARRAY;
    }

    @Override
    public void processRequest(BufferedInputStream request,JsonBuilder response) throws IOException,RequestException,SQLException{
        final JsonReader reader=new JsonReader(request);
        final String action=reader.readString("action");
        response.addField("action",action);
        String login=reader.readString("login"),
                password=reader.readString("password");
        if(action.equals("register"))
        {
            if(dao.insertUser(login,password)==-1)
                throw new RequestException("User already exists");
            response.setStatus("User registered successfuly",200);
        }
        else
            throw new RequestException("Unsuported method");
    }
}

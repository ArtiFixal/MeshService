package meshservice.services;

import meshservice.communication.JsonBuilder;
import meshservice.communication.JsonReader;
import meshservice.communication.RequestException;
import meshservice.communication.daos.UserDAO;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.sql.SQLException;

/**
 * Service responsible for registering new users.
 *
 * @author ApolLuck
 */
public class UserRegisterService extends Service{

    protected UserDAO dao;

    public UserRegisterService(int port) throws IOException,SQLException{
        super(port);
        dao=new UserDAO();
    }

    @Override
    public void processRequest(BufferedInputStream request,JsonBuilder response) throws IOException,RequestException{
        final JsonReader reader=new JsonReader(request);
        final String action=reader.readString("action");
        response.addField("action",action);
        String login=reader.readString("login"),
                password=reader.readString("password");
        try{
            if(action.equals("register"))
            {
                if(dao.insertUser(login,password)!=-1)
                    response.setStatus("User registered successfuly",200);
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

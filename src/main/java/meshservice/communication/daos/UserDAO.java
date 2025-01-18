package meshservice.communication.daos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import meshservice.User;

/**
 * Class responsible for operation on <b>user</b> DB table.
 *
 * @author ArtiFixal
 */
public class UserDAO extends DAOObject{

    public UserDAO() throws SQLException{
        super();
    }

    public UserDAO(Connection con) throws SQLException{
        super(con);
    }

    /**
     * Inserts new user to the DB.
     * 
     * @param login New user login.
     * @param publicKey User public key.
     * 
     * @return ID of inserted user or -1 if already exists.
     * 
     * @throws SQLException Any error occurred during the DML query.
     */
    public long insertUser(String login,byte[] publicKey) throws SQLException
    {
        PreparedStatement existanceCheck=con.prepareStatement("SELECT id FROM users WHERE username=?");
        existanceCheck.setString(1,login);
        if(!existanceCheck.executeQuery().next())
        {
            try(PreparedStatement insertStatement=con.prepareStatement("INSERT INTO users VALUES(NULL,?,?)"))
            {
                insertStatement.setString(1,login);
                insertStatement.setBytes(2,publicKey);
                insertStatement.execute();
                return getLastInsertedId();
            }
        }
        return -1;
    }

    /**
     * Looks for user with given login in DB.
     * 
     * @param login User login.
     * 
     * @return Found user, null otherwise.
     * 
     * @throws SQLException Any error occurred during the DML query.
     */
    public User findUserByLogin(String login) throws SQLException
    {
        PreparedStatement selectUser=con.prepareStatement("SELECT id,publicKey FROM users WHERE username=?");
        selectUser.setString(1,login);
        ResultSet user=selectUser.executeQuery();
        if(user.next())
        {
            byte[] key=user.getBytes(2);
            return new User(user.getLong(1),login,key);
        }
        return null;
    }

    /**
     * Changes given user public key.
     * 
     * @param userID Whom to change password.
     * @param oldKey Current user key.
     * @param newKey To what to change.
     * 
     * @return True on success, false otherwise.
     * 
     * @throws SQLException Any error occurred during the DML query.
     */
    public boolean changeKey(long userID,byte[] oldKey,byte[] newKey) throws SQLException
    {
        ResultSet user=getElementByID("publicKey","users",userID);
        if(user.next())
        {
            byte[] currentKey=user.getBytes(1);
            if(currentKey==oldKey)
            {
                PreparedStatement updatePassword=con.prepareCall(createUpdateQuery("users","id="+userID,"publicKey"));
                updatePassword.setBytes(1,newKey);
                return updatePassword.executeUpdate()==1;
            }
        }
        return false;
    }
}

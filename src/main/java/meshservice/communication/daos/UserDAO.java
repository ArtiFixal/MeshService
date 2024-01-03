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
     * @param password User password.
     * 
     * @return ID of inserted user or -1 if already exists.
     * 
     * @throws SQLException Any error occurred during the DML query.
     */
    public long insertUser(String login,String password) throws SQLException
    {
        PreparedStatement existanceCheck=con.prepareStatement("SELECT id FROM users WHERE username=?");
        existanceCheck.setString(1,login);
        if(!existanceCheck.executeQuery().next())
        {
            try(PreparedStatement insertStatement=con.prepareStatement("INSERT INTO users VALUES(NULL,?,?)"))
            {
                insertStatement.setString(1,login);
                String hash=hashPassword(password);
                insertStatement.setString(2,hash);
                insertStatement.execute();
                return getLastInsertedId();
            }
        }
        return -1;
    }

    /**
     * Authenticates given user if password matches.
     * 
     * @param login User login.
     * @param password User password.
     * 
     * @return Logged-in user data.
     * 
     * @throws SQLException Any error occurred during the DML query.
     */
    public User loginAs(String login,String password) throws SQLException
    {
        PreparedStatement selectUser=con.prepareStatement("SELECT id,password FROM users WHERE username=?");
        selectUser.setString(1,login);
        ResultSet user=selectUser.executeQuery();
        if(user.next())
        {
            String passwordHash=user.getString(2);
            if(passwordHash.equals(hashPassword(password)))
                return new User(user.getLong(1),login);
        }
        return null;
    }

    /**
     * Changes given user password.
     * 
     * @param userID Whom to change password.
     * @param oldPassword From what to change.
     * @param newPassword To what to change.
     * 
     * @return True on success, false otherwise.
     * 
     * @throws SQLException Any error occurred during the DML query.
     */
    public boolean changePassword(long userID,String oldPassword,String newPassword) throws SQLException
    {
        ResultSet user=getElementByID("password","users",userID);
        if(user.next())
        {
            String passwordHash=user.getString(1);
            if(passwordHash.equals(hashPassword(oldPassword)))
            {
                PreparedStatement updatePassword=con.prepareCall(createUpdateQuery("users","id="+userID,"password"));
                updatePassword.setString(1,newPassword);
                return updatePassword.executeUpdate()==1;
            }
        }
        return false;
    }

    private String hashPassword(String password){
        return password;
    }
}

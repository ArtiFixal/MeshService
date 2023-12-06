package meshservice.config;

import com.mysql.cj.jdbc.Driver;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Singleton which stores database config.
 *
 * @author ArtiFixal
 */
public class DBConfig{

    /**
     * File where DB conifg is saved.
     */
    private final static File DB_CONFIG=new File("dbConfig.cfg");

    /**
     * Stores URL to DB.
     */
    private final String dbUrl;

    /**
     * Stores user login.
     */
    private final String dbUser;

    /**
     * Stores user password.
     */
    private final String dbPass;

    /**
     * Singleton instance of config.
     */
    private static DBConfig config;

    private DBConfig() throws IOException,FileNotFoundException,
            NullPointerException,OptionNotFoundException
    {
        ConfigIO configFile=new ConfigIO(DB_CONFIG);
        // jdbc:mysql://{URL}/{BD_NAME}
        dbUrl="jdbc:mysql://"+configFile.readOptionValue("<dbURL>")+"/"
                +configFile.readOptionValue("<dbName>");
        dbUser=configFile.readOptionValue("<dbUser>");
        dbPass=configFile.readOptionValue("<dbPass>");
    }

    public static DBConfig getInstance()
    {
        if(config==null)
        {
            try{
                config=new DBConfig();
            }catch(FileNotFoundException e){
                // Create example config
                synchronized(DB_CONFIG){
                    // Leave if already created by previous sync enter
                    if(DB_CONFIG.exists()){
                        return config;
                    }
                    String configBuilder="<dbURL>127.0.0.1:3306\n<dbName>reibursements\n<dbUser>root\n<dbPass>\n";
                    ConfigIO w=new ConfigIO(DB_CONFIG);
                    try{
                        w.writeEntireConfig(configBuilder);
                    }catch(IOException ex){
                        //Logger.getLogger(DBConfig.class.getName()).log(Level.SEVERE,null,ex);
                    }
                    try{
                        config=new DBConfig();
                    }catch(Exception ex){
                        // Abadon all hope, ye who enter here.
                    }
                }
            }catch(OptionNotFoundException e){
                throw new RuntimeException("Unable to continue - DB config is "
                        +"incomplete: "+e.getMessage());
            }catch(IOException e){
                throw new RuntimeException("Unable to continue - An error "
                        +"occured during reading of DB config: "+e.getMessage());
            }
            // Create driver instance
            try{
                Driver d=new Driver();
            }catch(SQLException e){
                System.out.println(e);
            }
        }
        return config;
    }

    /**
     * Creates connection with DB.
     *
     * @return Connection to the DB.
     * @throws SQLException Any error occurred during connection validation.
     */
    public Connection createConnection() throws SQLException{
        return DriverManager.getConnection(dbUrl,dbUser,dbPass);
    }
}

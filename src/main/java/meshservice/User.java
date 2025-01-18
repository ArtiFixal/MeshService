package meshservice;

/**
 * Class representing DB user record without hash.
 * 
 * @author ArtiFixal
 */
public class User {
    private final long id;
    public String login;
    
    /**
     * Base64 encrypted key.
     */
    private byte[] publicKey;

    public User(long id, String login,byte[] publicKey) {
        this.id = id;
        this.login = login;
        this.publicKey=publicKey;
    }

    public long getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    /**
     * @return Base64 encrypted key.
     */
    public byte[] getPublicKey(){
        return publicKey;
    }
}

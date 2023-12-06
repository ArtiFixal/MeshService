package meshservice;

/**
 * Class representing DB user record without hash.
 * 
 * @author ArtiFixal
 */
public class User {
    private final long id;
    public String login;

    public User(long id, String login) {
        this.id = id;
        this.login = login;
    }

    public long getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }
}

package meshservice;

import java.sql.Timestamp;

/**
 * Class representing DB user post record.
 * 
 * @author ApolLuck
 */
public class Post {
    private final long id;
    private final long ownerId;
    public String content;
    public Timestamp whenCreated;
    
    public Post(long id,long ownerId,String content,Timestamp whenCreated) {
        this.id = id;
        this.ownerId = ownerId;
        this.content = content;
        this.whenCreated=whenCreated;
    }
    
    public long getId() {
        return id;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Timestamp getWhenCreated(){
        return whenCreated;
    }
}

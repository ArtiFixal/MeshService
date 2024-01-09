package meshservice;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.sql.Timestamp;

/**
 * Class representing DB user post record.
 * 
 * @author ApolLuck
 */
public class Post {
    @JsonProperty("id")
    private final long id;
    @JsonProperty("ownerID")
    private final long ownerId;
    @JsonProperty("content")
    public String content;
    @JsonProperty("created")
    public Timestamp whenCreated;
    
    @JsonCreator
    public Post(@JsonProperty("id") long id,
            @JsonProperty("ownerID") long ownerId,
            @JsonProperty("content") String content,
            @JsonProperty("created") Timestamp whenCreated)
    {
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

    @Override
    public String toString(){
        return id+" "+ownerId+" "+content+" "+whenCreated;
    }
}

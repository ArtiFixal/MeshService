package meshservice.communication.daos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import meshservice.Post;

/**
 * Class responsible for operations on <b>post</b> DB table.
 *
 * @author ApolLuck
 */
public class PostDAO extends DAOObject{

    public PostDAO() throws SQLException{
        super();
    }

    public PostDAO(Connection con) throws SQLException{
        super(con);
    }

    /**
     * Inserts new post to the DB.
     *
     * @param ownerID Whose this post is.
     * @param content What this post contains.
     *
     * @return ID of inserted post or -1 if failed to insert.
     *
     * @throws SQLException Any error occurred during the DML query.
     */
    public long insertPost(long ownerID,String content) throws SQLException
    {
        try(PreparedStatement insertStatement=con.prepareStatement("INSERT INTO posts (ownerID, content) VALUES (?, ?)")){
            insertStatement.setLong(1,ownerID);
            insertStatement.setString(2,content);
            return insertStatement.executeUpdate()==1?getLastInsertedId():-1;
        }
    }

    /**
     * Gets 10 most recent posts of given user.
     *
     * @param ownerID Whose posts to get.
     *
     * @return Most recent posts.
     *
     * @throws SQLException Any error occurred during the DML query.
     */
    public ArrayList<Post> getRecentPosts(long ownerID) throws SQLException
    {
        ArrayList<Post> recentPosts=new ArrayList<>();
        try(PreparedStatement selectStatement=con.prepareStatement("SELECT * FROM posts WHERE ownerID=? ORDER BY created DESC LIMIT 10")){
            selectStatement.setLong(1,ownerID);
            ResultSet resultSet=selectStatement.executeQuery();
            while(resultSet.next())
            {
                int postId=resultSet.getInt("id");
                int ownerId=resultSet.getInt("ownerID");
                String content=resultSet.getString("content");
                Timestamp created=resultSet.getTimestamp("created");
                Post post=new Post(postId,ownerId,content,created);
                recentPosts.add(post);
            }
        }
        return recentPosts;
    }
}

package meshservice.communication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * Class responsible for building JSON.
 *
 * @author ArtiFixal
 */
public class JsonBuilder{

    public static final ObjectMapper mapper=new ObjectMapper();
    public ObjectNode json;

    public JsonBuilder(){
        json=mapper.createObjectNode();
    }

    public JsonBuilder(String action){
        this();
        json.put("action",action);
    }

    public JsonBuilder(JsonReader request)throws RequestException,ReflectiveOperationException{
        this(request.readString("action"),request.readNumber("messageID",Long.class));
    }

    public JsonBuilder(JsonNode node){
        json=(ObjectNode)node;
    }

    public JsonBuilder(String action,long messageID){
        this();
        json.put("action",action);
        json.put("messageID",messageID);
    }

    public ObjectNode getJson(){
        return json;
    }

    public JsonBuilder setNode(String field,JsonNode node)
    {
        json.set(field,node);
        return this;
    }

    public <T> JsonBuilder addField(String field,T value)
    {
        return addField(field,String.valueOf(value));
    }

    public JsonBuilder addField(String field,String value)
    {
        json.put(field,value);
        return this;
    }

    private <T> JsonBuilder setField(String fieldName,T value)
    {
        final JsonNode statusNode=mapper.createObjectNode()
            .put(fieldName,String.valueOf(value));
        json.set(fieldName,statusNode.get(fieldName));
        return this;
    }

    public <T> JsonBuilder addArray(String fieldName,T[] array)
    {
        final ArrayNode arrayNode=json.putArray(fieldName);
        for(T element:array){
            arrayNode.add(String.valueOf(element));
        }
        return this;
    }

    public JsonBuilder addArray(String fieldName,Collection coll)
    {
        final ArrayNode arrayNode=json.putArray(fieldName);
        coll.forEach((element)->{
            arrayNode.add(String.valueOf(element));
        });
        return this;
    }

    public JsonBuilder setStatus(int status)
    {
        return setField("status",status);
    }

    public JsonBuilder setStatus(String message,int status)
    {
        return setStatus(status).setField("responseText",message);
    }

    public JsonBuilder setStatus(RequestException error)
    {
        return setStatus(error.getMessage(),error.getResponseStatus());
    }

    public byte[] toBytes()
    {
        return toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Clears all fields except <b>action</b> and <b>messageID</b>.
     */
    public void clear()
    {
        json.retain("action","messageID");
    }

    /**
     * Clears all fields.
     */
    public void clearAll()
    {
        json.removeAll();
    }

    @Override
    public String toString(){
        return json.toString();
    }
}

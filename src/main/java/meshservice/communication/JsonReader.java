package meshservice.communication;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.Callable;

/**
 * Class responsible for reading values from JSON.
 *
 * @author ArtiFixal
 */
public class JsonReader{

    /**
     * Default BigDecimal precision.
     */
    public final static MathContext DEFAULT_PRECISION=new MathContext(2,RoundingMode.HALF_UP);

    /**
     * Default prefix of exception messages related to JSON.
     */
    private final static String PREFIX="Malformed JSON request: ";

    /**
     * Mapper used to map values to Java Objects.
     */
    private final static ObjectMapper mapper=new ObjectMapper().configure(JsonParser.Feature.AUTO_CLOSE_SOURCE,false);

    /**
     * JSON containing request params.
     */
    private JsonNode requestNode;

    /**
     * Reads {@link #requestNode} from {@code InputStream}
     *
     * @param requestInputStream InputStream containing JSON.
     * @throws RequestException
     */
    public JsonReader(InputStream requestInputStream) throws RequestException
    {
        try{
            requestNode=mapper.readValue(requestInputStream,JsonNode.class);
        }catch(IOException e){
            e.printStackTrace();
            throw new RequestException(500,"An error ocurred during reading the JSON object");
        }
    }

    public JsonReader(JsonNode requestNode){
        this.requestNode=requestNode;
    }

    public JsonNode getRequestNode(){
        return requestNode;
    }

    /**
     * Retrieves non null {@code JsonNode} from {@link #requestNode}.
     *
     * @param nodeName Name of node to retrieve.
     *
     * @return Sub node of {@code requestNode}
     * @throws RequestException If subnode is null or {@code NullNode}.
     *
     * @see #requestNode
     */
    public JsonNode getNode(String nodeName) throws RequestException
    {
        JsonNode node=requestNode.get(nodeName);
        throwOnNull(node,PREFIX+nodeName+" not found");
        return node;
    }

    /**
     * Retrieves possible null node from {@code requestNode}.
     *
     * @param nodeName Name of node to retrieve.
     * @return {@code JsonNode} if it has an value, {@code NullNode} otherwise.
     */
    public JsonNode getNodeNullable(String nodeName)
    {
        try{
            return getNode(nodeName);
        }catch(RequestException e){
            return NullNode.getInstance();
        }
    }

    /**
     * Checks if given {@code JsonNode} is null or is {@code NullNode}.
     *
     * @param node Node to check.
     * @return True if {@code JsonNode} equeals null or is instace of
     * {@code NullNode}
     */
    public boolean isNull(JsonNode node){
        return (node==null||node instanceof NullNode);
    }

    /**
     * Reads non empty {@code String} from given JSON field.
     *
     * @param fieldName Name of field to read string from.
     *
     * @return Not empty String.
     * @throws RequestException If JSON field is null, {@code NullNode} or empty
     * String.
     * @see NullNode
     */
    public String readString(String fieldName) throws RequestException
    {
        JsonNode fieldNode=requestNode.get(fieldName);
        throwOnNull(fieldNode,PREFIX+fieldName+" not found");
        String requestData=fieldNode.asText().trim();
        if(requestData.isBlank())
            throw new RequestException(PREFIX+fieldName+" can't be empty");
        return requestData;
    }

    /**
     * Reads possible null {@code String} from given JSON field.
     *
     * @param fieldName Name of field to read string from.
     * @return Non empty String or null otherwise.
     */
    public String readStringNullable(String fieldName){
        return readNullable(()->readString(fieldName));
    }

    /**
     * Reads number from given field.
     *
     * @param <T> Type of Number to return.
     * @param fieldName Name of field to read number from.
     * @param classToRead What to return.
     *
     * @return Read number, never null.
     *
     * @throws RequestException If JSON field is null, instance of
     * {@code NullNode} or it is impossible to parse it from string.
     * @throws IllegalArgumentException If Class doesn't contain method
     * {@code valueOf(String)}
     */
    public <T extends Number> T readNumber(String fieldName,Class<T> classToRead)
            throws RequestException,IllegalArgumentException
    {
        String value=readString(fieldName);
        try{
            if(classToRead.equals(BigDecimal.class)||classToRead.equals(BigInteger.class))
                return classToRead.getConstructor(String.class).newInstance(value);
            // Use class static method instead
            return (T)classToRead.getMethod("valueOf",String.class)
                    .invoke(null,value);
        }catch(NumberFormatException e){
            throw new RequestException(PREFIX+fieldName+" have to be a "
                    +classToRead.getSimpleName());
        }catch(ReflectiveOperationException e){
            throw new IllegalArgumentException("Given class: "
                    +classToRead.getSimpleName()
                    +" doesn't contain method: valueOf(String)");
        }
    }

    /**
     * Reads number from given field and ensures its positive.
     *
     * @param <T> Type of Number to return.
     * @param fieldName Name of field to read number from.
     * @param classToRead What to return.
     *
     * @return Read positive number, never null.
     *
     * @throws RequestException If JSON field is null, instance of
     * {@code NullNode} or it is impossible to parse it from string.
     * @throws IllegalArgumentException If Class doesn't contain method
     * {@code valueOf(String)}
     */
    public <T extends Number> T readNumberPositive(String fieldName,Class<T> classToRead)
            throws RequestException,IllegalArgumentException
    {
        T number=readNumber(fieldName,classToRead);
        if(number.intValue()<0)
            throw new RequestException(PREFIX+fieldName+" have to be a positive");
        return number;
    }
    
    /**
     * Interface used to read single element from Json array.
     * 
     * @param <T> Type of element to read from {@code JsonNode}.
     */
    private interface ArrayNodeReader<T>{
        public T read(JsonNode node) throws ReflectiveOperationException;
    }
    
    /**
     * Reads {@code ArrayList} of elements from given JSON field.
     * 
     * @param <T> Element type.
     * @param fieldName Name of field containing array.
     * @param reader Interface instance.
     * 
     * @return Read array.
     */
    private <T> ArrayList<T> readArrayFromNode(String fieldName,ArrayNodeReader<T> reader){
        ArrayList<T> tmp=new ArrayList<>();
        JsonNode arr=requestNode.get(fieldName);
        try{
            for(int i=0;i<arr.size();i++)
                tmp.add(reader.read(arr.get(i)));
        }catch(ReflectiveOperationException e){
            throw new IllegalArgumentException("Unable to read from array cause:"+e.getMessage());
        }
        return tmp;
    }
    
    /**
     * Reads {@code ArrayList} of strings from given JSON field.
     * 
     * @param fieldName Name of field containing array.
     * @return Read array of strings.
     */
    public ArrayList<String> readArrayOf(String fieldName){
        return readArrayFromNode(fieldName,(JsonNode node)->node.asText());
    }

    /**
     * Reads {@code ArrayList} of numbers from given JSON field.
     * 
     * @param <T> Number type.
     * @param fieldName Name of field containing array.
     * @param clazz Class of number to read.
     * 
     * @return Read array of numbers.
     */
    public <T extends Number> ArrayList<T> readArrayOf(String fieldName,Class<T> clazz)
    {
        if(clazz.equals(BigDecimal.class)||clazz.equals(BigInteger.class))
            return readArrayFromNode(fieldName,(node)->clazz.getConstructor(String.class).newInstance(node.textValue()));
        return readArrayFromNode(fieldName,(node)->(T)clazz.getMethod("valueOf",String.class).invoke(null,node.asText()));
    }

    /**
     * Reads {@code LocalDate} from JSON field.
     *
     * @param fieldName Field to read.
     * @return Read date.
     *
     * @throws RequestException If unable to read date.
     */
    public LocalDate readLocalDate(String fieldName) throws RequestException
    {
        JsonNode fieldNode=requestNode.get(fieldName);
        throwOnNull(fieldNode,PREFIX+fieldName+" not found");
        return mapper.convertValue(fieldNode,LocalDate.class);
    }

    /**
     * Checks if node is null, if it is throws an exception.
     *
     * @param node What to check
     * @param errorMsg Message displayed as response.
     *
     * @throws RequestException If given node is null.
     */
    private void throwOnNull(JsonNode node,String errorMsg) throws RequestException{
        if(isNull(node))
            throw new RequestException(errorMsg);
    }

    /**
     * Retrieves element if it's valid or null if an error occured or element
     * was not found.
     *
     * @param <T> Valid element type.
     * @param c Read element callback.
     *
     * @return Valid element or null otherwise.
     */
    private <T> T readNullable(Callable<T> c)
    {
        try{
            return c.call();
        }catch(Exception e){
            return null;
        }
    }
}

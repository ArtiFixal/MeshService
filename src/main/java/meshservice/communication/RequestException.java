package meshservice.communication;

/**
 * Exception thrown when the {@code Service} request contains invalid data.
 * 
 * @author ArtiFixal
 */
public class RequestException extends Exception{
	private final int responseStatus;

	/**
	 * Request exception with default HTTP response status code of: 
	 * 400 (Bad request) 
	 * @param errorMsg Error description
	 */
	public RequestException(String errorMsg){
		super(errorMsg);
		responseStatus=400;
	}
	
	/**
	 * Exception with custom HTTP response status code.
	 * 
	 * @param responseStatus
	 * @param errorMsg 
	 */
	public RequestException(int responseStatus,String errorMsg){
		super(errorMsg);
		this.responseStatus=responseStatus;
	}

	public int getResponseStatus(){
		return responseStatus;
	}
}

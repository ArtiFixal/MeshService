package meshservice.communication.daos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.stream.Stream;
import meshservice.config.DBConfig;

/**
 * Class used as base for other DAO classes.
 * 
 * @author ArtiFixal
 */
public abstract class DAOObject implements AutoCloseable{
	
	/**
	 * Connection to the DB.
	 */
	protected final Connection con;

	public DAOObject() throws SQLException{
		con=DBConfig.getInstance().createConnection();
	}

	public DAOObject(Connection con){
		this.con=con;
	}
	
	/**
	 * Selects single number from executed query.
	 * 
	 * @param <T> Returned number type
	 * @param sql Selection query of wanted number.
	 * @param numberTypeToReturn What type to return
	 * 
	 * @return Selected number or null if there is no result.
	 * @throws SQLException Any error occurred during the query.
	 */
	public <T extends Number> T getSingleNumber(String sql,
			Class<T> numberTypeToReturn) throws SQLException
	{
		Statement count=con.createStatement();
		T value;
		try(ResultSet result=count.executeQuery(sql)) {
			if(!result.isBeforeFirst())
				return null;
			result.next();
			value=result.getObject(1,numberTypeToReturn);
		}
		return value;
	}
	
	/**
	 * Counts rows in given table.
	 * 
	 * @param tableName Table in which rows will be counted.
	 * 
	 * @return Row count.
	 * @throws SQLException Any error occurred during the query.
	 */
	public long countRows(String tableName) throws SQLException
	{
		return getSingleNumber("SELECT COUNT(id) FROM "+tableName,Long.class);
	}
	
	/**
	 * Retrives from database ID of last inserted record.
	 * 
	 * @return Last inserted ID.
	 * @throws SQLException Any error occurred during the query.
	 */
	public long getLastInsertedId() throws SQLException
	{
		return getSingleNumber("SELECT last_insert_id();",Long.class);
	}

	/**
	 * Selects element from given DB table by given ID.
	 * 
	 * @param tableColumns Which columns to select.
	 * @param tableName From where to select.
	 * @param elementID ID of element to select data from.
	 * 
	 * @return Select result.
	 * @throws SQLException Any error occurred during the query.
	 */
	public ResultSet getElementByID(String tableColumns,String tableName,long elementID) throws SQLException
	{
		try(Statement select=con.createStatement()){
			return select.executeQuery("SELECT "+tableColumns+" FROM "+tableName+" WHERE id="+elementID);
		}
	}

	/**
	 * Selects all available elements from given DB table.
	 * 
	 * @param fieldsName Which columns to select.
	 * @param tableName From where to select.
	 * 
	 * @return Select result.
	 * @throws SQLException Any error occurred during the query.
	 */
	public ResultSet getAllElements(String fieldsName,String tableName) throws SQLException
	{
		try(Statement select=con.createStatement()){
			return select.executeQuery("SELECT "+fieldsName+" FROM "+tableName);
		}
	}

	/**
	 * Selects all elements matching condition from given DB table.
	 * 
	 * @param fieldsName Which columns to select.
	 * @param tableName From where to select.
	 * @param condition What to select.
	 * 
	 * @return Select result.
	 * @throws SQLException Any error occurred during the query.
	 */
	public ResultSet getAllElements(String fieldsName,String tableName,String condition) throws SQLException
	{
		return getAllElements(fieldsName,tableName+" WHERE "+condition);
	}

	/**
	 * Creates SQL update query.
	 * 
	 * @param tableName Where to update.
	 * @param condition What to match.
	 * @param columnsName What to update.
	 * 
	 * @return SQL query.
	 */
	public static String createUpdateQuery(String tableName,String condition,String... columnsName)
	{
		StringBuilder query=new StringBuilder("UPDATE ")
			.append(tableName)
			.append(" SET ");
		for(int i=0;i<columnsName.length-1;i++){
			query.append(columnsName[i])
				.append("=?,");
		}
		query.append(columnsName[columnsName.length-1])
			.append("=?");
		if(!condition.isBlank()){
			query.append("WHERE ")
				.append(condition);
		}
		return query.toString();
	}

	/**
	 * Creates SQL update query.
	 * 
	 * @param tableName Where to update.
	 * @param condition What to match.
	 * @param fields What to update.
	 * 
	 * @return SQL query.
	 */
	public static String createUpdateQuery(String tableName,String condition,OptionalDBField... fields)
	{
		return createUpdateQuery(tableName,condition,Stream.of(fields)
			.map((e)->e.getColumnName()).toArray(String[]::new));
	}
	
	/**
	 * Binds given parameters to the statement.
	 * 
	 * @param statement To what params will be binded.
	 * @param params What to bind.
	 * 
	 * @return Statement with binded params.
	 * @throws SQLException Any error related to invalid parameter markers.
	 */
	public static PreparedStatement bindOptionalParams(PreparedStatement statement,
			Optional... params) throws SQLException
	{
		// Parameter index start from 1
		int lastParam=1;
		for(int i=0;i<params.length;i++){
			if(params[i].isPresent()){
				statement.setObject(lastParam,params[i]);
				lastParam++;
			}
		}
		return statement;
	}
	
	/**
	 * Binds given parameters to the statement.
	 * 
	 * @param statement To what params will be binded.
	 * @param params What to bind.
	 * 
	 * @return Statement with binded params.
	 * @throws SQLException Any error related to invalid parameter markers.
	 */
	public static PreparedStatement bindOptionalParams(PreparedStatement statement,
			OptionalDBField... params) throws SQLException
	{
		return bindOptionalParams(statement,Stream.of(params)
			.map((e)->e.getField()).toArray(Optional[]::new));
	}

	/**
	 * Creates ready to execute update statement.
	 * 
	 * @param tableName Where to update.
	 * @param condition What to match.
	 * @param fieldsToUpdate What to update.
	 * 
	 * @return Ready to execute update statement.
	 * @throws SQLException Any error occured during creation.
	 */
	public PreparedStatement createUpdateStatement(String tableName,String condition,
			OptionalDBField... fieldsToUpdate) throws SQLException
	{
		PreparedStatement statement=con.prepareStatement(createUpdateQuery(tableName,
			condition,fieldsToUpdate));
		bindOptionalParams(statement,fieldsToUpdate);
		return statement;
	}

	/**
	 * Executes delete SQL query on given DB table which matches element by ID.
	 * 
	 * @param tableName Where to delete
	 * @param elementID What to delete
	 * 
	 * @return True if delete was successful, false otherwise.
	 * @throws SQLException Any error occured during query.
	 */
	public boolean deleteByID(String tableName,long elementID) throws SQLException
	{
		try(Statement delete=con.createStatement()){
			return delete.executeUpdate("DELETE FROM "+tableName+" WHERE id="+elementID)==1;
		}
	}

	/**
	 * Closes connection with DB.
	 *
	 * @throws SQLException Any error occured during connection close try.
	 */
	@Override
	public void close() throws SQLException {
		con.close();
	}
}

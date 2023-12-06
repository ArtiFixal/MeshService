package meshservice.communication.daos;

import java.util.Optional;

/**
 * Optional table field.
 * 
 * @author ArtiFixal
 * @param <T> Field type.
 */
public class OptionalDBField<T>{
    /**
     * Field value.
     */
    protected Optional<T> field;
    
    /**
     * Field column name.
     */
    protected String columnName;

    public OptionalDBField(Optional<T> field, String columnName) {
        this.field = field;
        this.columnName = columnName;
    }

    public Optional<T> getField() {
        return field;
    }
    
    /**
     * @return Field column name.
     */
    public String getColumnName() {
        return columnName;
    }
}

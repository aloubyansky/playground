package trick;

public interface ExteriorInstanceProvider {

    boolean supports(Class<?> type);
	
	<T> T getInstance(Class<T> type);
}

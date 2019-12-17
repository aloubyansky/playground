package trick.init;

import trick.ExteriorInstanceProvider;
import trick.Trick;
import trick.impl.TrickImpl;

public class TrickLoader implements ExteriorInstanceProvider {

	@Override
	public boolean supports(Class<?> type) {
		return Trick.class.isAssignableFrom(type);
	}

	@Override
	public <T> T getInstance(Class<T> type) {
		if(supports(type)) {
			return type.cast(new TrickImpl());
		}
		throw new IllegalArgumentException("This loader does not support " + type + " and its subclasses");
	}
}

package org.acme;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

@ApplicationScoped
public class MyValueProducer {

	@Produces
	@MyValue
	public String myValue() {
		return "my-value";
	}
}

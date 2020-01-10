package io.playground.mvn.ext;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.eventspy.EventSpy;
import org.codehaus.plexus.component.annotations.Component;

@Component(role = EventSpy.class, hint = "playground")
public class PlaygroundEventSpy extends AbstractEventSpy {

	@Override
	public void init(Context context) throws Exception {
		System.out.println("EVENT SPY INIT");
	}

	@Override
	public void onEvent(Object event) throws Exception {
		System.out.println("EVENT " + event);
	}

}

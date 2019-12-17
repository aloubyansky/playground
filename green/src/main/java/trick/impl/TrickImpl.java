package trick.impl;

import trick.Trick;
import trick.TrickInfo;

public class TrickImpl implements Trick {

	@Override
	public String whatAreYou() {
		return "green";
	}
	
	@Override
	public TrickInfo getInfo() {
		return new TrickInfoImpl();
	}
}

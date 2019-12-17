package trick.impl;

import trick.TrickInfo;

public class TrickInfoImpl implements TrickInfo {

	@Override
	public String getName() {
		return "red";
	}

	@Override
	public String toString() {
		return getName() + " trick";
	}
}

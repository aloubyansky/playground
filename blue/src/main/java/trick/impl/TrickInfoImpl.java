package trick.impl;

import trick.TrickInfo;

public class TrickInfoImpl implements TrickInfo {

	@Override
	public String getName() {
		return "blue";
	}

	@Override
	public String toString() {
		return getName() + " trick";
	}
}

package trick.impl;

import trick.TrickInfo;

public class TrickInfoImpl implements TrickInfo {

	@Override
	public String getName() {
		return "green";
	}

	@Override
	public String toString() {
		return getName() + " trick";
	}
}

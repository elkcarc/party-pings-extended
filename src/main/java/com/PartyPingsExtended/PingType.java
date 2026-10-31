package com.PartyPingsExtended;

public enum PingType {
	DEFAULT(0),
	AVOID(1),
	CAUTION(2),
	QUESTION(3),
	ATTACK(4),
	DEFEND(5),
	MAGE(6),
	RANGE(7),
	MELEE(8);

	private final int pingtype;

	PingType(int pingtype)
	{
		this.pingtype = pingtype;
	}

	public int getPingType()
	{
		return pingtype;
	}
}

package com.PartyPingsExtended;

/**
 * The ping types, in enum/wire order. DEFAULT is a plain built-in tile ping
 * with no glyph; it never appears on the wheel rim.
 */
public enum PingType {
	DEFAULT(0),
	ENROUTE(1),
	AVOID(2),
	CAUTION(3),
	QUESTION(4),
	ATTACK(5),
	DEFEND(6),
	MAGE(7),
	RANGE(8),
	MELEE(9);

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

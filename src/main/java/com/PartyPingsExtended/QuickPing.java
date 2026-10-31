package com.PartyPingsExtended;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The quick-ping picker: "Default" plus the nine ping types. "Default" is a plain
 * built-in tile ping with no glyph. Kept separate from the wire {@link PingType}
 * so DEFAULT can never be sent or received as a glyph ping.
 */
@Getter
@RequiredArgsConstructor
public enum QuickPing
{
	DEFAULT(null),
	ENROUTE(PingType.ENROUTE),
	AVOID(PingType.AVOID),
	CAUTION(PingType.CAUTION),
	QUESTION(PingType.QUESTION),
	ATTACK(PingType.ATTACK),
	DEFEND(PingType.DEFEND),
	MAGE(PingType.MAGE),
	RANGE(PingType.RANGE),
	MELEE(PingType.MELEE);

	private final PingType pingType;
}

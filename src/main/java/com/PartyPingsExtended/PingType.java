package com.PartyPingsExtended;

import java.awt.Color;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PingType {
	/** Plain built-in tile ping with no glyph; never appears on the wheel rim. */
	DEFAULT(null, null, 0),
	ENROUTE("En-route", new Color(0xFD, 0xD8, 0x35), 1),
	AVOID("Avoid", new Color(0xE5, 0x39, 0x35), 2),
	CAUTION("Caution", new Color(0xFB, 0x8C, 0x00), 3),
	QUESTION("Question", new Color(0x1E, 0x88, 0xE5), 4),
	ATTACK("Attack", new Color(0xD3, 0x2F, 0x2F), 5),
	DEFEND("Defend", new Color(0x43, 0xA0, 0x47), 6),
	MAGE("Mage", new Color(0x9C, 0x27, 0xB0), 7),
	RANGE("Range", new Color(0x00, 0xAC, 0xC6), 8),
	MELEE("Melee", new Color(0x79, 0x55, 0x48), 9);

	private final String label;
	private final Color color;
	private final int pingtype;

	public int getPingType()
	{
		return pingtype;
	}
}

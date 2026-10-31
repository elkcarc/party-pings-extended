package com.PartyPingsExtended;

import java.awt.Color;
import java.awt.image.BufferedImage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;


@RequiredArgsConstructor
@Getter
class PartyTilePingDataExtended {
	private final net.runelite.api.coords.WorldPoint point;
	private final PingType pingType;
	// the sender's party member color (or YELLOW until known)
	@Setter
	private Color color = Color.YELLOW;

	// the glyph for this ping
	@Setter
	private BufferedImage glyph;

	// the faded copy of the glyph
	@Setter
	private BufferedImage faded;

	@Setter
	private int step = -1;

	// fade state: starts at 255, decays with elapsed time in the overlay
	@Setter
	private int alpha = 255;
	private final long creationTime = System.nanoTime();
}

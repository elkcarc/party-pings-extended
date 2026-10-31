package com.PartyPingsExtended;

import java.awt.Color;
import java.awt.image.BufferedImage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * One in-flight ping: the tile it marks, the type (for the glyph), and the
 * sender's color. The glyph is tinted once when the ping is created; the fade
 * copies are cached per alpha step by the overlay, never rebuilt per frame.
 */
@RequiredArgsConstructor
@Getter
class PartyTilePingDataExtended {
	private final net.runelite.api.coords.WorldPoint point;
	private final PingType pingType;
	// the sender's party member color (or YELLOW until known), used to recolor
	// the glyph so it matches the built-in tile highlight.
	@Setter
	private Color color = Color.YELLOW;

	// the glyph for this ping, already tinted (or the base image when this
	// client has recoloring off)
	@Setter
	private BufferedImage glyph;

	// the faded copy of the glyph, recomputed once per fade step (see the
	// overlay); stored on the ping so no cross-ping cache can go stale
	@Setter
	private BufferedImage faded;

	// which fade step `faded` currently holds (alpha / 16); -1 = none yet
	@Setter
	private int step = -1;

	// fade state: starts at 255, decays with elapsed time in the overlay
	@Setter
	private int alpha = 255;
	private final long creationTime = System.nanoTime();
}

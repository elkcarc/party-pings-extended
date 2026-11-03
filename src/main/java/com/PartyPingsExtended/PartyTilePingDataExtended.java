package com.PartyPingsExtended;

import java.awt.Color;
import java.awt.image.BufferedImage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

//client side data for storing information about recieved pings
@RequiredArgsConstructor
@Getter
class PartyTilePingDataExtended {
	private final net.runelite.api.coords.WorldPoint point;
	private final PingType pingType;
	// the sender's party member color (or YELLOW until known)
	@Setter
	private Color color = Color.YELLOW;

	// fade state: starts at 255, decays with elapsed time in the overlay
	@Setter
	private int alpha = 255;
	private final long creationTime = System.nanoTime();
}

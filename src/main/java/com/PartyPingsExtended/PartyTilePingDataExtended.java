package com.PartyPingsExtended;

import java.awt.Color;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.runelite.api.coords.WorldPoint;

@RequiredArgsConstructor
@Getter
@Setter
public class PartyTilePingDataExtended
{
    private final WorldPoint point;
    private final PingType pingType;
    private int alpha = 255;
    private final long creationTime = System.nanoTime();
    // The sending party member's color, resolved at receive time. Used to tint
    // the glyph so it matches the built-in tile highlight.
    private Color color = Color.WHITE;
}

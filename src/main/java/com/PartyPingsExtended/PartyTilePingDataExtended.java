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
    private final Color color;
    private final PingType pingType;
    private int alpha = 255;
    private final long creationTime = System.nanoTime();
}

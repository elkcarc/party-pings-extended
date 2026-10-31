package com.PartyPingsExtended;

import java.awt.Color;
import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.messages.PartyMemberMessage;

//Ping Information. The tile highlight and sound come from the standard TilePing
//sent alongside this message; this carries the tile, the glyph type, and the
//sender's party color (the same value the built-in plugin colors its tile
//highlight with), so the glyph always renders in the color the sender chose.
@Value
@EqualsAndHashCode(callSuper = true)
public class TilePingExtended extends PartyMemberMessage{
    private final WorldPoint point;
    private final PingType pingType;
    private final Color color;
}

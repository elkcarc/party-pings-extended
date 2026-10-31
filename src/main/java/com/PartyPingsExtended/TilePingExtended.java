package com.PartyPingsExtended;

import java.awt.Color;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.messages.PartyMemberMessage;

//Ping Information. Carries what's needed to draw the final glyph over the regular party plugin's ping.
@Value
public class TilePingExtended extends PartyMemberMessage{
    private final WorldPoint point;
    private final PingType pingType;
    private final Color color;
}

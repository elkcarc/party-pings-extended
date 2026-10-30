package com.PartyPingsExtended;

import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.messages.PartyMemberMessage;

//Ping Information. The tile highlight, color and sound come from the standard
//TilePing sent alongside this message; this carries only what the extended
//overlay needs to draw its glyph.
@Value
@EqualsAndHashCode(callSuper = true)
public class TilePingExtended extends PartyMemberMessage{
    private final WorldPoint point;
    private final PingType pingType;
}

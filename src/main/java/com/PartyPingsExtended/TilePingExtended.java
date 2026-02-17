package com.PartyPingsExtended;

import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.messages.PartyMemberMessage;

import java.awt.*;

//Ping Information
@Value
@EqualsAndHashCode(callSuper = true)
public class TilePingExtended extends PartyMemberMessage{
    private final WorldPoint point;
    private final Color color;
}

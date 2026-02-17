package com.PartyPingsExtended;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PingType {
    ENROUTE(1),
    AVOID(2),
    CAUTION(3),
    QUESTION(4),
    ATTACK(5),
    DEFEND(6);

    private final int pingtype;

    public int getPingType()
    {
        return pingtype;
    }
}
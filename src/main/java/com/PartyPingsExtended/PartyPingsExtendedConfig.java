package com.PartyPingsExtended;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import java.awt.Color;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup("Party Pings Extended")
public interface PartyPingsExtendedConfig extends Config
{
	@ConfigItem(
			keyName = "sounds",
			name = "Sound on ping",
			description = "Enables sound notification on party ping.",
			position = 1
	)
	default boolean sounds()
	{
		return true;
	}

	@ConfigItem(
			keyName = "recolorNames",
			name = "Recolor names",
			description = "Recolor party members names based on unique color hash.",
			position = 2
	)
	default boolean recolorNames()
	{
		return true;
	}

	@ConfigItem(
			keyName = "memberColor",
			name = "Self-color",
			description = "Which color you will appear as in the party panel and tile pings.",
			position = 3
	)
	Color memberColor();

	@ConfigItem(
			keyName = "memberColor",
			name = "",
			description = "",
			position = 4
	)
	void setMemberColor(Color newMemberColor);


	@ConfigItem(
			keyName = "pingHotkey",
			name = "Ping hotkey",
			description = "Key to hold to send a tile ping.<br>"
					+ "To ping, hold the ping hotkey down and click on the tile you want to ping.",
			position = 5
	)
	default Keybind pingHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "pings",
			name = "Pings",
			description = "Enables party pings.<br>"
					+ "To ping, hold the ping hotkey down and click on the tile you want to ping.",
			position = 6
	)
	default boolean pings()
	{
		return true;
	}

	@ConfigItem(
			keyName = "pingType",
			name = "Ping Type",
			description = "Selects Ping Image Type",
			position = 7
	)
	default PingType pingType()
	{
		return PingType.QUESTION;
	}
}

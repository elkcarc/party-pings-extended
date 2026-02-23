package com.PartyPingsExtended;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import java.awt.Color;
import net.runelite.client.config.Keybind;

@ConfigGroup("Party Pings Extended")
public interface PartyPingsExtendedConfig extends Config
{

	@ConfigItem(
			keyName = "tilePings",
			name = "Tile Highlight",
			description = "Enables Tile Highlighting on ping.<br>"
					+ "To ping, hold the ping hotkey down and click on the tile you want to ping.",
			position = 1
	)
	default boolean tilePings()
	{
		return true;
	}

	@ConfigItem(
			keyName = "sounds",
			name = "Sound on ping",
			description = "Enables sound notification on party ping.",
			position = 2
	)
	default boolean sounds()
	{
		return true;
	}

	@ConfigItem(
			keyName = "recolorNames",
			name = "Recolor names",
			description = "Recolor party members names based on unique color hash.",
			position = 3
	)
	default boolean recolorNames()
	{
		return true;
	}

	@ConfigItem(
			keyName = "memberColor",
			name = "Self-color",
			description = "Which color you will appear as in the party panel and tile pings.",
			position = 4
	)
	Color memberColor();

	@ConfigItem(
			keyName = "memberColor",
			name = "",
			description = "",
			position = 5
	)
	void setMemberColor(Color newMemberColor);


	@ConfigItem(
			keyName = "enroutePingHotkey",
			name = "En-route ping hotkey",
			description = "Key to hold to send a tile ping with an en-route symbol.<br>"
					+ "To ping, hold the ping hotkey down and click on the tile you want to ping.",
			position = 6
	)
	default Keybind enroutePingHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "avoidPingHotkey",
			name = "Avoid ping hotkey",
			description = "Key to hold to send a tile ping with an avoid symbol.<br>"
					+ "To ping, hold the ping hotkey down and click on the tile you want to ping.",
			position = 7
	)
	default Keybind avoidPingHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "cautionPingHotkey",
			name = "Caution ping hotkey",
			description = "Key to hold to send a  tile ping with a caution symbol.<br>"
					+ "To ping, hold the ping hotkey down and click on the tile you want to ping.",
			position = 8
	)
	default Keybind cautionPingHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "questionPingHotkey",
			name = "Question ping hotkey",
			description = "Key to hold to send a tile ping with a question symbol.<br>"
					+ "To ping, hold the ping hotkey down and click on the tile you want to ping.",
			position = 9
	)
	default Keybind questionPingHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "attackPingHotkey",
			name = "Attack ping hotkey",
			description = "Key to hold to send a tile ping with an attack symbol.<br>"
					+ "To ping, hold the ping hotkey down and click on the tile you want to ping.",
			position = 10
	)
	default Keybind attackPingHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "defendPingHotkey",
			name = "Defend ping hotkey",
			description = "Key to hold to send a tile ping with a defend symbol.<br>"
					+ "To ping, hold the ping hotkey down and click on the tile you want to ping.",
			position = 11
	)
	default Keybind defendPingHotkey()
	{
		return Keybind.NOT_SET;
	}
}

package com.PartyPingsExtended;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup("Party Pings Extended")
public interface PartyPingsExtendedConfig extends Config
{

	@ConfigItem(
			keyName = "enroutePingHotkey",
			name = "En-route ping hotkey",
			description = "Key to hold to send a tile ping with an en-route symbol.<br>"
					+ "To ping, hold the ping hotkey down and click on the tile you want to ping.",
			position = 1
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
			position = 2
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
			position = 3
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
			position = 4
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
			position = 5
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
			position = 6
	)
	default Keybind defendPingHotkey()
	{
		return Keybind.NOT_SET;
	}
}

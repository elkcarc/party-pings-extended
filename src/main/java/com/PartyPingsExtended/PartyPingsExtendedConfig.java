package com.PartyPingsExtended;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup(PartyPingsExtendedConfig.GROUP)
public interface PartyPingsExtendedConfig extends Config
{
	String GROUP = "Party Pings Extended";

	@ConfigItem(
			keyName = "recolorGlyphs",
			name = "Recolor Glyphs",
			description = "Recolor the two text Glyphs to match the ping color selected in the party plugin",
			position = 2
	)
	default boolean recolorGlyphs()
	{
		return true;
	}

	@ConfigItem(
			keyName = "fadeGlyphs",
			name = "Fade Glyphs",
			description = "Fade the glyphs out over time",
			position = 3
	)
	default boolean fadeGlyphs()
	{
		return true;
	}

	@ConfigSection(
   			name = "Ping wheel",
			description = "A wheel to select which ping to send",
			position = 4,
			closedByDefault = true
	)
	String pingWheelSection = "pingWheelSection";

	@ConfigItem(
			keyName = "pingWheelHotkey",
			name = "Wheel hotkey",
			description = "Hotkey to show the ping wheel",
			position = 4,
			section = "pingWheelSection"
	)
	default Keybind pingWheelHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "quickPing",
			name = "Quick ping",
			description = "The ping type in the center of the wheel.",
			position = 5,
			section = "pingWheelSection"
	)
	default PingType quickPing()
	{
		return PingType.DEFAULT;
	}

	@ConfigItem(
			keyName = "wheelDefault",
			name = "Default",
			description = "A standard ping with no Glyph.",
			position = 6,
			section = "pingWheelSection"
	)
	default boolean wheelDefault()
	{
		return true;
	}

	@ConfigItem(
			keyName = "wheelCaution",
			name = "Caution",
			description = "Show the caution option on the wheel.",
			position = 9,
			section = "pingWheelSection"
	)
	default boolean wheelCaution()
	{
		return true;
	}

	@ConfigItem(
			keyName = "wheelQuestion",
			name = "Question",
			description = "Show the question option on the wheel.",
			position = 10,
			section = "pingWheelSection"
	)
	default boolean wheelQuestion()
	{
		return true;
	}

	@ConfigItem(
			keyName = "wheelAttack",
			name = "Attack",
			description = "Show the attack option on the wheel.",
			position = 11,
			section = "pingWheelSection"
	)
	default boolean wheelAttack()
	{
		return true;
	}

	@ConfigItem(
			keyName = "wheelDefend",
			name = "Defend",
			description = "Show the defend option on the wheel.",
			position = 12,
			section = "pingWheelSection"
	)
	default boolean wheelDefend()
	{
		return true;
	}

	@ConfigItem(
			keyName = "wheelMage",
			name = "Mage",
			description = "Show the mage pray option on the wheel.",
			position = 13,
			section = "pingWheelSection"
	)
	default boolean wheelMage()
	{
		return true;
	}

	@ConfigItem(
			keyName = "wheelRange",
			name = "Range",
			description = "Show the range pray option on the wheel.",
			position = 14,
			section = "pingWheelSection"
	)
	default boolean wheelRange()
	{
		return true;
	}

	@ConfigItem(
			keyName = "wheelMelee",
			name = "Melee",
			description = "Show the melee pray option on the wheel.",
			position = 15,
			section = "pingWheelSection"
	)
	default boolean wheelMelee()
	{
		return true;
	}

	@ConfigItem(
			keyName = "wheelCancel",
			name = "Cancel",
			description = "Show the cancel option on the wheel. Selecting it sends nothing.",
			position = 16,
			section = "pingWheelSection"
	)
	default boolean wheelCancel()
	{
		return true;
	}

	@ConfigSection(
			name = "Wheel appearance",
			description = "Colors and transparency of the ping wheel.",
			position = 5,
			closedByDefault = true
	)
	String appearanceSection = "appearanceSection";

	@Alpha
	@ConfigItem(
			keyName = "wheelBackground",
			name = "Background color",
			description = "Fill color of the wheel's center circle and unhovered segments.",
			position = 1,
			section = "appearanceSection"
	)
	default Color wheelBackground()
	{
		return new Color(16, 18, 24, 128);
	}

	@Alpha
	@ConfigItem(
			keyName = "wheelBorderColor",
			name = "Border color",
			description = "Outline color of the wheel's center circle and unhovered segments.",
			position = 2,
			section = "appearanceSection"
	)
	default Color wheelBorderColor()
	{
		return new Color(255, 255, 255, 0);
	}

	@Alpha
	@ConfigItem(
			keyName = "wheelHoverColor",
			name = "Hover fill color",
			description = "Fill color of a segment (or the center circle) while it is aimed at.",
			position = 3,
			section = "appearanceSection"
	)
	default Color wheelHoverColor()
	{
		return new Color(255, 255, 255, 128);
	}

	@Alpha
	@ConfigItem(
			keyName = "wheelHoverBorder",
			name = "Hover border color",
			description = "Outline color of a segment (or the center circle) while it is aimed at.",
			position = 4,
			section = "appearanceSection"
	)
	default Color wheelHoverBorder()
	{
		return new Color(255, 255, 255, 128);
	}

	@Range(min = 60, max = 200)
	@ConfigItem(
			keyName = "wheelDiameter",
			name = "Wheel diameter",
			description = "Diameter of the ping wheel in pixels. Ranges from 60 to 200.",
			position = 5,
			section = "appearanceSection"
	)
	default int wheelDiameter()
	{
		return 150;
	}

	@ConfigSection(
			name = "Hotkey pings",
			description = "Individual hotkeys.",
			position = 3,
			closedByDefault = true
	)
	String hotkeySection = "hotkeySection";

	@ConfigItem(
			keyName = "defaultPingHotkey",
			name = "Default ping hotkey",
			description = "A plain tile ping with no glyph.",
			position = 13,
			section = "hotkeySection"
	)
	default Keybind defaultPingHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "cautionPingHotkey",
			name = "Caution ping hotkey",
			description = "A tile ping with a caution symbol.",
			position = 16,
			section = "hotkeySection"
	)
	default Keybind cautionPingHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "questionPingHotkey",
			name = "Question ping hotkey",
			description = "A tile ping with a question mark symbol.",
			position = 17,
			section = "hotkeySection"
	)
	default Keybind questionPingHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "attackPingHotkey",
		name = "Attack ping hotkey",
			description = "A tile ping with an attack symbol.",
			position = 18,
			section = "hotkeySection"
	)
	default Keybind attackPingHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "defendPingHotkey",
			name = "Defend ping hotkey",
			description = "A tile ping with a defend symbol.",
			position = 19,
			section = "hotkeySection"
	)
	default Keybind defendPingHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "magePingHotkey",
			name = "Mage ping hotkey",
			description = "A tile ping with the mage pray symbol.",
			position = 20,
			section = "hotkeySection"
	)
	default Keybind magePingHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "rangePingHotkey",
			name = "Range ping hotkey",
			description = "A tile ping with the range pray symbol.",
			position = 21,
			section = "hotkeySection"
	)
	default Keybind rangePingHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "meleePingHotkey",
			name = "Melee ping hotkey",
			description = "A tile ping with the melee pray symbol.",
			position = 22,
			section = "hotkeySection"
	)
	default Keybind meleePingHotkey()
	{
		return Keybind.NOT_SET;
	}
}

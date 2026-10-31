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
			keyName = "pingWheelEnabled",
			name = "Use ping wheel",
			description = "Choose how pings are sent. When on, the wheel hotkey opens the radial ping wheel<br>"
					+ "in place of the per-type hotkeys in the 'Hotkey pings' section (which then never<br>"
					+ "capture their keys). When off, the per-type hotkeys are used and the wheel is inert.",
			position = 1
	)
	default boolean pingWheelEnabled()
	{
		return false;
	}

	@ConfigItem(
			keyName = "recolorGlyphs",
			name = "Match glyph color to sender",
			description = "Recolor the en-route, avoid, caution and question glyphs to the sending party member's color,<br>"
					+ "so they match the tile highlight. When off, those glyphs keep their original colors.<br>"
					+ "Per-client: each party member decides this for themselves.",
			position = 2
	)
	default boolean recolorGlyphs()
	{
		return true;
	}

	@ConfigSection(
   			name = "Ping wheel",
			description = "Single-key radial ping wheel. Hold the wheel hotkey and move the mouse to aim<br>"
					+ "at an option, release the key to send it to the tile hovered when it opened.<br>"
					+ "Mouse clicks pass straight through to the game. Only enabled options show<br>"
					+ "on the rim, including 'Default' (a plain tile ping with no glyph). The quick<br>"
					+ "ping is carried by the center only - never shown on the rim.",
			position = 3,
			closedByDefault = true
	)
	String pingWheelSection = "pingWheelSection";

	@ConfigItem(
			keyName = "pingWheelHotkey",
			name = "Wheel hotkey",
			description = "Key to hold to open the ping wheel at the cursor. Aim by moving the mouse,<br>"
					+ "release the key to send. No clicking is involved. Modifier keys<br>"
					+ "(Alt/Shift/Ctrl) stay fully usable for typing in chat.",
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
			description = "Ping carried by the wheel's center: releasing the wheel key while the cursor<br>"
					+ "is in the center sends it, and a glyph type shows its glyph in the center and is<br>"
					+ "removed from the rim. 'DEFAULT' means the center carries the plain tile ping<br>"
					+ "(no glyph); DEFAULT is then absent from the rim.",
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
			description = "Show the default option on the wheel: a plain tile ping with no glyph.",
			position = 6,
			section = "pingWheelSection"
	)
	default boolean wheelDefault()
	{
		return true;
	}

	@ConfigItem(
			keyName = "wheelEnroute",
			name = "En-route",
			description = "Show the en-route option on the wheel.",
			position = 7,
			section = "pingWheelSection"
	)
	default boolean wheelEnroute()
	{
		return true;
	}

	@ConfigItem(
			keyName = "wheelAvoid",
			name = "Avoid",
			description = "Show the avoid option on the wheel.",
			position = 8,
			section = "pingWheelSection"
	)
	default boolean wheelAvoid()
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
			description = "Show the mage option on the wheel.",
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
			description = "Show the range option on the wheel.",
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
			description = "Show the melee option on the wheel.",
			position = 15,
			section = "pingWheelSection"
	)
	default boolean wheelMelee()
	{
		return true;
	}

	@ConfigSection(
			name = "Wheel appearance",
			description = "Colors and transparency of the ping wheel. The center circle and the rim<br>"
					+ "segments share the background, border and hover colors.",
			position = 4,
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
			description = "Diameter of the ping wheel in pixels. The hub and rim scale together,<br>"
					+ "keeping the ring proportions at any size.",
			position = 5,
			section = "appearanceSection"
	)
	default int wheelDiameter()
	{
		return 120;
	}

	@ConfigSection(
			name = "Hotkey pings",
			description = "Per-type hold-and-click hotkeys. Only active while the ping wheel is disabled;<br>"
					+ "a bind that is not active never captures its key. The 'Default' bind sends a<br>"
					+ "plain tile ping with no glyph.",
			position = 5,
			closedByDefault = true
	)
	String hotkeySection = "hotkeySection";

	@ConfigItem(
			keyName = "defaultPingHotkey",
			name = "Default ping hotkey",
			description = "Key to hold to send a plain tile ping with no glyph.<br>"
					+ "To ping, hold the ping hotkey down and click on the tile you want to ping.",
			position = 13,
			section = "hotkeySection"
	)
	default Keybind defaultPingHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "enroutePingHotkey",
			name = "En-route ping hotkey",
			description = "Key to hold to send a tile ping with an en-route symbol.<br>"
					+ "To ping, hold the ping hotkey down and click on the tile you want to ping.",
			position = 14,
			section = "hotkeySection"
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
			position = 15,
			section = "hotkeySection"
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
			description = "Key to hold to send a tile ping with a question symbol.<br>"
					+ "To ping, hold the ping hotkey down and click on the tile you want to ping.",
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
			description = "Key to hold to send a tile ping with an attack symbol.<br>"
					+ "To ping, hold the ping hotkey down and click on the tile you want to ping.",
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
			description = "Key to hold to send a tile ping with a defend symbol.<br>"
					+ "To ping, hold the ping hotkey down and click on the tile you want to ping.",
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
			description = "Key to hold to send a tile ping with a mage symbol.<br>"
					+ "To ping, hold the ping hotkey down and click on the tile you want to ping.",
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
			description = "Key to hold to send a tile ping with a range symbol.<br>"
					+ "To ping, hold the ping hotkey down and click on the tile you want to ping.",
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
			description = "Key to hold to send a tile ping with a melee symbol.<br>"
					+ "To ping, hold the ping hotkey down and click on the tile you want to ping.",
			position = 22,
			section = "hotkeySection"
	)
	default Keybind meleePingHotkey()
	{
		return Keybind.NOT_SET;
	}
}

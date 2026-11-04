package com.PartyPingsExtended;

import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.util.ImageUtil;

/**
 * The plugin's glyph art, loaded once and shared by both overlays, so the
 * type-to-image mapping exists in exactly one place.
 */
@Singleton
class Glyphs
{
	private final BufferedImage CAUTION;
	private final BufferedImage QUESTION;
	private final BufferedImage ATTACK;
	private final BufferedImage DEFEND;
	private final BufferedImage MAGE;
	private final BufferedImage RANGE;
	private final BufferedImage MELEE;
	private final BufferedImage CANCEL;

	@Inject
	Glyphs()
	{
		CAUTION = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/exclamationmark.png");
		QUESTION = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/questionmark.png");
		ATTACK = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/attackmark.png");
		DEFEND = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/defendmark.png");
		MAGE = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/Mage.png");
		RANGE = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/Range.png");
		MELEE = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/Melee.png");
		CANCEL = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/cancelmark.png");
	}

	// null means "this type has no glyph": DEFAULT is the plain built-in ping
	// and CANCEL sends nothing at all
	BufferedImage base(final PingType type)
	{
		switch (type)
		{
			case CAUTION:
				return CAUTION;
			case QUESTION:
				return QUESTION;
			case ATTACK:
				return ATTACK;
			case DEFEND:
				return DEFEND;
			case MAGE:
				return MAGE;
			case RANGE:
				return RANGE;
			case MELEE:
				return MELEE;
			case CANCEL:
				return CANCEL;
			default:
				return null;
		}
	}

	// the types whose glyph follows the sender's party color
	static boolean isRecolorable(final PingType type)
	{
		return type == PingType.CAUTION || type == PingType.QUESTION;
	}
}

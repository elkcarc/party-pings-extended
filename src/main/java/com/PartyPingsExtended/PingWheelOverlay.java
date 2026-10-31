package com.PartyPingsExtended;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

/**
 * The radial ping menu, drawn at the cursor while the wheel hotkey is held,
 * and only while in a party. Segment hit-testing lives in
 * {@link PartyPingsExtendedPlugin} so the visuals and the selection logic can
 * never disagree. The blank center circle always renders (it carries the quick
 * ping's glyph when one is set; that type is then excluded from the rim), and
 * the rim divides evenly among the enabled options - down to none, where only
 * the center circle shows. Colors and transparency come from the appearance
 * config. Icons are the same glyph PNGs the scene overlay uses, tinted to my
 * party color when recoloring is on, so the wheel ships no extra art.
 */
class PingWheelOverlay extends Overlay
{
	private static final int STROKE_WIDTH = 1;
	private static final float HOVER_STROKE = 2f;

	private final net.runelite.api.Client client;
	private final PartyPingsExtendedPlugin plugin;
	private final PartyPingsExtendedConfig config;
	private final BufferedImage ENROUTE;
	private final BufferedImage AVOID;
	private final BufferedImage CAUTION;
	private final BufferedImage QUESTION;
	private final BufferedImage ATTACK;
	private final BufferedImage DEFEND;
	private final BufferedImage MAGE;
	private final BufferedImage RANGE;
	private final BufferedImage MELEE;

	// Cached tinted wheel glyphs, keyed by "pingType:colorRGB", so my color is
	// tinted once per glyph instead of on every render frame.
	private final java.util.Map<String, BufferedImage> tintedGlyphs = new java.util.concurrent.ConcurrentHashMap<>();

	@Inject
	PingWheelOverlay(final net.runelite.api.Client client, final PartyPingsExtendedPlugin plugin, final PartyPingsExtendedConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		ENROUTE = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/enroutemark.png");
		AVOID = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/avoidmark.png");
		CAUTION = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/exclamationmark.png");
		QUESTION = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/questionmark.png");
		ATTACK = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/attackmark.png");
		DEFEND = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/defendmark.png");
		MAGE = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/Mage.png");
		RANGE = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/Range.png");
		MELEE = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/Melee.png");
	}

	private BufferedImage icon(final PingType type)
	{
		final BufferedImage base;
		switch (type)
		{
			case ENROUTE:
				base = ENROUTE;
				break;
			case AVOID:
				base = AVOID;
				break;
			case CAUTION:
				base = CAUTION;
				break;
			case QUESTION:
				base = QUESTION;
				break;
			case ATTACK:
				base = ATTACK;
				break;
			case DEFEND:
				base = DEFEND;
				break;
			case MAGE:
				base = MAGE;
				break;
			case RANGE:
				base = RANGE;
				break;
			case MELEE:
				base = MELEE;
				break;
			default:
				return ENROUTE;
		}

		// the wheel shows my own pings: tint the glyph to my party color when
		// glyph recoloring is on, exactly like the scene glyphs (attack and
		// defend keep their baked-in colors there, so they do here too)
		if (config.recolorGlyphs() && PingsOverlay.isRecolorable(type))
		{
			final Color c = plugin.myColor();
			final String key = type.name() + ":" + (c.getRGB() & 0xFFFFFF);
			final BufferedImage b = base;
			return tintedGlyphs.computeIfAbsent(key, k -> PingsOverlay.tint(b, c));
		}
		return base;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final PartyPingsExtendedPlugin.WheelSession session = plugin.getWheelSession();
		// the wheel renders only while enabled and in a party, whatever the
		// rim or quick ping selections are
		if (session == null || !plugin.wheelActive() || !plugin.inParty())
		{
			return null;
		}

		// aim with the live cursor position every frame (client thread)
		final net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		if (mouse != null && mouse.getX() != -1)
		{
			session.drag = new java.awt.Point(mouse.getX(), mouse.getY());
		}

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// outline the tile that will be pinged
		final net.runelite.api.coords.LocalPoint lp = net.runelite.api.coords.LocalPoint.fromWorld(client, session.target);
		if (lp != null)
		{
			final java.awt.Polygon poly = net.runelite.api.Perspective.getCanvasTilePoly(client, lp);
			if (poly != null)
			{
				graphics.setColor(new Color(255, 255, 255, 64));
				graphics.setStroke(new BasicStroke(2));
				graphics.draw(poly);
			}
		}

		final int cx = session.center.x;
		final int cy = session.center.y;
		final List<PingType> types = session.types;
		final PingType hovered = PartyPingsExtendedPlugin.hoveredType(session);
		final double outer = PartyPingsExtendedPlugin.WHEEL_RADIUS;
		final double inner = PartyPingsExtendedPlugin.WHEEL_DEAD_ZONE + 4;

		// the center circle always renders, carrying the quick ping's glyph when
		// one is set (that type is excluded from the rim); aiming at it is aiming
		// at the quick ping, so it highlights like a segment
		final PingType quick = config.quickPing().getPingType();
		final boolean centerHovered = hovered == PingType.DEFAULT;
		final double hub = PartyPingsExtendedPlugin.WHEEL_DEAD_ZONE - 4;
		graphics.setColor(centerHovered ? config.wheelHoverColor() : config.wheelBackground());
		graphics.fill(new Ellipse2D.Double(cx - hub, cy - hub, hub * 2, hub * 2));
		graphics.setColor(centerHovered ? config.wheelHoverBorder() : config.wheelBorderColor());
		graphics.setStroke(new BasicStroke(centerHovered ? HOVER_STROKE : STROKE_WIDTH));
		graphics.draw(new Ellipse2D.Double(cx - hub, cy - hub, hub * 2, hub * 2));
		if (quick != null)
		{
			// drawn at native pixel size, unscaled, exactly like a scene ping
			final BufferedImage quickGlyph = icon(quick);
			graphics.drawImage(quickGlyph, cx - quickGlyph.getWidth() / 2, cy - quickGlyph.getHeight() / 2, null);
		}

		if (!types.isEmpty())
		{
			drawRim(graphics, cx, cy, types, hovered, outer, inner);
		}

		return null;
	}

	// the rim: divided evenly among however many options are enabled; a single
	// option is a full ring styled exactly like a segment of a multi-segment rim
	private void drawRim(final Graphics2D graphics, final int cx, final int cy, final List<PingType> types,
						 final PingType hovered, final double outer, final double inner)
	{
		final Area innerCut = new Area(new Ellipse2D.Double(cx - inner, cy - inner, inner * 2, inner * 2));
		final double segmentDeg = 360.0 / types.size();
		final int count = types.size();

		for (int i = 0; i < count; i++)
		{
			final PingType type = types.get(i);
			final double centerDeg = -90 + segmentDeg * i; // screen angle of the segment center, clockwise from top

			final Area segment;
			if (count == 1)
			{
				// full ring: the whole disc minus the center, no seam
				segment = new Area(new Ellipse2D.Double(cx - outer, cy - outer, outer * 2, outer * 2));
				segment.subtract(innerCut);
			}
			else
			{
				final double arcStart = -centerDeg - segmentDeg / 2; // Java2D arcs are counter-clockwise
				segment = new Area(new Arc2D.Double(
					cx - outer, cy - outer, outer * 2, outer * 2, arcStart + 1.5, segmentDeg - 3, Arc2D.PIE));
				segment.subtract(innerCut);
			}

			final boolean isHovered = type == hovered;
			graphics.setColor(isHovered ? config.wheelHoverColor() : config.wheelBackground());
			graphics.fill(segment);
			graphics.setColor(isHovered ? config.wheelHoverBorder() : config.wheelBorderColor());
			graphics.setStroke(new BasicStroke(isHovered ? HOVER_STROKE : STROKE_WIDTH));
			graphics.draw(segment);

			final double rad = Math.toRadians(centerDeg);
			final double iconRadius = (inner + outer) / 2 + 2;
			final int ix = (int) Math.round(cx + Math.cos(rad) * iconRadius);
			final int iy = (int) Math.round(cy + Math.sin(rad) * iconRadius);
			// drawn at native pixel size, unscaled, exactly like a scene ping
			final BufferedImage glyph = icon(type);
			graphics.drawImage(glyph, ix - glyph.getWidth() / 2, iy - glyph.getHeight() / 2, null);
		}
	}
}

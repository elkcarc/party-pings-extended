package com.PartyPingsExtended;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

/**
 * The radial ping menu, drawn at the cursor while the wheel hotkey is held,
 * and only while in a party. Inspired by the implementation at https://github.com/oshaunbeck/better-pings-extension
 */
class PingWheelOverlay extends Overlay
{
	//glyph scaling: the outer radius the art is authored for, and the fixed
	//shrink that makes icons sit inside their segments at any diameter
	private static final double ICON_REF_OUTER = 60.0;
	private static final double ICON_SHRINK = 0.8;

	private final net.runelite.api.Client client;
	private final PartyPingsExtendedPlugin plugin;
	private final PartyPingsExtendedConfig config;
	private final BufferedImage CAUTION;
	private final BufferedImage QUESTION;
	private final BufferedImage ATTACK;
	private final BufferedImage DEFEND;
	private final BufferedImage MAGE;
	private final BufferedImage RANGE;
	private final BufferedImage MELEE;
	private final BufferedImage CANCEL;

	//Cached wheel glyphs to display on the wheel, keyed by type:color:scale
	private final java.util.Map<String, BufferedImage> tintedGlyphs = new java.util.concurrent.ConcurrentHashMap<>();

	void clearGlyphCache()
	{
		tintedGlyphs.clear();
	}

	@Inject
	PingWheelOverlay(final net.runelite.api.Client client, final PartyPingsExtendedPlugin plugin, final PartyPingsExtendedConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		CAUTION = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/exclamationmark.png");
		QUESTION = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/questionmark.png");
		ATTACK = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/attackmark.png");
		DEFEND = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/defendmark.png");
		MAGE = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/Mage.png");
		RANGE = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/Range.png");
		MELEE = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/Melee.png");
		CANCEL = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/cancelmark.png");
	}

	//the glyph for a type
	BufferedImage glyphFor(final PingType type)
	{
		final BufferedImage base;
		switch (type)
		{
			case DEFAULT:
				//the default ping has no glyph
				return null;
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
			case CANCEL:
				base = CANCEL;
				break;
			default:
				return null;
		}

		// glyphs scale with the wheel, shrunk by the fixed factor
		final double scale = (plugin.wheelOuter() / ICON_REF_OUTER) * ICON_SHRINK;
		final boolean recolor = config.recolorGlyphs() && PingsOverlay.isRecolorable(type);
		final Color c = recolor ? plugin.myColor() : null;
		final String key = type.name() + ":" + (c == null ? "-" : Integer.toString(c.getRGB() & 0xFFFFFF))
			+ ":" + Math.round(scale * 100);
		return tintedGlyphs.computeIfAbsent(key, k ->
		{
			final BufferedImage tinted = c != null ? PingsOverlay.tint(base, c) : base;
			return scale == 1.0 ? tinted : scaleImage(tinted, scale);
		});
	}

	//Scale to improve glyph appearance at different wheel sizes
	private static BufferedImage scaleImage(final BufferedImage src, final double s)
	{
		final int w = src.getWidth();
		final int h = src.getHeight();
		final int dw = Math.max(1, (int) Math.round(w * s));
		final int dh = Math.max(1, (int) Math.round(h * s));
		final double[] mid = new double[h * dw * 4];
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < dw; x++)
			{
				final double cxPix = (x + 0.5) / s - 0.5;
				final int ix = (int) Math.floor(cxPix);
				double wSum = 0, rSum = 0, gSum = 0, bSum = 0, aSum = 0;
				for (int k = -1; k <= 2; k++)
				{
					final double wt = catmullRom(cxPix - (ix + k));
					wSum += wt;
					final int argb = src.getRGB(clampIx(ix + k, w - 1), y);
					final int a = (argb >>> 24) & 0xFF;
					rSum += wt * (((argb >>> 16) & 0xFF) * a / 255);
					gSum += wt * (((argb >>> 8) & 0xFF) * a / 255);
					bSum += wt * ((argb & 0xFF) * a / 255);
					aSum += wt * a;
				}
				final int base = (y * dw + x) * 4;
				mid[base] = wSum == 0 ? 0 : rSum / wSum;
				mid[base + 1] = wSum == 0 ? 0 : gSum / wSum;
				mid[base + 2] = wSum == 0 ? 0 : bSum / wSum;
				mid[base + 3] = wSum == 0 ? 0 : aSum / wSum;
			}
		}
		final BufferedImage out = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < dh; y++)
		{
			final double cyPix = (y + 0.5) / s - 0.5;
			final int iy = (int) Math.floor(cyPix);
			for (int x = 0; x < dw; x++)
			{
				double rSum = 0, gSum = 0, bSum = 0, aSum = 0;
				for (int k = -1; k <= 2; k++)
				{
					final double wt = catmullRom(cyPix - (iy + k));
					final int base = (clampIx(iy + k, h - 1) * dw + x) * 4;
					rSum += wt * mid[base];
					gSum += wt * mid[base + 1];
					bSum += wt * mid[base + 2];
					aSum += wt * mid[base + 3];
				}
				final double aAvg = aSum;
				if (aAvg <= 0.5)
				{
					continue;
				}
				final int a = clamp255((int) Math.round(aAvg));
				final int ru = clamp255((int) Math.round(clamp255((int) Math.round(rSum)) * 255.0 / aAvg));
				final int gu = clamp255((int) Math.round(clamp255((int) Math.round(gSum)) * 255.0 / aAvg));
				final int bu = clamp255((int) Math.round(clamp255((int) Math.round(bSum)) * 255.0 / aAvg));
				out.setRGB(x, y, (a << 24) | (ru << 16) | (gu << 8) | bu);
			}
		}
		return out;
	}

	//catmull-Rom cubic filter (b = 0, c = 0.5)
	private static double catmullRom(final double x)
	{
		final double t = Math.abs(x);
		final double t2 = t * t;
		final double t3 = t2 * t;
		if (t < 1)
		{
			return 1.5 * t3 - 2.5 * t2 + 1.0;
		}
		else if (t < 2)
		{
			return -0.5 * t3 + 2.5 * t2 - 4.0 * t + 2.0;
		}
		return 0.0;
	}

	private static int clampIx(final int v, final int max)
	{
		return v < 0 ? 0 : (v > max ? max : v);
	}

	private static int clamp255(final int v)
	{
		return v < 0 ? 0 : (v > 255 ? 255 : v);
	}


	@Override
	public Dimension render(Graphics2D graphics)
	{
		final PartyPingsExtendedPlugin.WheelSession session = plugin.getWheelSession();
		if (session == null || !plugin.inParty())
		{
			return null;
		}

		// aim with the cursor position
		final net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		if (mouse != null && mouse.getX() != -1)
		{
			session.drag = new java.awt.Point(mouse.getX(), mouse.getY());
		}

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// outline the selected section
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

		final PartyPingsExtendedPlugin.WheelGeom g = session.geom;

		// the segment that release would select (null = the center/quick ping)
		// - highlight exactly that, wherever the aim lies
		final PartyPingsExtendedPlugin.WheelSegment hoveredSeg = plugin.hoveredSegment(session);
		final boolean centerHovered = hoveredSeg == null;
		graphics.setColor(centerHovered ? config.wheelHoverColor() : config.wheelBackground());
		graphics.fill(g.hubFill);
		graphics.setColor(centerHovered ? config.wheelHoverBorder() : config.wheelBorderColor());
		graphics.fill(centerHovered ? g.hubBorderHover : g.hubBorder);

		final BufferedImage quickGlyph = session.quickGlyph;
		if (quickGlyph != null) // scale with the wheel size
		{
			graphics.drawImage(quickGlyph, session.center.x - quickGlyph.getWidth() / 2,
				session.center.y - quickGlyph.getHeight() / 2, null);
		}

		// rim segments: fill + border fills from the frozen geometry, glyph at
		// the segment's frozen mid-angle position
		final double iconRadius = (g.inner + g.outer) / 2 + 2;
		for (final PartyPingsExtendedPlugin.WheelSegment seg : g.segments)
		{
			final boolean hovered = hoveredSeg != null && hoveredSeg.type == seg.type;
			graphics.setColor(hovered ? config.wheelHoverColor() : config.wheelBackground());
			graphics.fill(seg.fill);
			graphics.setColor(hovered ? config.wheelHoverBorder() : config.wheelBorderColor());
			graphics.fill(hovered ? seg.borderHover : seg.border);

			final double rad = Math.toRadians(seg.centerDeg);
			final int ix = (int) Math.round(session.center.x + Math.cos(rad) * iconRadius);
			final int iy = (int) Math.round(session.center.y + Math.sin(rad) * iconRadius);
			final BufferedImage glyph = seg.glyph;
			if (glyph != null) // scale with the wheel size
			{
				graphics.drawImage(glyph, ix - glyph.getWidth() / 2, iy - glyph.getHeight() / 2, null);
			}
		}

		return null;
	}
}

package com.PartyPingsExtended;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
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
 * the center circle shows. Gaps between segments are slits of constant width
 * from the inner edge of the donut to the outer edge. Colors and transparency
 * come from the appearance config. Icons are the same glyph PNGs the scene
 * overlay uses, tinted to the sender's party color (carried in the ping
 * message) when this client has glyph recoloring enabled, and scaled with the
 * configured wheel diameter, so the wheel ships no extra art.
 */
class PingWheelOverlay extends Overlay
{
	private static final int STROKE_WIDTH = 1;
	private static final float HOVER_STROKE = 2f;
	// constant pixel width of the slit between neighboring segments, measured
	// perpendicular to the slit at every radius
	private static final double GAP_WIDTH = 4.0;
	// glyph scaling: the outer radius the art is authored for, and the fixed
	// shrink that makes icons sit inside their segments at any diameter
	private static final double ICON_REF_OUTER = 60.0;
	private static final double ICON_SHRINK = 0.8;

	private final net.runelite.api.Client client;
	private final PartyPingsExtendedPlugin plugin;
	private final PartyPingsExtendedConfig config;
	private final BufferedImage AVOID;
	private final BufferedImage CAUTION;
	private final BufferedImage QUESTION;
	private final BufferedImage ATTACK;
	private final BufferedImage DEFEND;
	private final BufferedImage MAGE;
	private final BufferedImage RANGE;
	private final BufferedImage MELEE;

	// Cached wheel glyphs, keyed by "type:colorRGB:scale" — tinted and rescaled
	// once per distinct combination instead of on every render frame.
	private final java.util.Map<String, BufferedImage> tintedGlyphs = new java.util.concurrent.ConcurrentHashMap<>();

	// drop all cached tinted glyphs
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
			case DEFAULT:
				// the default ping has no glyph
				return null;
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
				return null;
		}

		// one cache entry per (type, color-or-none, scale): the glyph is tinted
		// and rescaled once per distinct combination, never per frame.
		// Recoloring is a per-client choice (attack, defend, mage, range and
		// melee keep their baked-in colors everywhere, so they do here too).
		final boolean recolor = config.recolorGlyphs() && PingsOverlay.isRecolorable(type);
		final Color c = recolor ? plugin.myColor() : null;
		// glyphs scale with the wheel, shrunk by the fixed factor at every diameter
		final double scale = plugin.wheelOuter() / ICON_REF_OUTER * ICON_SHRINK;
		final String key = type.name() + ":" + (c == null ? "-" : Integer.toString(c.getRGB() & 0xFFFFFF))
			+ ":" + Math.round(scale * 100);
		return tintedGlyphs.computeIfAbsent(key, k ->
		{
			final BufferedImage tinted = c != null ? PingsOverlay.tint(base, c) : base;
			// at the default diameter scale is exactly 1.0: native pixels, no resample
			return scale == 1.0 ? tinted : scaleImage(tinted, scale);
		});
	}

	// bicubic rescale of a glyph at the exact requested scale (no ratio
	// snapping): separable 4x4 Catmull-Rom, ported from the same filter as
	// RuneLite's gpu plugin bicubic.glsl (catmull_rom: b=0, c=0.5). Color is
	// weighted by alpha so fully-transparent texels don't drag edges toward
	// black; channels clamp to [0,255] since cubic filters can overshoot.
	private static BufferedImage scaleImage(final BufferedImage src, final double s)
	{
		final int w = src.getWidth();
		final int h = src.getHeight();
		final int dw = Math.max(1, (int) Math.round(w * s));
		final int dh = Math.max(1, (int) Math.round(h * s));

		// horizontal pass: for every SOURCE row, each destination column
		// gathers a weighted sum of the 4 source pixels around it. Channels are
		// stored premultiplied by alpha (acc[0]=r, acc[1]=g, acc[2]=b, acc[3]=a
		// - explicit channels, not shift arithmetic on the packed int).
		final double[] mid = new double[h * dw * 4];
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < dw; x++)
			{
				final double cxPix = (x + 0.5) / s - 0.5;
				final int ix = (int) Math.floor(cxPix);
				double wSum = 0;
				double rSum = 0, gSum = 0, bSum = 0, aSum = 0;
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
				mid[base] = rSum;
				mid[base + 1] = gSum;
				mid[base + 2] = bSum;
				mid[base + 3] = aSum;
			}
		}

		// vertical pass: sum the stored per-row columns over 4 source rows
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
				// the filter sums to 1, so the weighted sums are already the
				// filtered premultiplied values
				final double aAvg = aSum;
				if (aAvg <= 0.5)
				{
					continue; // fully transparent
				}
				final int a = clamp255((int) Math.round(aAvg));
				// un-premultiply the averaged premultiplied channels
				final int ru = clamp255((int) Math.round(clamp255((int) Math.round(rSum)) * 255.0 / aAvg));
				final int gu = clamp255((int) Math.round(clamp255((int) Math.round(gSum)) * 255.0 / aAvg));
				final int bu = clamp255((int) Math.round(clamp255((int) Math.round(bSum)) * 255.0 / aAvg));
				out.setRGB(x, y, (a << 24) | (ru << 16) | (gu << 8) | bu);
			}
		}
		return out;
	}

	// Catmull-Rom cubic filter (b = 0, c = 0.5), matching bicubic.glsl
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
		final PingType hovered = plugin.hoveredType(session);
		final double outer = plugin.wheelOuter();
		// rim inner edge: one gap out from the hub, like every other gap
		final double inner = plugin.wheelHub() + GAP_WIDTH;

		// the center circle carries the quick ping's glyph when one is set (the
		// quick type never shows on the rim); aiming at the center selects the
		// quick ping, so it highlights like a segment. Its edge sits GAP_WIDTH
		// away from the rim's inner edge, like every other gap
		final PingType quick = config.quickPing();
		final boolean centerHovered = plugin.aimIsCenter(session);
		final double hub = inner - GAP_WIDTH;
		final double hubBw = centerHovered ? HOVER_STROKE : STROKE_WIDTH;
		final Ellipse2D hubCircle = new Ellipse2D.Double(cx - hub, cy - hub, hub * 2, hub * 2);
		final Area hubBorder = new Area(hubCircle);
		// the border band occupies the outermost bw pixels of the hub, fully
		// inside it (inset geometry, not a clipped stroke)
		hubBorder.subtract(new Area(new Ellipse2D.Double(cx - (hub - hubBw), cy - (hub - hubBw),
			(hub - hubBw) * 2, (hub - hubBw) * 2)));
		graphics.setColor(centerHovered ? config.wheelHoverColor() : config.wheelBackground());
		graphics.fill(hubCircle);
		graphics.setColor(centerHovered ? config.wheelHoverBorder() : config.wheelBorderColor());
		graphics.fill(hubBorder);
		final BufferedImage quickGlyph = icon(quick);
		if (quickGlyph != null)
		{
			// wheel glyphs scale with the wheel (cached, resampled once)
			graphics.drawImage(quickGlyph, cx - quickGlyph.getWidth() / 2, cy - quickGlyph.getHeight() / 2, null);
		}

		if (!types.isEmpty())
		{
			drawRim(graphics, cx, cy, types, hovered, outer, inner);
		}

		return null;
	}

	// the rim: divided evenly among however many options are enabled; a single
	// option is a full ring styled exactly like a segment of a multi-segment rim.
	// The slit between neighboring segments has a constant pixel width at every
	// radius: each segment is the hub-cut disc clipped by the two half-planes
	// whose boundaries are the raw sector lines, each shifted GAP_WIDTH/2 away
	// from the segment - two parallel straight edges, gap uniform inside to out
	private void drawRim(final Graphics2D graphics, final int cx, final int cy, final List<PingType> types,
						 final PingType hovered, final double outer, final double inner)
	{
		final int count = types.size();
		final double halfGap = GAP_WIDTH / 2.0;

		for (int i = 0; i < count; i++)
		{
			final PingType type = types.get(i);
			final double centerDeg = -90 + (360.0 / count) * i; // screen angle of the segment center, clockwise from top
			final double bw = hovered == type ? HOVER_STROKE : STROKE_WIDTH; // border band width

			// the segment's fillable area: donut band between hub and outer
			// edge, slit-clipped; for a single option the full ring, no seam
			final Area fill = new Area(new Ellipse2D.Double(cx - outer, cy - outer, outer * 2, outer * 2));
			fill.subtract(new Area(new Ellipse2D.Double(cx - inner, cy - inner, inner * 2, inner * 2)));
			if (count > 1)
			{
				// constant-width slit: half-plane edges at halfGap from the raw
				// sector line on each side
				fill.intersect(halfPlane(cx, cy, centerDeg - 180.0 / count, centerDeg, halfGap));
				fill.intersect(halfPlane(cx, cy, centerDeg + 180.0 / count, centerDeg, halfGap));
			}

			// the border is geometry, not a stroke: the same area shrunk by one
			// border width on every edge; fill minus shrunk = a band that lies
			// entirely inside the segment, visible in full, unclipped
			final Area shrunk = new Area(new Ellipse2D.Double(cx - (outer - bw), cy - (outer - bw),
				(outer - bw) * 2, (outer - bw) * 2));
			shrunk.subtract(new Area(new Ellipse2D.Double(cx - (inner + bw), cy - (inner + bw),
				(inner + bw) * 2, (inner + bw) * 2)));
			if (count > 1)
			{
				shrunk.intersect(halfPlane(cx, cy, centerDeg - 180.0 / count, centerDeg, halfGap + bw));
				shrunk.intersect(halfPlane(cx, cy, centerDeg + 180.0 / count, centerDeg, halfGap + bw));
			}
			final Area border = new Area(fill);
			border.subtract(shrunk);

			graphics.setColor(hovered == type ? config.wheelHoverColor() : config.wheelBackground());
			graphics.fill(fill);
			graphics.setColor(hovered == type ? config.wheelHoverBorder() : config.wheelBorderColor());
			graphics.fill(border);

			final double rad = Math.toRadians(centerDeg);
			final double iconRadius = (inner + outer) / 2 + 2;
			final int ix = (int) Math.round(cx + Math.cos(rad) * iconRadius);
			final int iy = (int) Math.round(cy + Math.sin(rad) * iconRadius);
			final BufferedImage glyph = icon(type);
			if (glyph != null)
			{
				// wheel glyphs scale with the wheel (cached, resampled once)
				graphics.drawImage(glyph, ix - glyph.getWidth() / 2, iy - glyph.getHeight() / 2, null);
			}
		}
	}

	// the half-plane bounded by the line at angle lineDeg through the center,
	// shifted perpendicular to itself by `offset` pixels onto the side facing
	// inwardDeg. Adjacent segments clip the SAME boundary line in opposite
	// directions by halfGap, so the slit between them is a channel of constant
	// width; with fill and shrunk edges offset symmetrically about the shared
	// boundary, the border thickness (fill edge to shrunk edge) is identical
	// on the arcs and on the slit lines. Rendered as a polygon far larger than
	// the wheel so the clip is exact across the whole disc
	private static Area halfPlane(final double cx, final double cy, final double lineDeg,
								final double inwardDeg, final double offset)
	{
		final double lx = Math.cos(Math.toRadians(lineDeg));
		final double ly = Math.sin(Math.toRadians(lineDeg));
		// unit perpendicular of the boundary, pointing onto the inwardDeg side
		double ux = -ly;
		double uy = lx;
		if (ux * Math.cos(Math.toRadians(inwardDeg)) + uy * Math.sin(Math.toRadians(inwardDeg)) < 0)
		{
			ux = -ux;
			uy = -uy;
		}
		final double px = cx + ux * offset;
		final double py = cy + uy * offset;
		final double L = 1000;
		final Path2D poly = new Path2D.Double();
		poly.moveTo(px + lx * L, py + ly * L);
		poly.lineTo(px - lx * L, py - ly * L);
		poly.lineTo(px - lx * L + ux * L, py - ly * L + uy * L);
		poly.lineTo(px + lx * L + ux * L, py + ly * L + uy * L);
		poly.closePath();
		return new Area(poly);
	}
}

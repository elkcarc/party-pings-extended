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
 * message) when this client has glyph recoloring enabled, so the wheel ships
 * no extra art.
 */
class PingWheelOverlay extends Overlay
{
	private static final int STROKE_WIDTH = 1;
	private static final float HOVER_STROKE = 2f;
	// constant pixel width of the slit between neighboring segments, measured
	// perpendicular to the slit at every radius
	private static final double GAP_WIDTH = 4.0;

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
			case DEFAULT:
				// the default ping has no glyph
				return null;
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
				return null;
		}

		// tint the glyph to the sender's party color only when this client has
		// recoloring enabled (per-client choice); attack, defend, mage, range
		// and melee keep their baked-in colors everywhere, so they do here too
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

		// the center circle carries the quick ping's glyph when one is set (the
		// quick type never shows on the rim); aiming at the center selects the
		// quick ping, so it highlights like a segment. Its edge sits GAP_WIDTH
		// away from the rim's inner edge, like every other gap
		final PingType quick = config.quickPing();
		final java.awt.Point aim = session.drag;
		final boolean centerHovered = (aim.x - cx) * (aim.x - cx) + (aim.y - cy) * (aim.y - cy)
			<= PartyPingsExtendedPlugin.WHEEL_DEAD_ZONE * PartyPingsExtendedPlugin.WHEEL_DEAD_ZONE;
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
			// drawn at native pixel size, unscaled, exactly like a scene ping
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
				// drawn at native pixel size, unscaled, exactly like a scene ping
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

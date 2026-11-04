package com.PartyPingsExtended;

import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.party.PartyPlugin;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.plugins.party.PartyPluginService;
import net.runelite.client.plugins.party.data.PartyMemberData;
import net.runelite.client.plugins.party.messages.TilePing;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.input.KeyManager;

@PluginDescriptor(
	name = "Party Pings Extended"
)
// depends on the built-in Party plugin for the tile highlight and sound component
@PluginDependency(PartyPlugin.class)
public class PartyPingsExtendedPlugin extends Plugin
{

	// default wheel geometry
	static final int WHEEL_RADIUS = 88;
	static final int WHEEL_DEAD_ZONE = 30;

	// border band widths (resting / hover) and the constant slit width between
	// neighboring rim segments, in pixels
	static final int STROKE_WIDTH = 1;
	static final float HOVER_STROKE = 2f;
	static final double GAP_WIDTH = 4.0;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private WSClient wsClient;

	@Inject
	private PartyService party;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PingsOverlay pingsOverlay;

	@Inject
	private PingWheelOverlay pingWheelOverlay;

	@Inject
	private PartyPingsExtendedConfig config;

	@Inject
	private KeyManager keyManager;

	@Getter
	private final List<PartyTilePingDataExtended> pendingTilePingsExtended = Collections.synchronizedList(new ArrayList<>());

	@com.google.inject.Inject(optional = true)
	private PartyPluginService partyPluginService;

	@Getter
	private volatile WheelSession wheelSession;

	//which bind is currently held
	private PingType latchedPing = null;

	@Provides
	PartyPingsExtendedConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PartyPingsExtendedConfig.class);
	}

	@Override
	protected void startUp()
	{
		wsClient.registerMessage(TilePing.class);
		wsClient.registerMessage(TilePingExtended.class);
		keyManager.registerKeyListener(defaultHotkeyListener);
		keyManager.registerKeyListener(cautionHotkeyListener);
		keyManager.registerKeyListener(questionHotkeyListener);
		keyManager.registerKeyListener(attackHotkeyListener);
		keyManager.registerKeyListener(defendHotkeyListener);
		keyManager.registerKeyListener(mageHotkeyListener);
		keyManager.registerKeyListener(rangeHotkeyListener);
		keyManager.registerKeyListener(meleeHotkeyListener);
		keyManager.registerKeyListener(wheelHotkeyListener);
		overlayManager.add(pingsOverlay);
		overlayManager.add(pingWheelOverlay);
	}

	@Override
	protected void shutDown()
	{
		wsClient.unregisterMessage(TilePing.class);
		wsClient.unregisterMessage(TilePingExtended.class);
		keyManager.unregisterKeyListener(defaultHotkeyListener);
		keyManager.unregisterKeyListener(cautionHotkeyListener);
		keyManager.unregisterKeyListener(questionHotkeyListener);
		keyManager.unregisterKeyListener(attackHotkeyListener);
		keyManager.unregisterKeyListener(defendHotkeyListener);
		keyManager.unregisterKeyListener(mageHotkeyListener);
		keyManager.unregisterKeyListener(rangeHotkeyListener);
		keyManager.unregisterKeyListener(meleeHotkeyListener);
		keyManager.unregisterKeyListener(wheelHotkeyListener);
		overlayManager.remove(pingsOverlay);
		overlayManager.remove(pingWheelOverlay);
		pingsOverlay.clearGlyphCache();
		pingWheelOverlay.clearGlyphCache();
		wheelSession = null;
		pendingTilePingsExtended.clear();
	}


	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!PartyPingsExtendedConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		final String key = event.getKey();
		if ("recolorGlyphs".equals(key) || "wheelDiameter".equals(key))
		{
			// the scene cache is tint-only; the wheel cache is tint+scale, and a
			// diameter change also makes any open geometry stale
			pingsOverlay.clearGlyphCache();
			pingWheelOverlay.clearGlyphCache();
			wheelSession = null;
		}
	}

	@Subscribe
	public void onFocusChanged(FocusChanged focusChanged)
	{
		if (!focusChanged.isFocused())
		{
			latchedPing = null;
			wheelSession = null;
		}
	}


	private static HotkeyListener typeListener(final Supplier<Keybind> bind, final PingType type,
											   final Consumer<PingType> latch)
	{
		return new HotkeyListener(bind)
		{
			@Override
			public void hotkeyPressed()
			{
				latch.accept(type);
			}

			@Override
			public void hotkeyReleased()
			{
				latch.accept(null);
			}
		};
	}

	private final HotkeyListener defaultHotkeyListener = typeListener(() -> config.defaultPingHotkey(), PingType.DEFAULT, t -> latchedPing = t);
	private final HotkeyListener cautionHotkeyListener = typeListener(() -> config.cautionPingHotkey(), PingType.CAUTION, t -> latchedPing = t);
	private final HotkeyListener questionHotkeyListener = typeListener(() -> config.questionPingHotkey(), PingType.QUESTION, t -> latchedPing = t);
	private final HotkeyListener attackHotkeyListener = typeListener(() -> config.attackPingHotkey(), PingType.ATTACK, t -> latchedPing = t);
	private final HotkeyListener defendHotkeyListener = typeListener(() -> config.defendPingHotkey(), PingType.DEFEND, t -> latchedPing = t);
	private final HotkeyListener mageHotkeyListener = typeListener(() -> config.magePingHotkey(), PingType.MAGE, t -> latchedPing = t);
	private final HotkeyListener rangeHotkeyListener = typeListener(() -> config.rangePingHotkey(), PingType.RANGE, t -> latchedPing = t);
	private final HotkeyListener meleeHotkeyListener = typeListener(() -> config.meleePingHotkey(), PingType.MELEE, t -> latchedPing = t);

	boolean inParty()
	{
		return party.isInParty();
	}

	//get self color for recoloring glyphs
	Color myColor()
	{
		final PartyMember local = party.getLocalMember();
		return memberColor(local != null ? local.getMemberId() : -1L);
	}

	//built-in plugin's color for a member (YELLOW when unknown).
	Color memberColor(final long memberId)
	{
		if (partyPluginService != null)
		{
			final PartyMemberData data = partyPluginService.getPartyData(memberId);
			if (data != null)
			{
				return data.getColor();
			}
		}
		return Color.YELLOW;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (latchedPing == null || client.isMenuOpen())
		{
			return;
		}

		Tile selectedSceneTile = client.getSelectedSceneTile();
		if (selectedSceneTile == null)
		{
			return;
		}

		boolean isOnCanvas = false;

		for (MenuEntry menuEntry : client.getMenuEntries())
		{
			if (menuEntry == null)
			{
				continue;
			}

			if ("walk here".equalsIgnoreCase(menuEntry.getOption()))
			{
				isOnCanvas = true;
			}
		}

		if (!isOnCanvas)
		{
			return;
		}

		final PingType pingType = latchedPing;

		event.consume();
		final WorldPoint point = selectedSceneTile.getWorldLocation();
		clientThread.invokeLater(() -> sendPing(pingType, point));
	}

	//ping wheel mode
	private final HotkeyListener wheelHotkeyListener = new HotkeyListener(() -> config.pingWheelHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			if (wheelSession == null && client.getGameState() == GameState.LOGGED_IN)
			{
				Tile tile = client.getSelectedSceneTile();
				net.runelite.api.Point mouse = client.getMouseCanvasPosition();
				if (tile != null && mouse != null && mouse.getX() != -1)
				{
					java.awt.Point aim = new java.awt.Point(mouse.getX(), mouse.getY());
					// build the wheel geometry once, then cache
					final List<PingType> types = enabledTypes();
					final double outer = wheelOuter();
					final double hub = wheelHub();
					final WheelGeom geom = buildGeom(aim, types, hub, hub + GAP_WIDTH, outer,
						pingWheelOverlay::glyphFor);
					final PingType quick = config.quickPing();
					wheelSession = new WheelSession(aim, tile.getWorldLocation(), geom, quick,
						pingWheelOverlay.glyphFor(quick));
				}
			}
		}

		@Override
		public void hotkeyReleased()
		{
			WheelSession session = wheelSession;
			if (session != null)
			{
				wheelSession = null;
				final WheelSegment seg = hoveredSegment(session);
				PingType selected = seg == null ? session.quickPing : seg.type;
				if (selected != null)
				{
					sendPing(selected, session.target);
				}
			}
		}
		@Override
		public void focusLost()
		{
			wheelSession = null;
			super.focusLost();
		}
	};

	// The rim options currently enabled in config, in enum order: each type is
	// paired with the toggle that shows it. The quick ping is excluded, since it
	// shows in the center instead
	java.util.List<PingType> enabledTypes()
	{
		final java.util.List<PingType> types = new ArrayList<>();
		if (config.wheelDefault())
		{
			types.add(PingType.DEFAULT);
		}
		if (config.wheelCaution())
		{
			types.add(PingType.CAUTION);
		}
		if (config.wheelQuestion())
		{
			types.add(PingType.QUESTION);
		}
		if (config.wheelAttack())
		{
			types.add(PingType.ATTACK);
		}
		if (config.wheelDefend())
		{
			types.add(PingType.DEFEND);
		}
		if (config.wheelMage())
		{
			types.add(PingType.MAGE);
		}
		if (config.wheelRange())
		{
			types.add(PingType.RANGE);
		}
		if (config.wheelMelee())
		{
			types.add(PingType.MELEE);
		}
		if (config.wheelCancel())
		{
			types.add(PingType.CANCEL);
		}
		// the quick ping lives in the centre only, never on the rim
		types.remove(config.quickPing());
		return types;
	}

	// per-segment geometry
	static class WheelSegment
	{
		final PingType type;
		final java.awt.Shape fill;
		final java.awt.Shape border;
		final java.awt.Shape borderHover;
		final double centerDeg;
		final java.awt.image.BufferedImage glyph;
		// where the glyph's top-left belongs, resolved once at open time
		final int iconX;
		final int iconY;

		WheelSegment(PingType type, java.awt.Shape fill, java.awt.Shape border, java.awt.Shape borderHover,
					 double centerDeg, java.awt.image.BufferedImage glyph, int iconX, int iconY)
		{
			this.type = type;
			this.fill = fill;
			this.border = border;
			this.borderHover = borderHover;
			this.centerDeg = centerDeg;
			this.glyph = glyph;
			this.iconX = iconX;
			this.iconY = iconY;
		}
	}

	// all wheel segments together
	static class WheelGeom
	{
		final java.awt.Shape hubFill;
		final java.awt.Shape hubBorder;
		final java.awt.Shape hubBorderHover;
		final java.util.List<WheelSegment> segments;
		final double hub;
		final double inner;
		final double outer;

		WheelGeom(java.awt.Shape hubFill, java.awt.Shape hubBorder, java.awt.Shape hubBorderHover,
				  java.util.List<WheelSegment> segments, double hub, double inner, double outer)
		{
			this.hubFill = hubFill;
			this.hubBorder = hubBorder;
			this.hubBorderHover = hubBorderHover;
			this.segments = segments;
			this.hub = hub;
			this.inner = inner;
			this.outer = outer;
		}
	}

	// build the wheel
	static WheelGeom buildGeom(java.awt.Point center, java.util.List<PingType> types, double hub, double inner, double outer,
							   java.util.function.Function<PingType, BufferedImage> glyphFor)
	{
		final int cx = center.x;
		final int cy = center.y;
		final int count = types.size();
		final double halfGap = GAP_WIDTH / 2.0;
		final double bw = STROKE_WIDTH;
		final double bhb = HOVER_STROKE;

		final java.awt.geom.Ellipse2D hubCircle = new java.awt.geom.Ellipse2D.Double(cx - hub, cy - hub, hub * 2, hub * 2);
		final java.awt.geom.Area hubBorder = new java.awt.geom.Area(hubCircle);
		hubBorder.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(
			cx - (hub - bw), cy - (hub - bw), (hub - bw) * 2, (hub - bw) * 2)));
		final java.awt.geom.Area hubBorderHover = new java.awt.geom.Area(hubCircle);
		hubBorderHover.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(
			cx - (hub - bhb), cy - (hub - bhb), (hub - bhb) * 2, (hub - bhb) * 2)));

		final java.util.List<WheelSegment> segments = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			final double centerDeg = -90 + (360.0 / count) * i;
			final java.awt.geom.Area fill = new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(
				cx - outer, cy - outer, outer * 2, outer * 2));
			fill.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(
				cx - inner, cy - inner, inner * 2, inner * 2)));
			if (count > 1)
			{

				fill.intersect(halfPlane(cx, cy, centerDeg - 180.0 / count, centerDeg, halfGap));
				fill.intersect(halfPlane(cx, cy, centerDeg + 180.0 / count, centerDeg, halfGap));
			}

			final java.awt.geom.Area border = wedgeRing(cx, cy, outer - bw, inner + bw, count, centerDeg, halfGap + bw);
			final java.awt.geom.Area borderHover = wedgeRing(cx, cy, outer - bhb, inner + bhb, count, centerDeg, halfGap + bhb);
			final java.awt.image.BufferedImage glyph = glyphFor.apply(types.get(i));
			// icon anchors depend only on the frozen radii, so resolve them here
			final double rad = Math.toRadians(centerDeg);
			final double iconRadius = (inner + outer) / 2 + 2;
			final int ix = (int) Math.round(cx + Math.cos(rad) * iconRadius);
			final int iy = (int) Math.round(cy + Math.sin(rad) * iconRadius);
			segments.add(new WheelSegment(types.get(i), fill, border, borderHover, centerDeg, glyph,
				glyph == null ? ix : ix - glyph.getWidth() / 2,
				glyph == null ? iy : iy - glyph.getHeight() / 2));
		}
		return new WheelGeom(hubCircle, hubBorder, hubBorderHover, segments, hub, inner, outer);
	}

	private static java.awt.geom.Area wedgeRing(int cx, int cy, double outer, double inner, int count, double centerDeg, double inset)
	{
		final java.awt.geom.Area ring = new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(
			cx - outer, cy - outer, outer * 2, outer * 2));
		ring.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(
			cx - inner, cy - inner, inner * 2, inner * 2)));
		if (count > 1)
		{
			ring.intersect(halfPlane(cx, cy, centerDeg - 180.0 / count, centerDeg, inset));
			ring.intersect(halfPlane(cx, cy, centerDeg + 180.0 / count, centerDeg, inset));
		}
		return ring;
	}

	//wheel segment selection: radius and angle arithmetic only, no shape queries
	WheelSegment hoveredSegment(WheelSession session)
	{
		final WheelGeom g = session.geom;
		final double dx = session.drag.x - session.center.x;
		final double dy = session.drag.y - session.center.y;
		final double distSq = dx * dx + dy * dy;

		if (distSq < g.hub * g.hub)
		{
			return null; //center selects the quick ping
		}
		if (g.segments.isEmpty())
		{
			return null;
		}
		final double segment = 360.0 / g.segments.size();
		final double angle = Math.toDegrees(Math.atan2(dy, dx));
		return g.segments.get((int) Math.floor((((angle + 90 + segment / 2) % 360) + 360) % 360 / segment));
	}

	//helper to get even gaps around the rim (inside to outside)
	static java.awt.geom.Area halfPlane(final double cx, final double cy, final double lineDeg,
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
		final java.awt.geom.Path2D poly = new java.awt.geom.Path2D.Double();
		poly.moveTo(px + lx * L, py + ly * L);
		poly.lineTo(px - lx * L, py - ly * L);
		poly.lineTo(px - lx * L + ux * L, py - ly * L + uy * L);
		poly.lineTo(px + lx * L + ux * L, py + ly * L + uy * L);
		poly.closePath();
		return new java.awt.geom.Area(poly);
	}

	double wheelOuter()
	{
		return config.wheelDiameter() / 2.0;
	}

	// center circle edge radius
	double wheelHub()
	{
		return wheelOuter() * WHEEL_DEAD_ZONE / (double) WHEEL_RADIUS;
	}

	//the state of the open wheel
	static class WheelSession
	{
		final java.awt.Point center;
		final WorldPoint target;
		// the quick ping captured when the wheel opened, so the glyph shown and
		// the type sent can never disagree
		final PingType quickPing;
		final WheelGeom geom;
		final BufferedImage quickGlyph;
		volatile java.awt.Point drag;

		WheelSession(java.awt.Point center, WorldPoint target, WheelGeom geom, PingType quickPing,
					 BufferedImage quickGlyph)
		{
			this.center = center;
			this.target = target;
			this.geom = geom;
			this.quickPing = quickPing;
			this.quickGlyph = quickGlyph;
			this.drag = center;
		}
	}

	private void sendPing(PingType type, WorldPoint point)
	{
		// outside a party, pings do nothing
		if (!party.isInParty())
		{
			return;
		}

		// CANCEL: nothing is sent
		if (type == PingType.CANCEL)
		{
			return;
		}

		// DEFAULT is a plain built-in ping: tile and sound only, no glyph
		if (type == PingType.DEFAULT)
		{
			party.send(new TilePing(point));
			return;
		}


		final Color color = myColor(); //getting the color for recoloring glyphs
		final PartyTilePingDataExtended data = new PartyTilePingDataExtended(point, type);
		data.setColor(color);
		pendingTilePingsExtended.add(data);

		party.send(new TilePing(point));
		party.send(new TilePingExtended(point, type, color));
    }

    @Subscribe
    public void onUserPart(UserPart event)
    {
		wheelSession = null;
		pendingTilePingsExtended.clear();
    }

    @Subscribe
    public void onTilePingExtended(TilePingExtended event)
    {
		final PartyMember local = party.getLocalMember();
		if (local != null && event.getMemberId() == local.getMemberId())
		{

			return;
		}

		PartyTilePingDataExtended data = new PartyTilePingDataExtended(event.getPoint(), event.getPingType());
		data.setColor(event.getColor());
		pendingTilePingsExtended.add(data);
    }
}

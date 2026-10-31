package com.PartyPingsExtended;

import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
import java.util.*;
import java.util.List;

import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.Hooks;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.plugins.party.PartyPluginService;
import net.runelite.client.plugins.party.data.PartyMemberData;
import net.runelite.client.plugins.party.messages.TilePing;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.input.KeyManager;

@Slf4j
@PluginDescriptor(
	name = "Party Pings Extended"
)
// depends on the built-in Party plugin so its public module (which provides
// PartyPluginService, the source of each member's color) is installed into
// this plugin's injector
@PluginDependency(PartyPlugin.class)
public class PartyPingsExtendedPlugin extends Plugin
{

	// default wheel geometry: outer radius and hub radius (px). The live values
	// come from the wheelDiameter config scaled against these defaults, so the
	// hub/rim proportion stays constant at any diameter.
	static final int WHEEL_RADIUS = 88;
	static final int WHEEL_DEAD_ZONE = 30;

	// guards against a single gesture ever double-firing; not a spam limiter
	private static final long SEND_COOLDOWN_MS = 150;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private WSClient wsClient;

	@Inject
	private PartyService party;

	@Inject
	private Hooks hooks;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PingsOverlay pingsOverlay;

	@Inject
	private PingWheelOverlay pingWheelOverlay;

	@Inject
	private PartyPingsExtendedConfig config;

	@Inject
	private Notifier notifier;

	@Inject
	private KeyManager keyManager;

	@Getter
	private final List<PartyTilePingDataExtended> pendingTilePingsExtended = Collections.synchronizedList(new ArrayList<>());

	// The built-in Party plugin's data service: the authoritative source for a
	// member's color (what the built-in tile highlight is drawn with). Optional:
	// null when the Party plugin is not installed here, in which case glyph
	// tinting falls back to yellow. (com.google.inject.Inject so the missing
	// binding is skipped instead of failing plugin injection.)
	@com.google.inject.Inject(optional = true)
	private PartyPluginService partyPluginService;

	// The open ping wheel, or null when closed. Written on the AWT thread,
	// read on the client thread by the overlay.
	@Getter
	private volatile WheelSession wheelSession;

	private long lastSendAt;

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
		keyManager.registerKeyListener(enrouteHotkeyListener);
		keyManager.registerKeyListener(avoidHotkeyListener);
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
		keyManager.unregisterKeyListener(enrouteHotkeyListener);
		keyManager.unregisterKeyListener(avoidHotkeyListener);
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
		clearGlyphCache();
		wheelSession = null;
		pendingTilePingsExtended.clear();
	}

	// drop all cached tinted/faded glyph images
	void clearGlyphCache()
	{
		pingsOverlay.clearGlyphCache();
		pingWheelOverlay.clearGlyphCache();
	}

	// config changes apply immediately: cached glyphs were built under the old
	// settings, so a change to recolorGlyphs (or any appearance option that
	// ends up feeding a tint) must invalidate them. Pings created after the
	// change pick up the new setting when their glyph is prepared.
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (PartyPingsExtendedConfig.GROUP.equals(event.getGroup())
			&& ("recolorGlyphs".equals(event.getKey()) || "wheelDiameter".equals(event.getKey())))
		{
			clearGlyphCache();
		}
	}

	@Subscribe
	public void onFocusChanged(FocusChanged focusChanged)
	{
		if (!focusChanged.isFocused())
		{
			enrouteHotkeyPressed = false;
			avoidHotkeyPressed = false;
			cautionHotkeyPressed = false;
			questionHotkeyPressed = false;
			attackHotkeyPressed = false;
			defendHotkeyPressed = false;
			mageHotkeyPressed = false;
			rangeHotkeyPressed = false;
			meleeHotkeyPressed = false;
			defaultHotkeyPressed = false;
			// close the wheel without pinging
			wheelSession = null;
		}
	}

	// ----- per-type hotkey mode (inactive while the wheel is enabled) -----

	private final HotkeyListener defaultHotkeyListener = new HotkeyListener(() -> config.defaultPingHotkey())
	{
		@Override
		public void keyPressed(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
		{
				return; // wheel mode: stay passive so this bind never eats a key
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
			{
				return;
			}
			super.keyTyped(e);
		}

		@Override
		public void hotkeyPressed()
		{
			defaultHotkeyPressed = true;
		}

		@Override
		public void hotkeyReleased()
		{
			defaultHotkeyPressed = false;
		}
	};
	private boolean defaultHotkeyPressed = false;

	private final HotkeyListener enrouteHotkeyListener = new HotkeyListener(() -> config.enroutePingHotkey())
	{
		@Override
		public void keyPressed(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
			{
				return; // wheel mode: stay passive so this bind never eats a key
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
			{
				return;
			}
			super.keyTyped(e);
		}

		@Override
		public void hotkeyPressed()
		{
			enrouteHotkeyPressed = true;
		}

		@Override
		public void hotkeyReleased()
		{
			enrouteHotkeyPressed = false;
		}
	};
	private boolean enrouteHotkeyPressed = false;

	private final HotkeyListener avoidHotkeyListener = new HotkeyListener(() -> config.avoidPingHotkey())
	{
		@Override
		public void keyPressed(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
			{
				return; // wheel mode: stay passive so this bind never eats a key
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
			{
				return;
			}
			super.keyTyped(e);
		}

		@Override
		public void hotkeyPressed()
		{
			avoidHotkeyPressed = true;
		}

		@Override
		public void hotkeyReleased()
		{
			avoidHotkeyPressed = false;
		}
	};
	private boolean avoidHotkeyPressed = false;

	private final HotkeyListener cautionHotkeyListener = new HotkeyListener(() -> config.cautionPingHotkey())
	{
		@Override
		public void keyPressed(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
			{
				return; // wheel mode: stay passive so this bind never eats a key
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
			{
				return;
			}
			super.keyTyped(e);
		}

		@Override
		public void hotkeyPressed()
		{
			cautionHotkeyPressed = true;
		}

		@Override
		public void hotkeyReleased()
		{
			cautionHotkeyPressed = false;
		}
	};
	private boolean cautionHotkeyPressed = false;

	private final HotkeyListener questionHotkeyListener = new HotkeyListener(() -> config.questionPingHotkey())
	{
		@Override
		public void keyPressed(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
			{
				return; // wheel mode: stay passive so this bind never eats a key
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
			{
				return;
			}
			super.keyTyped(e);
		}

		@Override
		public void hotkeyPressed()
		{
			questionHotkeyPressed = true;
		}

		@Override
		public void hotkeyReleased()
		{
			questionHotkeyPressed = false;
		}
	};
	private boolean questionHotkeyPressed = false;

	private final HotkeyListener attackHotkeyListener = new HotkeyListener(() -> config.attackPingHotkey())
	{
		@Override
		public void keyPressed(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
			{
				return; // wheel mode: stay passive so this bind never eats a key
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
			{
				return;
			}
			super.keyTyped(e);
		}

		@Override
		public void hotkeyPressed()
		{
			attackHotkeyPressed = true;
		}

		@Override
		public void hotkeyReleased()
		{
			attackHotkeyPressed = false;
		}
	};
	private boolean attackHotkeyPressed = false;

	private final HotkeyListener defendHotkeyListener = new HotkeyListener(() -> config.defendPingHotkey())
	{
		@Override
		public void keyPressed(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
			{
				return; // wheel mode: stay passive so this bind never eats a key
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
			{
				return;
			}
			super.keyTyped(e);
		}

		@Override
		public void hotkeyPressed()
		{
			defendHotkeyPressed = true;
		}

		@Override
		public void hotkeyReleased()
		{
			defendHotkeyPressed = false;
		}
	};
	private boolean defendHotkeyPressed = false;

	private final HotkeyListener mageHotkeyListener = new HotkeyListener(() -> config.magePingHotkey())
	{
		@Override
		public void keyPressed(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
			{
				return; // wheel mode: stay passive so this bind never eats a key
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
			{
				return;
			}
			super.keyTyped(e);
		}

		@Override
		public void hotkeyPressed()
		{
			mageHotkeyPressed = true;
		}

		@Override
		public void hotkeyReleased()
		{
			mageHotkeyPressed = false;
		}
	};
	private boolean mageHotkeyPressed = false;

	private final HotkeyListener rangeHotkeyListener = new HotkeyListener(() -> config.rangePingHotkey())
	{
		@Override
		public void keyPressed(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
			{
				return; // wheel mode: stay passive so this bind never eats a key
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
			{
				return;
			}
			super.keyTyped(e);
		}

		@Override
		public void hotkeyPressed()
		{
			rangeHotkeyPressed = true;
		}

		@Override
		public void hotkeyReleased()
		{
			rangeHotkeyPressed = false;
		}
	};
	private boolean rangeHotkeyPressed = false;

	private final HotkeyListener meleeHotkeyListener = new HotkeyListener(() -> config.meleePingHotkey())
	{
		@Override
		public void keyPressed(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
			{
				return; // wheel mode: stay passive so this bind never eats a key
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (config.pingWheelEnabled())
			{
				return;
			}
			super.keyTyped(e);
		}

		@Override
		public void hotkeyPressed()
		{
			meleeHotkeyPressed = true;
		}

		@Override
		public void hotkeyReleased()
		{
			meleeHotkeyPressed = false;
		}
	};
	private boolean meleeHotkeyPressed = false;

	// Whether the wheel currently owns input. The wheel hotkey and the per-type
	// binds consult this so a bind that is not in use never captures its key.
	boolean wheelActive()
	{
		return config.pingWheelEnabled();
	}

	// Pings only work in a party: no wheel, no glyph, no send outside one.
	boolean inParty()
	{
		return party.isInParty();
	}

	// My current party member color: exactly the value the built-in Party plugin
	// colors the tile highlight with (its per-member PartyMemberData color), so
	// glyph and tile always match. YELLOW when unknown (no Party plugin data).
	Color myColor()
	{
		final PartyMember local = party.getLocalMember();
		return memberColor(local != null ? local.getMemberId() : -1L);
	}

	// The built-in plugin's recorded color for a member (YELLOW when unknown).
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
		// wheel mode owns input: no per-type bind is active then
		if (wheelActive())
		{
			return;
		}

		boolean hotkeyPressed = defaultHotkeyPressed || enrouteHotkeyPressed || avoidHotkeyPressed || cautionHotkeyPressed || questionHotkeyPressed
			|| attackHotkeyPressed || defendHotkeyPressed || mageHotkeyPressed || rangeHotkeyPressed || meleeHotkeyPressed;

		if (!hotkeyPressed || client.isMenuOpen())
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

		PingType pingType = PingType.DEFAULT;
		if (defaultHotkeyPressed) {
			pingType = PingType.DEFAULT;
		}
		else if (enrouteHotkeyPressed) {
			pingType = PingType.ENROUTE;
		}
		else if (avoidHotkeyPressed) {
			pingType = PingType.AVOID;
		}
		else if (cautionHotkeyPressed) {
			pingType = PingType.CAUTION;
		}
		else if (questionHotkeyPressed) {
			pingType = PingType.QUESTION;
		}
		else if (attackHotkeyPressed) {
			pingType = PingType.ATTACK;
		}
		else if (defendHotkeyPressed) {
			pingType = PingType.DEFEND;
		}
		else if (mageHotkeyPressed) {
			pingType = PingType.MAGE;
		}
		else if (rangeHotkeyPressed) {
			pingType = PingType.RANGE;
		}
		else if (meleeHotkeyPressed) {
			pingType = PingType.MELEE;
		}

		event.consume();
		final WorldPoint point = selectedSceneTile.getWorldLocation();
		final PingType finalPingType = pingType;
		clientThread.invokeLater(() -> sendPing(finalPingType, point));
	}

	// ----- ping wheel mode: hold the key, aim with the mouse, release to send -----

	private final HotkeyListener wheelHotkeyListener = new HotkeyListener(() -> config.pingWheelHotkey())
	{
		@Override
		public void keyPressed(java.awt.event.KeyEvent e)
		{
			if (!config.pingWheelEnabled())
			{
				return; // per-type mode: the wheel bind must never eat a key
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (!config.pingWheelEnabled())
			{
				return;
			}
			super.keyTyped(e);
		}

		@Override
		public void hotkeyPressed()
		{
			if (wheelSession == null && client.getGameState() == GameState.LOGGED_IN)
			{
				// the wheel always opens when enabled, whatever the quick ping
				// selection is
				Tile tile = client.getSelectedSceneTile();
				net.runelite.api.Point mouse = client.getMouseCanvasPosition();
				if (tile != null && mouse != null && mouse.getX() != -1)
				{
					java.awt.Point aim = new java.awt.Point(mouse.getX(), mouse.getY());
					wheelSession = new WheelSession(aim, tile.getWorldLocation(), enabledTypes());
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

				PingType selected = hoveredType(session);				if (selected == null)
				{
					// aimed at the center: the configured quick ping
					selected = config.quickPing();
				}
				if (selected != null)
				{
					sendPing(selected, session.target);
				}
			}
		}

		@Override
		public void focusLost()
		{
			// alt-tab and the like: close the wheel without pinging
			wheelSession = null;
			super.focusLost();
		}
	};

	// The rim options currently enabled in config, in enum order. The wheel
	// divides itself evenly among exactly these. The quick ping, when it is a
	// glyph type, is excluded (it shows in the center instead) and DEFAULT
	// never appears on the rim at all.
	java.util.List<PingType> enabledTypes()
	{
		java.util.List<PingType> types = new ArrayList<>();
		final PingType quick = config.quickPing();
		if (config.wheelDefault() && quick != PingType.DEFAULT)
		{
			types.add(PingType.DEFAULT);
		}
		if (config.wheelEnroute() && quick != PingType.ENROUTE)
		{
			types.add(PingType.ENROUTE);
		}
		if (config.wheelAvoid())
		{
			types.add(PingType.AVOID);
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
		return types;
	}

	/**
	 * Maps the session's current aim to one of its enabled segments; null inside
	 * the dead center (the caller resolves that to the quick ping). Segments are
	 * centered top-first, continuing clockwise, divided evenly among the
	 * enabled types in enum order.
	 */
	PingType hoveredType(WheelSession session)
	{
		if (aimIsCenter(session) || session.types.isEmpty())
		{
			return null; // the center selects the quick ping (resolved by the caller)
		}

		final java.awt.Point drag = session.drag;
		final int dx = drag.x - session.center.x;
		final int dy = drag.y - session.center.y;

		final List<PingType> types = session.types;
		final double segment = 360.0 / types.size();
		final double angle = Math.toDegrees(Math.atan2(dy, dx)); // 0 = east, clockwise positive
		final int index = (int) Math.floor((((angle + 90 + segment / 2) % 360) + 360) % 360 / segment);
		return types.get(index);
	}

	// live geometry: the wheel's outer edge radius, from the configured diameter
	double wheelOuter()
	{
		return config.wheelDiameter() / 2.0;
	}

	// the hub (center circle) edge radius: the default hub/outer proportion
	// (30/88), scaled to the configured diameter. Also the hit-test radius for
	// "aimed at the center".
	double wheelHub()
	{
		return wheelOuter() * WHEEL_DEAD_ZONE / (double) WHEEL_RADIUS;
	}

	// true when the session's current aim lies inside the hub edge
	boolean aimIsCenter(WheelSession session)
	{
		final java.awt.Point drag = session.drag;
		final double dx = drag.x - session.center.x;
		final double dy = drag.y - session.center.y;
		final double hub = wheelHub();
		return dx * dx + dy * dy < hub * hub;
	}

	static class WheelSession
	{
		final java.awt.Point center;
		final WorldPoint target;
		final List<PingType> types; // enabled rim options, enum order
		volatile java.awt.Point drag;

		WheelSession(java.awt.Point center, WorldPoint target, List<PingType> types)
		{
			this.center = center;
			this.target = target;
			this.types = types;
			this.drag = center;
		}
	}

	// ----- shared send path -----

	private void sendPing(PingType type, WorldPoint point)
	{
		// outside a party, pings do nothing at all
		if (!party.isInParty())
		{
			return;
		}

		final long now = System.currentTimeMillis();
		if (now - lastSendAt < SEND_COOLDOWN_MS)
		{
			return;
		}
		lastSendAt = now;

		// DEFAULT is a plain built-in ping: tile and sound only, no glyph
		if (type == PingType.DEFAULT)
		{
			party.send(new TilePing(point));
			return;
		}

		// the color the built-in plugin will draw the tile highlight with, baked
		// into the extended message so every client resolves the exact same
		// color; each client applies it locally only if its recolorGlyphs is on
		final Color color = myColor();
		final PartyTilePingDataExtended data = new PartyTilePingDataExtended(point, type);
		data.setColor(color);
		pendingTilePingsExtended.add(data);

		party.send(new TilePing(point));
		party.send(new TilePingExtended(point, type, color));
    }

    @Subscribe
    public void onUserPart(UserPart event)
    {
		// drop in-flight pings: outside a party, nothing renders
		wheelSession = null;
		pendingTilePingsExtended.clear();
    }

    @Subscribe
    public void onTilePingExtended(TilePingExtended event)
    {
		final PartyMember local = party.getLocalMember();
		if (local != null && event.getMemberId() == local.getMemberId())
		{
			// our own ping echoed back; it was already shown on send
			return;
		}

		// render with the color carried in the message, which is the sender's
		// own built-in-plugin color at send time; tinting is decided per-client
		// by the recolorGlyphs setting at render time
		PartyTilePingDataExtended data = new PartyTilePingDataExtended(event.getPoint(), event.getPingType());
		data.setColor(event.getColor());
		pendingTilePingsExtended.add(data);
    }
}

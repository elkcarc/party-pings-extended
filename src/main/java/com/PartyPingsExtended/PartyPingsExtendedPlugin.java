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
// depends on the built-in Party plugin for the tile highlight and sound component
@PluginDependency(PartyPlugin.class)
public class PartyPingsExtendedPlugin extends Plugin
{

	// default wheel geometry
	static final int WHEEL_RADIUS = 88;
	static final int WHEEL_DEAD_ZONE = 30;

	// guards against a single gesture ever double-firing; not a spam limiter
	private static final long SEND_COOLDOWN_MS = 10;

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

	@com.google.inject.Inject(optional = true)
	private PartyPluginService partyPluginService;

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

	void clearGlyphCache()
	{
		pingsOverlay.clearGlyphCache();
		pingWheelOverlay.clearGlyphCache();
	}


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
			defaultHotkeyPressed = false;
			avoidHotkeyPressed = false;
			cautionHotkeyPressed = false;
			questionHotkeyPressed = false;
			attackHotkeyPressed = false;
			defendHotkeyPressed = false;
			mageHotkeyPressed = false;
			rangeHotkeyPressed = false;
			meleeHotkeyPressed = false;
			defaultHotkeyPressed = false;
			wheelSession = null;
		}
	}

	private final HotkeyListener defaultHotkeyListener = new HotkeyListener(() -> config.defaultPingHotkey())
	{
		@Override
		public void keyPressed(java.awt.event.KeyEvent e)
		{
			if (wheelActive())
		{
				return;
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (wheelActive())
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

	private final HotkeyListener avoidHotkeyListener = new HotkeyListener(() -> config.avoidPingHotkey())
	{
		@Override
		public void keyPressed(java.awt.event.KeyEvent e)
		{
			if (wheelActive())
			{
				return;
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (wheelActive())
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
			if (wheelActive())
			{
				return;
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (wheelActive())
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
			if (wheelActive())
			{
				return;
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (wheelActive())
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
			if (wheelActive())
			{
				return;
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (wheelActive())
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
			if (wheelActive())
			{
				return;
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (wheelActive())
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
			if (wheelActive())
			{
				return;
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (wheelActive())
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
			if (wheelActive())
			{
				return;
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (wheelActive())
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
			if (wheelActive())
			{
				return;
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (wheelActive())
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


	boolean wheelActive()
	{
		return config.pingInputMode() == PingInputMode.PING_WHEEL;
	}

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
		if (wheelActive())
		{
			return;
		}

		boolean hotkeyPressed = defaultHotkeyPressed || avoidHotkeyPressed || cautionHotkeyPressed || questionHotkeyPressed
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

	//ping wheel mode
	private final HotkeyListener wheelHotkeyListener = new HotkeyListener(() -> config.pingWheelHotkey())
	{
		@Override
		public void keyPressed(java.awt.event.KeyEvent e)
		{
			if (!wheelActive())
			{
				return;
			}
			super.keyPressed(e);
		}

		@Override
		public void keyTyped(java.awt.event.KeyEvent e)
		{
			if (!wheelActive())
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
				PingType selected = hoveredType(session);
				if (selected == null) // aimed at the center, send quick ping
				{
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
			wheelSession = null;
			super.focusLost();
		}
	};

	// The rim options currently enabled in config, in enum order. The wheel
	// divides itself evenly among exactly these. The quick ping is excluded (it shows in the center instead)
	java.util.List<PingType> enabledTypes()
	{
		java.util.List<PingType> types = new ArrayList<>();
		final PingType quick = config.quickPing();
		if (config.wheelDefault() && quick != PingType.DEFAULT)
		{
			types.add(PingType.DEFAULT);
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

	//wheel segment selection handler
	PingType hoveredType(WheelSession session)
	{
		if (aimIsCenter(session) || session.types.isEmpty())
		{
			return null; // the center selects the quick ping
		}

		final java.awt.Point drag = session.drag;
		final int dx = drag.x - session.center.x;
		final int dy = drag.y - session.center.y;

		final List<PingType> types = session.types;
		final double segment = 360.0 / types.size();
		final double angle = Math.toDegrees(Math.atan2(dy, dx));
		final int index = (int) Math.floor((((angle + 90 + segment / 2) % 360) + 360) % 360 / segment);
		return types.get(index);
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
		final List<PingType> types;
		volatile java.awt.Point drag;

		WheelSession(java.awt.Point center, WorldPoint target, List<PingType> types)
		{
			this.center = center;
			this.target = target;
			this.types = types;
			this.drag = center;
		}
	}


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

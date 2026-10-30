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
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.Hooks;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.plugins.party.messages.MemberColorUpdate;
import net.runelite.client.plugins.party.messages.TilePing;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.input.KeyManager;

@Slf4j
@PluginDescriptor(
	name = "Party Pings Extended"
)
public class PartyPingsExtendedPlugin extends Plugin
{

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
	private PartyPingsExtendedConfig config;

	@Inject
	private Notifier notifier;

	@Inject
	private KeyManager keyManager;

	@Getter
	private final List<PartyTilePingDataExtended> pendingTilePingsExtended = Collections.synchronizedList(new ArrayList<>());

	// Sender color per party member id, mirroring the built-in Party plugin:
	// seeded WHITE on join, updated as members pick a custom color.
	private final Map<Long, Color> memberColors = new ConcurrentHashMap<>();

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
		wsClient.registerMessage(MemberColorUpdate.class);
		keyManager.registerKeyListener(enrouteHotkeyListener);
		keyManager.registerKeyListener(avoidHotkeyListener);
		keyManager.registerKeyListener(cautionHotkeyListener);
		keyManager.registerKeyListener(questionHotkeyListener);
		keyManager.registerKeyListener(attackHotkeyListener);
		keyManager.registerKeyListener(defendHotkeyListener);
		overlayManager.add(pingsOverlay);
	}

	@Override
	protected void shutDown()
	{
		wsClient.unregisterMessage(TilePing.class);
		wsClient.unregisterMessage(TilePingExtended.class);
		wsClient.unregisterMessage(MemberColorUpdate.class);
		keyManager.unregisterKeyListener(enrouteHotkeyListener);
		keyManager.unregisterKeyListener(avoidHotkeyListener);
		keyManager.unregisterKeyListener(cautionHotkeyListener);
		keyManager.unregisterKeyListener(questionHotkeyListener);
		keyManager.unregisterKeyListener(attackHotkeyListener);
		keyManager.unregisterKeyListener(defendHotkeyListener);
		overlayManager.remove(pingsOverlay);
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
		}
	}

	private final HotkeyListener enrouteHotkeyListener = new HotkeyListener(() -> config.enroutePingHotkey())
	{
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

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		boolean hotkeyPressed = enrouteHotkeyPressed || avoidHotkeyPressed || cautionHotkeyPressed || questionHotkeyPressed || attackHotkeyPressed || defendHotkeyPressed;

		if (!hotkeyPressed || client.isMenuOpen() || !party.isInParty() )
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

		PingType pingType = PingType.ENROUTE;;
		if (enrouteHotkeyPressed) {
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


		event.consume();
		final WorldPoint point = selectedSceneTile.getWorldLocation();
		final PingType finalPingType = pingType;
		clientThread.invokeLater(() -> {
			party.send(new TilePing(point));
			party.send(new TilePingExtended(point, finalPingType));
		});
	}

	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		memberColors.putIfAbsent(event.getMemberId(), Color.WHITE);
	}

	@Subscribe
	public void onMemberColorUpdate(MemberColorUpdate event)
	{
		memberColors.put(event.getMemberId(), event.getColor());
	}

	@Subscribe
	public void onTilePingExtended(TilePingExtended event)
	{
		PartyTilePingDataExtended data = new PartyTilePingDataExtended(event.getPoint(), event.getPingType());
		data.setColor(memberColors.getOrDefault(event.getMemberId(), Color.WHITE));
		pendingTilePingsExtended.add(data);
	}
}

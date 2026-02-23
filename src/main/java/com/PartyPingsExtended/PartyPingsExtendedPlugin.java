package com.PartyPingsExtended;

import com.google.inject.Provides;
import javax.inject.Inject;
import javax.sound.sampled.*;

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

import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.Hooks;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.party.data.PartyData;
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
	protected Clip soundClip;

	@Inject
	private Notifier notifier;

	@Inject
	private KeyManager keyManager;

	@Getter
	private final List<PartyTilePingDataExtended> pendingTilePingsExtended = Collections.synchronizedList(new ArrayList<>());

	@Provides
	PartyPingsExtendedConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PartyPingsExtendedConfig.class);
	}

	@Override
	protected void startUp()
	{
		wsClient.registerMessage(TilePingExtended.class);
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
		wsClient.unregisterMessage(TilePingExtended.class);
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

		PingType pingType = PingType.QUESTION; //todo determine based on key

		event.consume();
		final TilePingExtended tilePing = new TilePingExtended(selectedSceneTile.getWorldLocation(), this.config.memberColor(), pingType);
		clientThread.invoke(() -> client.playSoundEffect(SoundEffectID.SMITH_ANVIL_TONK));
		clientThread.invokeLater(() -> party.send(tilePing));
	}

	@Subscribe
	public void onTilePingExtended(TilePingExtended event)
	{
		final Color color = event.getColor() != null ? event.getColor() : Color.RED;
		pendingTilePingsExtended.add(new PartyTilePingDataExtended(event.getPoint(), color, event.getPingType()));

		if (config.sounds())
		{
			WorldPoint point = event.getPoint();

			if (point.getPlane() != client.getPlane() || !WorldPoint.isInScene(client, point.getX(), point.getY()))
			{
				return;
			}

			clientThread.invoke(() -> client.playSoundEffect(SoundEffectID.SMITH_ANVIL_TONK));
		}
	}



}

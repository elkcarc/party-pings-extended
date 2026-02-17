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
		keyManager.registerKeyListener(hotkeyListener);
		overlayManager.add(pingsOverlay);
	}

	@Override
	protected void shutDown()
	{
		wsClient.unregisterMessage(TilePingExtended.class);
		keyManager.unregisterKeyListener(hotkeyListener);
		overlayManager.remove(pingsOverlay);
	}

	@Subscribe
	public void onFocusChanged(FocusChanged focusChanged)
	{
		if (!focusChanged.isFocused())
		{
			hotkeyPressed = false;
		}
	}

	private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.pingHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			hotkeyPressed = true;
		}

		@Override
		public void hotkeyReleased()
		{
			hotkeyPressed = false;
		}
	};
	private boolean hotkeyPressed = false;

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{

		if (!hotkeyPressed || client.isMenuOpen() || !party.isInParty() || !config.pings() )
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

		event.consume();
		final TilePingExtended tilePing = new TilePingExtended(selectedSceneTile.getWorldLocation(), this.config.memberColor(), config.pingType());
		clientThread.invoke(() -> client.playSoundEffect(SoundEffectID.SMITH_ANVIL_TONK));
		clientThread.invokeLater(() -> party.send(tilePing));
	}

	@Subscribe
	public void onTilePingExtended(TilePingExtended event)
	{
		if (config.pings())
		{
			final Color color = event.getColor() != null ? event.getColor() : Color.RED;
			pendingTilePingsExtended.add(new PartyTilePingDataExtended(event.getPoint(), color, event.getPingType()));
		}

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

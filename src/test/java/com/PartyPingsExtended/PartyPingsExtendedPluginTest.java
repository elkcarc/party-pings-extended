package com.PartyPingsExtended;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PartyPingsExtendedPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PartyPingsExtendedPlugin.class);
		RuneLite.main(args);
	}
}
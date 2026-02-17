package com.PartyPingsExtended;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.Iterator;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.party.data.PartyTilePingData;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

class PingsOverlay extends Overlay
{
    private final Client client;
    private final PartyPingsExtendedPlugin plugin;

    @Inject
    private PingsOverlay(final Client client, final PartyPingsExtendedPlugin plugin)
    {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        synchronized (plugin.getPendingTilePingsExtended())
        {
            final Iterator<PartyTilePingDataExtended> iterator = plugin.getPendingTilePingsExtended().iterator();

            while (iterator.hasNext())
            {
                PartyTilePingDataExtended next = iterator.next();

                if (next.getAlpha() <= 0)
                {
                    iterator.remove();
                    continue;
                }

                renderPing(graphics, next);

                long elapsedTimeMillis = (System.nanoTime() - next.getCreationTime()) / 1000000;
                next.setAlpha((int) Math.max(0, 255 - (elapsedTimeMillis / 4)));
            }
        }

        return null;
    }

    private void renderPing(final Graphics2D graphics, final PartyTilePingDataExtended ping)
    {
        final LocalPoint localPoint = LocalPoint.fromWorld(client, ping.getPoint());

        if (localPoint == null)
        {
            return;
        }

        final Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);

        if (poly == null)
        {
            return;
        }

        final Color color = new Color(
                ping.getColor().getRed(),
                ping.getColor().getGreen(),
                ping.getColor().getBlue(),
                ping.getAlpha());

        OverlayUtil.renderPolygon(graphics, poly, color);
    }
}

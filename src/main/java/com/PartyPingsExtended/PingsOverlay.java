package com.PartyPingsExtended;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.party.data.PartyTilePingData;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.ImageUtil;

class PingsOverlay extends Overlay
{
    private final Client client;
    private final PartyPingsExtendedPlugin plugin;
    private final BufferedImage ARROW_ICON;


    @Inject
    private PingsOverlay(final Client client, final PartyPingsExtendedPlugin plugin)
    {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        ARROW_ICON = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/questionmark.png");
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
        renderGlyphPing(graphics, localPoint);
    }
    private void renderGlyphPing(final Graphics2D graphics, final LocalPoint dest)
    {
        if (dest == null)
        {
            return;
        }

        Point canvasLoc = Perspective.getCanvasImageLocation(client, dest, ARROW_ICON, 120 + (int) (10 * Math.sin(client.getGameCycle() / 6.0)));

        if (canvasLoc != null)
        {
            OverlayUtil.renderImageLocation(graphics, canvasLoc, ARROW_ICON);
        }

    }
}

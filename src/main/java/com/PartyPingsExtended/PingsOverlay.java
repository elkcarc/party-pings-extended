package com.PartyPingsExtended;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.ImageUtil;

class PingsOverlay extends Overlay
{
    private final Client client;
    private final PartyPingsExtendedPlugin plugin;
    private final PartyPingsExtendedConfig config;
    private final BufferedImage ENROUTE;
    private final BufferedImage AVOID;
    private final BufferedImage CAUTION;
    private final BufferedImage QUESTION;
    private final BufferedImage ATTACK;
    private final BufferedImage DEFEND;




    @Inject
    private PingsOverlay(final Client client, final PartyPingsExtendedPlugin plugin, final PartyPingsExtendedConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        ENROUTE = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/enroutemark.png");
        AVOID = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/avoidmark.png");
        CAUTION = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/exclamationmark.png");
        QUESTION = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/questionmark.png");
        ATTACK = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/attackmark.png");
        DEFEND = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/defendmark.png");
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

                if(config.tilePings()){
                    renderPing(graphics, next);
                }
                renderGlyph(graphics, next);

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
    private void renderGlyph(final Graphics2D graphics, final PartyTilePingDataExtended ping)
    {
        final LocalPoint localPoint = LocalPoint.fromWorld(client, ping.getPoint());
        if (localPoint == null)
        {
            return;
        }

        BufferedImage Glyph;
        switch(ping.getPingType().getPingType())
        {
            case 1:
                Glyph = ENROUTE;
                break;
            case 2:
                Glyph = AVOID;
                break;
            case 3:
                Glyph = CAUTION;
                break;
            case 4:
                Glyph = QUESTION;
                break;
            case 5:
                Glyph = ATTACK;
                break;
            case 6:
                Glyph = DEFEND;
                break;
            default:
                return;
        }

        Point canvasLoc = Perspective.getCanvasImageLocation(client, localPoint, Glyph, 120 + (int) (10 * Math.sin(client.getGameCycle() / 6.0)));
        if (canvasLoc != null)
        {
            OverlayUtil.renderImageLocation(graphics, canvasLoc, Glyph);
        }
    }
}

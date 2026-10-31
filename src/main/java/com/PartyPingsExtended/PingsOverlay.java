package com.PartyPingsExtended;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.ImageUtil;

@Singleton
class PingsOverlay extends Overlay
{
    private final Client client;
    private final PartyPingsExtendedPlugin plugin;
    private final PartyPingsExtendedConfig config;
    private final BufferedImage AVOID;
    private final BufferedImage CAUTION;
    private final BufferedImage QUESTION;
    private final BufferedImage ATTACK;
    private final BufferedImage DEFEND;
    private final BufferedImage MAGE;
    private final BufferedImage RANGE;
    private final BufferedImage MELEE;

    static boolean isRecolorable(PingType type)
    {
        switch (type)
        {
            case AVOID:
            case CAUTION:
            case QUESTION:
                return true;
            default:
                return false;
        }
    }

    @Inject
    private PingsOverlay(final Client client, final PartyPingsExtendedPlugin plugin, final PartyPingsExtendedConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        AVOID = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/avoidmark.png");
        CAUTION = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/exclamationmark.png");
        QUESTION = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/questionmark.png");
        ATTACK = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/attackmark.png");
        DEFEND = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/defendmark.png");
        MAGE = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/Mage.png");
        RANGE = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/Range.png");
        MELEE = ImageUtil.loadImageResource(PartyPingsExtendedPlugin.class, "/Melee.png");
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

                renderGlyph(graphics, next);

                long elapsedTimeMillis = (System.nanoTime() - next.getCreationTime()) / 1000000;
                next.setAlpha((int) Math.max(0, 255 - (elapsedTimeMillis / 4)));
            }
        }
        return null;
    }

    private void renderGlyph(final Graphics2D graphics, final PartyTilePingDataExtended ping)
    {
        final LocalPoint localPoint = LocalPoint.fromWorld(client, ping.getPoint());
        if (localPoint == null)
        {
            return;
        }


        final int step = ping.getAlpha() / 16;
        if (step != ping.getStep())
        {
            BufferedImage Glyph = baseGlyph(ping.getPingType());
            if (config.recolorGlyphs() && isRecolorable(ping.getPingType()))
            {
                Glyph = tint(Glyph, ping.getColor());
            }
            ping.setStep(step);
            ping.setFaded(applyAlpha(Glyph, step * 16));
        }
        final BufferedImage Glyph = ping.getFaded();
        if (Glyph == null)
        {
            return;
        }

        net.runelite.api.Point canvasLoc = Perspective.getCanvasImageLocation(client, localPoint, Glyph, 120 + (int) (10 * Math.sin(client.getGameCycle() / 6.0)));
        if (canvasLoc != null)
        {
            OverlayUtil.renderImageLocation(graphics, canvasLoc, Glyph);
        }
    }

    BufferedImage baseGlyph(final PingType type)
    {
        switch (type)
        {
            case AVOID:
                return AVOID;
            case CAUTION:
                return CAUTION;
            case QUESTION:
                return QUESTION;
            case ATTACK:
                return ATTACK;
            case DEFEND:
                return DEFEND;
            case MAGE:
                return MAGE;
            case RANGE:
                return RANGE;
            case MELEE:
                return MELEE;
            default:
                return AVOID;
        }
    }

    void clearGlyphCache()
    {
    }

    static BufferedImage tint(BufferedImage src, Color color) //recolor based on party color helper
    {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0)
                {
                    continue;
                }
                out.setRGB(x, y, (a << 24) | (color.getRGB() & 0x00FFFFFF));
            }
        }
        return out;
    }

    static BufferedImage applyAlpha(BufferedImage src, int alpha) //fade out helper
    {
        if (alpha >= 255)
        {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                int argb = src.getRGB(x, y);
                int sa = (argb >>> 24) & 0xFF;
                if (sa == 0)
                {
                    continue;
                }
                int fa = (sa * alpha) / 255;
                out.setRGB(x, y, ((fa << 24) | (argb & 0x00FFFFFF)));
            }
        }
        return out;
    }
}

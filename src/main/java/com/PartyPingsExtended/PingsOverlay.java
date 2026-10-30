package com.PartyPingsExtended;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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

    // Cached tinted glyphs, keyed by "pingType:colorRGB", so a given sender
    // color is tinted once per glyph instead of on every render frame.
    private final Map<String, BufferedImage> tintedGlyphs = new HashMap<>();

    // The four semantic glyphs that follow the sender's party color; attack and
    // defend keep their baked-in image colors.
    static boolean isRecolorable(PingType type)
    {
        switch (type)
        {
            case ENROUTE:
            case AVOID:
            case CAUTION:
            case QUESTION:
                return true;
            default:
                return false;
        }
    }

    // Tint a monochrome glyph to `color` using its alpha channel as a mask.
    static BufferedImage tint(BufferedImage src, Color color)
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
                out.setRGB(x, y, ((a << 24) | (color.getRGB() & 0x00FFFFFF)));
            }
        }
        return out;
    }




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

        // Tint the four semantic glyphs to the sender's party color so they
        // match the built-in tile highlight; attack and defend keep their own.
        if (config.recolorGlyphs() && isRecolorable(ping.getPingType()))
        {
            final BufferedImage base = Glyph;
            final Color c = ping.getColor();
            final String key = ping.getPingType().getPingType() + ":" + (c.getRGB() & 0xFFFFFF);
            Glyph = tintedGlyphs.computeIfAbsent(key, k -> tint(base, c));
        }

        // Fade in lock-step with the built-in tile: bake the ping's alpha into
        // each pixel so the glyph dims over its ~1 s life instead of popping out.
        int a = ping.getAlpha();
        if (a < 255)
        {
            Glyph = applyAlpha(Glyph, a);
        }

        Point canvasLoc = Perspective.getCanvasImageLocation(client, localPoint, Glyph, 120 + (int) (10 * Math.sin(client.getGameCycle() / 6.0)));
        if (canvasLoc != null)
        {
            OverlayUtil.renderImageLocation(graphics, canvasLoc, Glyph);
        }
      }

    // Return a copy of `src` with every pixel's alpha scaled by the global fade
    // factor, preserving each pixel's own RGB. Fades the glyph without shifting hue.
    static BufferedImage applyAlpha(BufferedImage src, int alpha)
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

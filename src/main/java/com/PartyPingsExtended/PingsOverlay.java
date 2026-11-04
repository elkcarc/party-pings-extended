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

@Singleton
class PingsOverlay extends Overlay
{
    // a non-fading glyph keeps its full opacity, but still has to disappear at
    // some point: it lives for roughly the same span a fading one would
    private static final long NO_FADE_LIFETIME_MS = 1000;

    private final Client client;
    private final PartyPingsExtendedPlugin plugin;
    private final PartyPingsExtendedConfig config;
    private final Glyphs glyphs;

    // tinted glyphs, built once per (type, color) and reused for every ping of
    // that kind; fading is done by the graphics context at draw time
    private final java.util.Map<String, BufferedImage> tintedGlyphs = new java.util.concurrent.ConcurrentHashMap<>();

    void clearGlyphCache()
    {
        tintedGlyphs.clear();
    }

    @Inject
    private PingsOverlay(final Client client, final PartyPingsExtendedPlugin plugin,
                         final PartyPingsExtendedConfig config, final Glyphs glyphs)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.glyphs = glyphs;
        setPosition(OverlayPosition.DYNAMIC);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        synchronized (plugin.getPendingTilePingsExtended())
        {
            final Iterator<PartyTilePingDataExtended> iterator = plugin.getPendingTilePingsExtended().iterator();

            // one composite swap for the whole frame instead of one per glyph
            final Composite original = graphics.getComposite();
            try
            {
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
                    if (config.fadeGlyphs())
                    {
                        next.setAlpha((int) Math.max(0, 255 - (elapsedTimeMillis / 4)));
                    }
                    else
                    {
                        next.setAlpha(elapsedTimeMillis >= NO_FADE_LIFETIME_MS ? 0 : 255);
                    }
                }
            }
            finally
            {
                graphics.setComposite(original);
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

        final BufferedImage Glyph = glyph(ping.getPingType(), ping.getColor());
        if (Glyph == null)
        {
            return;
        }

        final int alpha = ping.getAlpha();
        graphics.setComposite(alpha < 255
            ? java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha / 255f)
            : java.awt.AlphaComposite.SrcOver);

        net.runelite.api.Point canvasLoc = Perspective.getCanvasImageLocation(client, localPoint, Glyph, 120 + (int) (10 * Math.sin(client.getGameCycle() / 6.0)));
        if (canvasLoc != null)
        {
            OverlayUtil.renderImageLocation(graphics, canvasLoc, Glyph);
        }
    }

    // the glyph for a type, tinted to the ping's carried color when this client
    // recolors; cached per (type, color) so tinting runs once, not per frame
    private BufferedImage glyph(final PingType type, final Color color)
    {
        final BufferedImage base = glyphs.base(type);
        if (base == null || !config.recolorGlyphs() || !Glyphs.isRecolorable(type) || color == null)
        {
            return base;
        }
        final String key = type.name() + ":" + (color.getRGB() & 0xFFFFFF);
        return tintedGlyphs.computeIfAbsent(key, k -> tint(base, color));
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

}

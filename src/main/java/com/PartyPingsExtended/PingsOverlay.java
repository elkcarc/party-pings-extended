package com.PartyPingsExtended;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.ImageUtil;

@Singleton
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
    private final BufferedImage MAGE;
    private final BufferedImage RANGE;
    private final BufferedImage MELEE;

    // Glyphs tinted once per (type, sender color) and reused for every ping of
    // that pair, instead of converting pixels on every render frame.
    private final Map<String, BufferedImage> tintedGlyphs = new ConcurrentHashMap<>();

    // The four semantic glyphs that follow the sender's party color; attack,
    // defend, mage, range and melee keep their baked-in image colors.
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

    // Cached faded copies of glyphs, keyed by "glyphKey:alpha", so the per-step
    // fade is computed once per alpha step (16 steps: 255,239,...,15) instead
    // of per frame per ping. Cleared alongside the tint cache.
    private final Map<String, BufferedImage> fadedGlyphs = new ConcurrentHashMap<>();

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

        // glyph prepared once (base + optional tint) when the ping was created;
        // the faded copy is cached per alpha step, not rebuilt per frame
        final BufferedImage tinted = ping.getGlyph();
        if (tinted == null)
        {
            return;
        }
        final String glyphKey = ping.getPingType().name() + ":" + (ping.getColor().getRGB() & 0xFFFFFF);
        BufferedImage Glyph = faded(glyphKey, tinted, ping.getAlpha());

        net.runelite.api.Point canvasLoc = Perspective.getCanvasImageLocation(client, localPoint, Glyph, 120 + (int) (10 * Math.sin(client.getGameCycle() / 6.0)));
        if (canvasLoc != null)
        {
            OverlayUtil.renderImageLocation(graphics, canvasLoc, Glyph);
        }
    }

    private BufferedImage faded(final String key, final BufferedImage glyph, final int alpha)
    {
        final String k = key + ":" + (alpha / 16);
        final BufferedImage cached = fadedGlyphs.get(k);
        if (cached != null)
        {
            return cached;
        }
        final BufferedImage out = applyAlpha(glyph, (alpha / 16) * 16);
        fadedGlyphs.put(k, out);
        return out;
    }

    BufferedImage baseGlyph(final PingType type)
    {
        switch (type)
        {
            case ENROUTE:
                return ENROUTE;
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
                return ENROUTE;
        }
    }

    // build the ping's glyph once: base image, tinted to the given sender
    // color when recolorable, or the base itself otherwise
    BufferedImage prepareGlyph(final PingType type, final Color color)
    {
        final BufferedImage base = baseGlyph(type);
        if (config.recolorGlyphs() && isRecolorable(type))
        {
            final String key = type.name() + ":" + (color.getRGB() & 0xFFFFFF);
            return tintedGlyphs.computeIfAbsent(key, k -> tint(base, color));
        }
        return base;
    }

    void clearGlyphCache()
    {
        tintedGlyphs.clear();
        fadedGlyphs.clear();
    }

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
                out.setRGB(x, y, (a << 24) | (color.getRGB() & 0x00FFFFFF));
            }
        }
        return out;
    }

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

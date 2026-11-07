package glyph.server;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Byte-bounded LRU of encoded PNGs. Output is a pure function of the params, so entries never go stale. */
final class RenderCache {
    private final long maxBytes;
    private final LinkedHashMap<RenderParams, byte[]> map = new LinkedHashMap<>(256, 0.75f, true);
    private long bytes;

    RenderCache(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    synchronized byte[] get(RenderParams k) {
        return map.get(k);
    }

    synchronized void put(RenderParams k, byte[] png) {
        long cost = cost(k, png);
        if (cost > maxBytes / 4) return; // one huge image must not flush everything else
        byte[] old = map.put(k, png);
        if (old != null) bytes -= cost(k, old);
        bytes += cost;
        for (Iterator<Map.Entry<RenderParams, byte[]>> it = map.entrySet().iterator();
             bytes > maxBytes && it.hasNext(); ) {
            Map.Entry<RenderParams, byte[]> e = it.next();
            bytes -= cost(e.getKey(), e.getValue());
            it.remove();
        }
    }

    synchronized long bytes() { return bytes; }

    synchronized int entries() { return map.size(); }

    private static long cost(RenderParams k, byte[] png) {
        return png.length + 2L * k.text().length() + 200;
    }
}

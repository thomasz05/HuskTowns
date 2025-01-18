package net.william278.husktowns.hook.map;

import java.util.HashMap;
import java.util.Map;

public class TileFlags {
    private final Map<Long, long[]> chunkmap = new HashMap<>();

    private long last_key = Long.MAX_VALUE;
    private long[] last_row;

    public boolean getFlag(int x, int y) {
        long[] row;
        long k = (long) (x >> 6) << 32L | 0xFFFFFFFFL & (y >> 6);
        if (k == this.last_key) {
            row = this.last_row;
        } else {
            row = this.chunkmap.get(k);
            this.last_key = k;
            this.last_row = row;
        }
        if (row == null)
            return false;
        return ((row[y & 0x3F] & 1L << (x & 0x3F)) != 0L);
    }

    public void setFlag(int x, int y, boolean f) {
        long[] row;
        long k = (long) (x >> 6) << 32L | 0xFFFFFFFFL & (y >> 6);
        if (k == this.last_key) {
            row = this.last_row;
        } else {
            row = this.chunkmap.get(k);
            this.last_key = k;
            this.last_row = row;
        }
        if (f) {
            if (row == null) {
                row = new long[64];
                this.chunkmap.put(k, row);
                this.last_row = row;
            }
            row[y & 0x3F] = row[y & 0x3F] | 1L << (x & 0x3F);
        } else if (row != null) {
            row[y & 0x3F] = row[y & 0x3F] & (~(1L << (x & 0x3F)));
        }
    }

    public void clear() {
        this.chunkmap.clear();
        this.last_row = null;
        this.last_key = Long.MAX_VALUE;
    }
}

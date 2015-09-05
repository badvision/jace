// Copyright 2000-2005 the Contributors, as shown in the revision logs.
// Licensed under the Apache Public Source License 2.0 ("the License").
// You may not use this file except in compliance with the License.

package org.ibex.nestedvm.util;

// Based on the various org.xwt.util.* classes by Adam Megacz

public class InodeCache {
    private static final Object PLACEHOLDER = new Object();
    private static final short SHORT_PLACEHOLDER = -2;
    private static final short SHORT_NULL = -1;
    private static final int LOAD_FACTOR = 2;
    
    private final int maxSize;
    private final int totalSlots;
    private final int maxUsedSlots;
    
    private final Object[] keys;
    private final short[] next;
    private final short[] prev;
    private final short[] inodes;
    private final short[] reverse;
    
    private int size, usedSlots;
    private short mru, lru;
    
    public InodeCache() { this(1024); }
    public InodeCache(int maxSize) {
        this.maxSize = maxSize;
        totalSlots = maxSize*LOAD_FACTOR*2 + 3;
        maxUsedSlots = totalSlots / LOAD_FACTOR;
        if(totalSlots > Short.MAX_VALUE) throw new IllegalArgumentException("cache size too large");
        keys = new Object[totalSlots];
        next = new short[totalSlots];
        prev = new short[totalSlots];
        inodes = new short[totalSlots];
        reverse = new short[totalSlots];
        clear();
    }
    
    private static void fill(Object[] a,Object o) { for(int i=0;i<a.length;i++) a[i] = o; }
    private static void fill(short[] a, short s)  { for(int i=0;i<a.length;i++) a[i] = s; }
    public final void clear() {
        size = usedSlots = 0;
        mru = lru = -1;
        fill(keys,null);
        fill(inodes,SHORT_NULL);
        fill(reverse,SHORT_NULL);
    }
    
    public final short get(Object key) {
        int hc = key.hashCode() & 0x7fffffff;
        int dest = hc % totalSlots;
        int odest = dest;
        int tries = 1;
        boolean plus = true;
        Object k;
        int placeholder = -1;
        
        while((k = keys[dest]) != null) {
            if(k == PLACEHOLDER) {
                if(placeholder == -1) placeholder = dest;
            } else if(k.equals(key)) {
                short inode = inodes[dest];
                if(dest == mru) return inode;
                if(lru == dest) {
                    lru = next[lru];
                } else {
                    short p = prev[dest];
                    short n = next[dest];
                    next[p] = n;
                    prev[n] = p;
                }
                prev[dest] = mru;
                next[mru] = (short) dest;
                mru = (short) dest;
                return inode;
            }
            dest = Math.abs((odest + (plus ? 1 : -1) * tries * tries) % totalSlots);
            if(!plus) tries++;
            plus = !plus;
        }
        
        // not found
        int slot;
        if(placeholder == -1) {
            // new slot
            slot = dest;
            if(usedSlots == maxUsedSlots) {
                clear();
                return get(key);
            }
            usedSlots++;
        } else {
            // reuse a placeholder
            slot = placeholder;
        }
        
        if(size == maxSize) {
            // cache is full
            keys[lru] = PLACEHOLDER;
            inodes[lru] = SHORT_PLACEHOLDER;
            lru = next[lru];
        } else {
            if(size == 0) lru = (short) slot;
            size++;
        }
        
        int inode;
        OUTER: for(inode = hc & 0x7fff;;inode++) {
            dest = inode % totalSlots;
            odest = dest;
            tries = 1;
            plus = true;
            placeholder = -1;
            int r;
            while((r = reverse[dest]) != SHORT_NULL) {
                int i = inodes[r];
                if(i == SHORT_PLACEHOLDER) {
                    if(placeholder == -1) placeholder = dest;
                } else if(i == inode) {
                    continue OUTER;
                }
                dest = Math.abs((odest + (plus ? 1 : -1) * tries * tries) % totalSlots);
                if(!plus) tries++;
                plus = !plus;
            }
            // found a free inode
            if(placeholder != -1) dest = placeholder;
            break OUTER;
        }
        keys[slot] = key;
        reverse[dest] = (short) slot;
        inodes[slot] = (short) inode;
        if(mru != -1) {
            prev[slot] = mru;
            next[mru] = (short) slot;
        }
        mru = (short) slot;
        return (short) inode;
    }
    
    public Object reverse(short inode) {
        int dest = inode % totalSlots;
        int odest = dest;
        int tries = 1;
        boolean plus = true;
        int r;
        while((r = reverse[dest]) != SHORT_NULL) {
            if(inodes[r] == inode) return keys[r];
            dest = Math.abs((odest + (plus ? 1 : -1) * tries * tries) % totalSlots);
            if(!plus) tries++;
            plus = !plus;
        }        
        return null;
    }
    
    /*private void dump() {
        System.err.println("Size " + size);
        System.err.println("UsedSlots " + usedSlots);
        System.err.println("MRU " + mru);
        System.err.println("LRU " + lru);
        if(size == 0) return;
        for(int i=mru;;i=prev[i]) {
            System.err.println("" + i + ": " + keys[i] + " -> " + inodes[i] + "(prev: " + prev[i] + " next: " + next[i] + ")");
            if(i == lru) break;
        }
    }
    
    private void stats() {
        int freeKeys = 0;
        int freeReverse = 0;
        int placeholderKeys = 0;
        int placeholderReverse = 0;
        for(int i=0;i<totalSlots;i++) {
            if(keys[i] == null) freeKeys++;
            if(keys[i] == PLACEHOLDER) placeholderKeys++;
            if(reverse[i] == SHORT_NULL) freeReverse++;
        }
        System.err.println("Keys: " + freeKeys + "/" + placeholderKeys);
        System.err.println("Reverse: " + freeReverse);
    }
    
    public static void main(String[] args) throws Exception {
        InodeCache c = new InodeCache();
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(System.in)); 
        String s;
        boolean good = false;
        try {
            while((s = br.readLine()) != null) {
                if(s.charAt(0) == '#') {
                    short n = Short.parseShort(s.substring(1));
                        System.err.println("" + n + " -> " + c.reverse(n));
                } else {
                    //System.err.println("Adding " + s);
                    short n = c.get(s);
                    System.err.println("Added " + s + " -> " + n);
                    //c.dump();
                }
            }
            good = true;
        } finally {
            if(!good) c.stats();
        }
    }*/
}

package com.deem.zkui.utils;

import java.util.Properties;

/** Per-operation limits; no mutable budget is shared between requests. */
public final class TreeWork {
    public static final int PAGE_SIZE = 100;
    public static final int MAX_CHILDREN = 500000;
    public static final int MAX_NODES = 10000;
    public static final int MAX_DEPTH = 128;
    public static final long MAX_BYTES = 8L * 1024 * 1024;
    private final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
    private int nodes;
    private int discovered;
    private long bytes;

    public static final class LimitException extends IllegalStateException {
        public LimitException() { super("Tree operation exceeds its work limit; inspect state and select a smaller subtree"); }
    }

    public static void configureBuffer(Properties props) {
        String value = System.getProperty("jute.maxbuffer", props.getProperty("zkMaxBufferBytes", "8388608"));
        final int size;
        try { size = Integer.parseInt(value); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("ZooKeeper buffer must be an integer"); }
        if (size < 1048576 || size > 16777216) throw new IllegalArgumentException("ZooKeeper buffer must be between 1 and 16 MiB");
        System.setProperty("jute.maxbuffer", Integer.toString(size));
    }

    public void checkTime() {
        if (System.nanoTime() - deadline > 0) throw new LimitException();
    }

    public void visit(int depth) {
        checkTime();
        if (depth > MAX_DEPTH || ++nodes > MAX_NODES) throw new LimitException();
    }

    public void children(int count) {
        checkTime();
        if (count > MAX_NODES - discovered) throw new LimitException();
    }

    public void reserve(int count) {
        children(count);
        discovered += count;
    }

    public void bytes(int count) {
        bytes += count;
        if (bytes > MAX_BYTES) throw new LimitException();
        checkTime();
    }
}

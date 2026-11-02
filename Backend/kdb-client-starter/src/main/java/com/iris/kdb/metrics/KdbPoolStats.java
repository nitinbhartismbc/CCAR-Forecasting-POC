package com.iris.kdb.metrics;

public class KdbPoolStats {

    private int activeConnections;
    private int idleConnections;
    private int waitingThreads;
    private int maxPoolSize;

    public KdbPoolStats(int activeConnections,
                        int idleConnections,
                        int waitingThreads,
                        int maxPoolSize) {
        this.activeConnections = activeConnections;
        this.idleConnections = idleConnections;
        this.waitingThreads = waitingThreads;
        this.maxPoolSize = maxPoolSize;
    }

    public int getActiveConnections() {
        return activeConnections;
    }

    public int getIdleConnections() {
        return idleConnections;
    }

    public int getWaitingThreads() {
        return waitingThreads;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }
}
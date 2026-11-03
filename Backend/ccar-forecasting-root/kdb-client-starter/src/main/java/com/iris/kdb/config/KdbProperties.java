package com.iris.kdb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kdb")
public class KdbProperties {

    /**
     * KDB Host
     */
    private String host = "localhost";

    /**
     * KDB Port
     */
    private int port = 5001;

    /**
     * Username
     */
    private String username;

    /**
     * Password
     */
    private String password;

    /**
     * Maximum number of pooled connections
     */
    private int maxTotal = 20;

    /**
     * Maximum idle connections
     */
    private int maxIdle = 10;

    /**
     * Minimum idle connections
     */
    private int minIdle = 2;

    /**
     * Max wait time while borrowing connection
     */
    private long maxWaitMillis = 5000;

    /**
     * Validate connection before borrow
     */
    private boolean testOnBorrow = true;

    /**
     * Validate connection while idle
     */
    private boolean testWhileIdle = true;

    /**
     * Time between eviction runs
     */
    private long timeBetweenEvictionRunsMillis = 30000;

    /**
     * Min evictable idle time
     */
    private long minEvictableIdleTimeMillis = 60000;

    /**
     * AOP query logging settings (kdb.logging.*)
     */
    private final Logging logging = new Logging();

    public Logging getLogging() {
        return logging;
    }

    public static class Logging {

        /**
         * Enable the KDB query logging aspect
         */
        private boolean enabled = true;

        /**
         * Queries taking at least this long are logged at WARN
         */
        private long slowQueryThresholdMs = 1000;

        /**
         * Query text longer than this is truncated in logs
         */
        private int maxQueryLength = 500;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getSlowQueryThresholdMs() {
            return slowQueryThresholdMs;
        }

        public void setSlowQueryThresholdMs(long slowQueryThresholdMs) {
            this.slowQueryThresholdMs = slowQueryThresholdMs;
        }

        public int getMaxQueryLength() {
            return maxQueryLength;
        }

        public void setMaxQueryLength(int maxQueryLength) {
            this.maxQueryLength = maxQueryLength;
        }
    }

    // Getters & Setters

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getMaxTotal() {
        return maxTotal;
    }

    public void setMaxTotal(int maxTotal) {
        this.maxTotal = maxTotal;
    }

    public int getMaxIdle() {
        return maxIdle;
    }

    public void setMaxIdle(int maxIdle) {
        this.maxIdle = maxIdle;
    }

    public int getMinIdle() {
        return minIdle;
    }

    public void setMinIdle(int minIdle) {
        this.minIdle = minIdle;
    }

    public long getMaxWaitMillis() {
        return maxWaitMillis;
    }

    public void setMaxWaitMillis(long maxWaitMillis) {
        this.maxWaitMillis = maxWaitMillis;
    }

    public boolean isTestOnBorrow() {
        return testOnBorrow;
    }

    public void setTestOnBorrow(boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
    }

    public boolean isTestWhileIdle() {
        return testWhileIdle;
    }

    public void setTestWhileIdle(boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
    }

    public long getTimeBetweenEvictionRunsMillis() {
        return timeBetweenEvictionRunsMillis;
    }

    public void setTimeBetweenEvictionRunsMillis(
            long timeBetweenEvictionRunsMillis) {
        this.timeBetweenEvictionRunsMillis =
                timeBetweenEvictionRunsMillis;
    }

    public long getMinEvictableIdleTimeMillis() {
        return minEvictableIdleTimeMillis;
    }

    public void setMinEvictableIdleTimeMillis(
            long minEvictableIdleTimeMillis) {
        this.minEvictableIdleTimeMillis =
                minEvictableIdleTimeMillis;
    }
}
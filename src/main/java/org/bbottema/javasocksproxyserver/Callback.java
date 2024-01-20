package org.bbottema.javasocksproxyserver;

public interface Callback {
    abstract public boolean filter(java.net.InetAddress data);
    abstract public void error(String msg);
    abstract public void error(String msg, Exception e);
    abstract public void debug(String msg);
    abstract public void debug(String msg, Exception e);
    abstract public void info(String msg);
}
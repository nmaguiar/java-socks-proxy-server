package org.bbottema.javasocksproxyserver;

import java.util.Map;

public interface Callback {
    abstract public boolean exec(Map data);
    abstract public void error(String msg);
    abstract public void error(String msg, Exception e);
    abstract public void debug(String msg);
    abstract public void debug(String msg, Exception e);
    abstract public void info(String msg);
}
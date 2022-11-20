package org.bbottema.javasocksproxyserver;

import java.util.Map;

public interface Callback {
    public boolean exec(Map data);
    public void error(String msg);
    public void error(String msg, Exception e);
    public void debug(String msg);
    public void info(String msg);
}
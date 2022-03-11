package it.polimi.mwtech2022.project1.utils;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class LogUtils {

    public static final void setLogLevel() {
        Logger.getLogger("org").setLevel(Level.OFF);
        Logger.getLogger("akka").setLevel(Level.OFF);
    }

}
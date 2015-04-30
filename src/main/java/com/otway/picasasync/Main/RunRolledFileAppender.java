package com.otway.picasasync.Main;

import org.apache.log4j.RollingFileAppender;

/**
 This appender rolls over at program start.
 This is for creating a clean boundary between log data of different runs.
 */
public class RunRolledFileAppender extends RollingFileAppender
{
    public RunRolledFileAppender() { }

    @Override
    public void activateOptions() {
        super.activateOptions();
        super.rollOver();
    }

    @Override
    public void rollOver() { }

}
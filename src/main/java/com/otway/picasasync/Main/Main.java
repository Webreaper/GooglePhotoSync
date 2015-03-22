/*
    Copyright 2015 Mark Otway

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package com.otway.picasasync.Main;

import java.io.File;

import com.otway.picasasync.config.Settings;
import com.otway.picasasync.syncutil.SyncManager;
import com.otway.picasasync.ui.SyncTrayIcon;
import org.apache.log4j.*;

import javax.swing.*;

public class Main {

    private static final Logger log = Logger.getLogger(Main.class);

    public Main() {
        initLogging();

        setSystemLookAndFeel();

        try {
            Settings settings = new Settings();

            if( settings.loadSettings() ) {

                SyncManager manager = new SyncManager(settings);

                SyncTrayIcon trayIcon = new SyncTrayIcon();
                trayIcon.Initialise(settings, manager);

                log.info("Application started successfully.");

                // Allow interactive login the first time
                if( manager.initWebClient(true) ) {
                    manager.StartLoop();
                }
            }
            else
                log.error("Error loading settings.");

        } catch (Exception e) {
            log.error("Application startup failed.");
            e.printStackTrace();
        }
    }

    private void initLogging() {

        ConsoleAppender console = new ConsoleAppender(); //create appender
        String PATTERN = "%d [%p|%c|%C{1}] %m%n";
        console.setLayout(new PatternLayout(PATTERN));
        console.setThreshold(Level.INFO);
        console.activateOptions();
        Logger.getRootLogger().addAppender(console);

        FileAppender fa = new FileAppender();
        fa.setName("FileLogger");
        fa.setFile( new File( System.getProperty("user.home"), "PicaSync.log").toString() );
        fa.setLayout(new PatternLayout("%d %-5p [%c{1}] %m%n"));
        fa.setThreshold(Level.INFO);
        fa.setAppend(true);
        fa.activateOptions();

        Logger.getRootLogger().addAppender(fa);
    }

    private void setSystemLookAndFeel(){
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            //System.setProperty("apple.laf.useScreenMenuBar", "false");

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        new Main();
    }
}

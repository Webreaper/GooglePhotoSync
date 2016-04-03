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

package com.otway.picasasync.syncutil;

import com.otway.picasasync.ui.SyncTrayIcon;

import javax.swing.*;

/**
 * Synchronised object used to marshal updates and status
 * between the worker thread and the dispatcher thread
 */
public class SyncState {

    private final Object lock = new Object();

    private boolean cancelled;
    private String lastStatus;
    private boolean syncInProgress;
    private SyncTrayIcon trayIcon;
    private int totalDownloaded;
    private int totalUploaded;
    private int totalFailed;

    public void setTrayIcon( SyncTrayIcon trayIcon ){
        this.trayIcon = trayIcon;
    }

    public boolean getIsInProgress() {
        synchronized ( lock ){
            return syncInProgress;
        }
    }

    public void addStats( int downloaded, int uploaded, int failed )
    {
        synchronized( lock ){
            totalDownloaded += downloaded;
            totalUploaded += uploaded;
            totalFailed += failed;
        }
    }
    public boolean getIsCancelled() {
        synchronized ( lock ){
            return cancelled;
        }
    }

    public void setStatus( String msg ){
        synchronized ( lock ){
            lastStatus = msg;
        }

        updateGUIStatus( false );
    }

    public void start() {
        synchronized (lock ){
            totalDownloaded = 0;
            totalUploaded = 0;
            totalFailed = 0;
            cancelled = false;
            syncInProgress = true;
        }

        updateGUIStatus( false );
    }

    public void cancel( boolean withError ){
        synchronized ( lock ){
            cancelled = true;
            syncInProgress = false;
        }

        updateGUIStatus( withError );
    }

    private void updateGUIStatus( final boolean errorState ){

        if( trayIcon != null ) {

            final String msg;
            final String stats;
            final boolean inProgress;

            synchronized (lock) {
                msg = this.lastStatus;
                inProgress = this.syncInProgress;
                stats = String.format( "%d downloaded, %d uploaded, %d failed", this.totalDownloaded, this.totalUploaded, this.totalFailed);
            }

            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    trayIcon.setStatus(msg, stats, inProgress, errorState);
                }
            });
        }
    }
}

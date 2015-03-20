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

    public void setTrayIcon( SyncTrayIcon trayIcon ){
        this.trayIcon = trayIcon;
    }

    public boolean getIsInProgress() {
        synchronized ( lock ){
            return syncInProgress;
        }
    }

    public void addStats( int downloaded, int uploaded )
    {
        synchronized( lock ){
            totalDownloaded += downloaded;
            totalUploaded += uploaded;
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
                stats = String.format( "%d downloaded, %d uploaded", this.totalDownloaded, this.totalUploaded);
            }

            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    trayIcon.setStatus(msg, stats, inProgress, errorState);
                }
            });
        }
    }
}

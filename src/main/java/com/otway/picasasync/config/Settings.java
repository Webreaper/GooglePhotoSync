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

package com.otway.picasasync.config;

import org.apache.log4j.Logger;

import javax.swing.*;
import java.io.File;
import java.util.prefs.Preferences;

/**
 * General settings class for loading/saving prefs
 */
public class Settings {
    private static final Logger log = Logger.getLogger(Settings.class);
    private Preferences preferences;

    private static final String REFRESH_TOKEN = "RefreshToken";
    private static final String SYNC_FOLDER = "SyncFolder";
    private static final String DATE_RANGE = "DateRange";
    private static final String EXCLUDE_VIDEO = "ExcludeVideo";
    private static final String EXCLUDE_DROPBOX = "ExcludeDropBox";

    private static final String DOWNLOAD_NEW = "DownloadNew";
    private static final String DOWNLOAD_CHANGED = "DownloadChanged";
    private static final String UPLOAD_NEW = "UploadNew";
    private static final String UPLOAD_CHANGED = "UploadChanged";
    private static final String AUTOBACKUP_DOWNLOAD = "AutoBackupDownload";
    private static final String AUTOBACKUP_UPLOAD = "AutoBackupUpload";

    private File photoRootFolder;
    private String refreshToken;
    private Integer syncDateRangeDays;
    private Boolean excludeVideos;
    private Boolean excludeDropBox;

    public String getRefreshToken() { return refreshToken; }
    public File getPhotoRootFolder() { return photoRootFolder; }
    public int getSyncDateRange() {return syncDateRangeDays;}
    public boolean getExcludeVideos() { return excludeVideos; }
    public boolean getExcludeDropBox() { return excludeDropBox; }

    private Boolean downloadNew;
    private Boolean downloadChanged;
    private Boolean uploadNew;
    private Boolean uploadChanged;
    public boolean getDownloadNew() { return downloadNew; }
    public boolean getDownloadChanged() { return downloadChanged; }
    public boolean getUploadNew() { return uploadNew; }
    public boolean getUploadChanged() { return uploadChanged; }
    public void setDownloadNew(boolean downloadNew) {this.downloadNew = downloadNew; saveSettings();}
    public void setDownloadChanged(boolean downloadChanged) {this.downloadChanged = downloadChanged; saveSettings();}
    public void setUploadNew(boolean uploadNew) {this.uploadNew = uploadNew; saveSettings();}
    public void setUploadChanged(boolean uploadChanged) {this.uploadChanged = uploadChanged; saveSettings();}

    public void setRefreshToken( String token ) { refreshToken = token; saveSettings(); }
    public void setSyncDateRange( Integer range ) { syncDateRangeDays = range; saveSettings(); }
    public void setExcludeVideo(boolean excludeVideo) {this.excludeVideos = excludeVideo; saveSettings();}
    public void setExcludeDropBox(boolean excludeDropBox) {this.excludeDropBox = excludeDropBox; saveSettings();}

    private Boolean autoBackupDownload;
    private Boolean autoBackupUpload;
    public boolean getAutoBackupDownload() { return autoBackupDownload; }
    public boolean getAutoBackupUpload() { return autoBackupUpload; }
    public void setAutoBackupDownload(boolean autoBackupDownload) {this.autoBackupDownload = autoBackupDownload; saveSettings();}
    public void setAutoBackupUpload(boolean autoBackupUpload) {this.autoBackupUpload = autoBackupUpload; saveSettings();}

    public Settings() {
        preferences = Preferences.userNodeForPackage(Settings.class);
    }

    public boolean loadSettings() {

        boolean result = true;
        photoRootFolder = null;

        String prefsFolder = preferences.get( SYNC_FOLDER, null );
        if( prefsFolder != null )
            photoRootFolder = new File( prefsFolder );

        if( photoRootFolder == null || ! photoRootFolder.exists() ) {
            result = setPhotoRootFolder();
        }

        refreshToken = preferences.get( REFRESH_TOKEN, null );
        syncDateRangeDays = Integer.parseInt(preferences.get(DATE_RANGE, "365"));
        excludeVideos = Boolean.parseBoolean(preferences.get(EXCLUDE_VIDEO, "true"));
        excludeDropBox = Boolean.parseBoolean(preferences.get( EXCLUDE_DROPBOX, "false" ));
        downloadNew = Boolean.parseBoolean(preferences.get( DOWNLOAD_NEW, "true"));
        downloadChanged = Boolean.parseBoolean(preferences.get( DOWNLOAD_CHANGED, "true"));
        uploadNew = Boolean.parseBoolean(preferences.get( UPLOAD_NEW, "true"));
        uploadChanged = Boolean.parseBoolean(preferences.get( UPLOAD_CHANGED, "true"));
        autoBackupDownload = Boolean.parseBoolean(preferences.get( AUTOBACKUP_DOWNLOAD, "true"));
        autoBackupUpload = Boolean.parseBoolean(preferences.get( AUTOBACKUP_UPLOAD, "true"));

        log.info( "Settings loaded successfully.");
        return result;
    }

    public boolean setPhotoRootFolder() {

        // No folder, or the chosen folder isn't there. So prompt the user
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Root Folder for Sync");

        if( chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION )
        {
            photoRootFolder = chooser.getSelectedFile();

            log.info( "Sync folder set to:" + photoRootFolder );
            saveSettings();
            return true;
        }

        return false;
    }

    public void saveSettings() {
        preferences.put( SYNC_FOLDER, getPhotoRootFolder().toString() );
        preferences.put( DATE_RANGE, syncDateRangeDays.toString() );
        preferences.put( EXCLUDE_VIDEO, excludeVideos.toString() );
        preferences.put( EXCLUDE_DROPBOX, excludeDropBox.toString() );
        preferences.put( DOWNLOAD_NEW, downloadNew.toString());
        preferences.put( DOWNLOAD_CHANGED, downloadChanged.toString());
        preferences.put( UPLOAD_CHANGED, uploadChanged.toString());
        preferences.put( UPLOAD_NEW, uploadNew.toString());
        preferences.put( AUTOBACKUP_DOWNLOAD, autoBackupDownload.toString() );
        preferences.put( AUTOBACKUP_UPLOAD, autoBackupUpload.toString() );

        if( getRefreshToken() != null )
            preferences.put( REFRESH_TOKEN, getRefreshToken() );
        else
            preferences.remove( REFRESH_TOKEN );

        log.info( "Settings saved successfully.");
    }
}


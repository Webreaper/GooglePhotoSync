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

import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.photos.AlbumEntry;
import com.google.gdata.data.photos.PhotoEntry;
import com.google.gdata.util.ServiceException;
import com.google.gdata.util.ServiceForbiddenException;
import com.otway.picasasync.config.Settings;
import com.otway.picasasync.metadata.UniquePhoto;
import com.otway.picasasync.utils.FileUtilities;
import com.otway.picasasync.utils.TimeUtils;
import com.otway.picasasync.webclient.GoogleOAuth;
import com.otway.picasasync.webclient.PicasawebClient;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.otway.picasasync.utils.TimeUtils.getTimeFromMS;
import static com.otway.picasasync.utils.TimeUtils.sortSyncNewestFirst;

/**
 * Sync Manager Class - background worker thread implementation to
 * do the actual Sync work. Coordinates with the GUI thread.
 */
public class SyncManager {

    private static final String AUTOBACKUP_NAME = "Auto-Backup";
    private static final Logger log = Logger.getLogger(SyncManager.class);
    private final Settings settings;
    private final GoogleOAuth auth = new GoogleOAuth();
    private final SyncState syncState = new SyncState();
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final Object lock = new Object();
    private volatile boolean quit = false;
    private PicasawebClient webClient ;
    private AlbumEntry recycleAlbum;
    private final HashSet<String> deletedPhotos = new HashSet<String>();

    public void updateProgress( String msg ){ syncState.setStatus(msg); }
    public SyncState getSyncState() { return syncState; }
    public SyncManager( Settings settings ) { this.settings = settings; }

    public void shutDown() {
        log.warn("Shutting down background sync thread.");
        quit = true;
        executor.shutdown();
    }

    public void StartLoop() {

        log.info("Initialising background sync loop.");
        final int SYNC_FREQUENCY_MINS = 5;

        Runnable r = new Runnable() {
            public void run() {

                while (! quit ) {

                    try {
                        synchronized ( lock ){

                            lock.wait( SYNC_FREQUENCY_MINS * 1000 * 60 );
                        }

                        BeginCompleteSync();
                    }
                    catch( Exception ex )
                    {
                        log.error("Unexpected exception in background sync thread...", ex);
                    }
                }
            }
        };

        executor.submit(r);
    }

    public void startSync() {
        synchronized ( lock ){
            log.info( "Initiating sync now.");
            lock.notify();
        }
    }

    public void invalidateWebClient() {
        webClient = null;
    }

    public void BeginCompleteSync() {

        if( syncState.getIsInProgress() ) {

            log.warn("Sync started when already in progress. Doing nothing...");
            return;
        }

        LocalDateTime startDate = LocalDateTime.now();
        startDate = startDate.plusDays(-1 * settings.getSyncDateRange() );
        boolean endedWithError = true;

        log.info("Synchronisation started. Max photo age: " + startDate );

        if (SwingUtilities.isEventDispatchThread())
        {
            log.error("Sync started on GUI thread!");
            throw new RuntimeException("This method should not be run on the GUI thread");
        }

        try {
            syncState.start();

            File rootFolder = initFolder();

            syncState.setStatus("Starting synchronisation");

            // Do the actual sync
            Synchronise(rootFolder, startDate);

            syncState.setStatus("Sync complete");

            endedWithError = false;

        } catch( ServiceForbiddenException forbiddenEx ) {

            log.error("Auth expired. Discarding web client; will re-auth on next loop.");
            invalidateWebClient();
        }
        catch( UnknownHostException ex ){
            log.warn("Unknown host exception. Did we lose internet access?");
            // Cancel this sync, and we'll try again in a bit
            syncState.setStatus("Error finding Google.com. Sync Aborted.");
        }
        catch( SocketException ex ){
            log.warn("Socket exception. Did we lose internet access?");
            // Cancel this sync, and we'll try again in a bit
            syncState.setStatus("Connection error. Sync Aborted.");
        }
        catch( SocketTimeoutException ex ){
            log.warn("Socket timeout. Did we lose internet access?");
            // Cancel this sync, and we'll try again in a bit
            syncState.setStatus("Connection timeout. Sync aborted.");
        }
        catch (Exception ex) {
            log.error("Unexpected error: ", ex);
        } finally {
            log.info("Synchronisation ended.");
            if( endedWithError )
                syncState.setStatus("Sync failed.");
            syncState.cancel( endedWithError );
        }
    }

    private AlbumSync getAutoBackupWorkItem( File rootFolder ) throws IOException, ServiceException {

        List<AlbumEntry> autoBackup = webClient.getAlbums(false);
        AlbumEntry autoBackupEntry = null;

        for( AlbumEntry album : autoBackup )
        {
            String albumTitle = album.getTitle().getPlainText();
            String albumName = album.getName();
            log.info("Checking album: " + albumName );

            if( PicasawebClient.isInstantUpload(album))
            {
                if( albumName.equals( "Instant Upload" ) )
                {
                    autoBackupEntry = album;
                    break;
                }
            }
        }

        return  new AlbumSync( autoBackupEntry,
                new File( rootFolder, AUTOBACKUP_NAME),
                this, settings );
    }

    private void Synchronise(File rootFolder, LocalDateTime oldestDate) throws Exception {
        log.info("Querying picasa for album list...");

        if( ! initWebClient( false ) )
            return;

        List<String> exclusions = readExcludedAlbumsList( rootFolder);
        List<AlbumSync> workItems = new ArrayList<AlbumSync>();

        syncState.setStatus("Querying Google for album list");
        List<AlbumEntry> allRemoteAlbums = webClient.getAlbums(true);
        log.info(allRemoteAlbums.size() + " albums returned.");

        prepareRecycleAlbum( allRemoteAlbums );

        List<AlbumSync> albums = getRemoteDownloadList(allRemoteAlbums, rootFolder, oldestDate);

        // Get the single upload album for AutoBackup uploads
        if( settings.getAutoBackupUpload() )
        {
            AlbumSync autoBackupUpload = getAutoBackupWorkItem( rootFolder );
            workItems.add(autoBackupUpload);
        }


        if( syncState.getIsCancelled() )
            return;

        if( settings.getUploadNew() || settings.getUploadChanged() ) {

            // We're allowed to do uploads. Look for new folders to upload
            List<File> subFolders = getNewSubFolders( allRemoteAlbums, rootFolder );

            if (syncState.getIsCancelled())
                return;

            // Add new local sub-folders first - which will create a new online album
            for (File newFolder : subFolders) {

                if( exclusions.contains( newFolder.getName() ) )
                {
                    // use JNA FileUtils.moveToTrash here
                    continue;
                }

                // Prep a new album that we'll create remotely
                AlbumEntry album = new AlbumEntry();
                album.setTitle(new PlainTextConstruct(newFolder.getName() ));

                AlbumSync workItem = new AlbumSync(album, newFolder, this, settings);
                workItems.add(workItem);

                if (syncState.getIsCancelled())
                    return;
            }
        }

        for (AlbumSync album : albums)
        {
            if (settings.getExcludeDropBox() && album.getAlbumName().equals("Drop Box"))
                continue;

            if (exclusions.contains(album.getAlbumName()))
                continue;

            workItems.add(album);
        }

        // Sort into the newest items, based on local folder date
        sortSyncNewestFirst( workItems );

        log.info("================================================================");
        // Now, work through the list of actual jobs
        log.info("Preparing to process " + workItems.size() + " work items...");
        int failedAlbums = 0;

        for( AlbumSync syncLog : workItems )
            log.info(" " + syncLog.getAlbumName() + " (changed locally on " + syncLog.localChangeDate() + ")" );
        log.info("================================================================");

        for ( AlbumSync sync : workItems ) {

            //if( ! sync.getAlbumName().equals( "TestAlbum" ) )
            //    continue;

            try {
                sync.process( webClient, oldestDate, recycleAlbum );

                failedAlbums = 0;
            }
            catch( ServiceForbiddenException ex )
            {
                invalidateWebClient();

                if( ! initWebClient( false ) )
                {
                    // Reauth didn't work. Fail.
                    throw ex;
                }
            }
            catch( Exception ex ){

                log.warn("Exception processing album... continuing.", ex);
                failedAlbums++;
            }

            if( failedAlbums > 2 )
            {
                log.error("More than two sequential albums failed. Aborting sync.");
                syncState.cancel( true );
                break;
            }

            if( syncState.getIsCancelled() )
                return;
        }
    }

    private void prepareRecycleAlbum(List<AlbumEntry> allRemoteAlbums) throws IOException, ServiceException
    {
        for( AlbumEntry album : allRemoteAlbums )
        {
            if( album.getTitle().getPlainText().equals("Recycle Bin") )
            {
                recycleAlbum = album;
                allRemoteAlbums.remove( album );
                break;
            }
        }

        if( recycleAlbum == null )
        {
            recycleAlbum = new AlbumEntry();
            recycleAlbum.setTitle(new PlainTextConstruct("Recycle Bin"));
            recycleAlbum.setDescription(new PlainTextConstruct("Picasync Photos ready for deletion."));
        }
        else
        {
            // Get all of the unique IDs of the photos in the Recycle Bin
            // so we can skip any we see if another client tries to upload
            // from another PC.
            List<PhotoEntry> photos = webClient.getPhotos( recycleAlbum );

            for( PhotoEntry photo : photos )
            {
                // Enough date to uniquely identify a photo
                deletedPhotos.add( new UniquePhoto( photo ).getUniqueIdentifier() );
            }

        }
    }

    /*
        Carefully recycle any photos which have been tagged with 'delete'. We
        move the online versions to a 'Recycle Bin' album, so the user can
        manually delete once they've confirmed nothing important will be lost,
        and we move the local images to the recycle bin/trash for the same
        reason. That way, we don't actually delete anything.
     */
    public void recyclePhoto( ImageSync image ) throws IOException, ServiceException
    {
        // Check that the album exists, create it and save if it doesn't.
        recycleAlbum = webClient.prepareRemoteAlbum( recycleAlbum );

        if( recycleAlbum != null )
        {
            PhotoEntry photo = image.getRemotePhoto();

            try
            {
                if (photo != null)
                {
                    webClient.movePhoto(photo, recycleAlbum);
                    deletedPhotos.add( new UniquePhoto(photo).getUniqueIdentifier() );
                }

                log.info("Moving image " + image.getLocalFile() + " to trash");
                FileUtilities.moveToTrash(image.getLocalFile());
            }
            catch( Exception ex )
            {
                log.error("Unable to recycle photo.", ex );
            }
        }
    }

    private List<String> readExcludedAlbumsList( File rootFolder)
    {
        final String exclusionsFile = "exclude.txt";

        File excludeList = new File( rootFolder, exclusionsFile );

        if( excludeList.exists() )
        {
            try
            {
                List<String> exclusions = FileUtils.readLines(excludeList, "UTF-8");
                exclusions.remove(AUTOBACKUP_NAME);

                log.info(exclusions.size() + " albums were excluded via " + exclusionsFile);
                return exclusions;
            }
            catch (IOException ex)
            {
                log.warn("Unable to read exclusions file.", ex);
            }
        }

        return new ArrayList<String>();
    }

    public boolean initWebClient( boolean allowInteractive ) {

        log.info("Initialising Web client and authenticating...");

        if( webClient == null ) {

            try {
                webClient = auth.authenticatePicasa(settings, allowInteractive, syncState );
            }
            catch( Exception _ex ) {
                log.error( "Exception while authenticating.", _ex );
                invalidateWebClient();
            }

            if( webClient != null )
            {
                log.info("Connection established.");
            }
            else{
                log.warn("Unable to re-authenticate. User will need to auth interactively.");
            }
        }

        return webClient != null;
    }

    /*
    *  Process the list of albums in the cloud, creating the local folder path for each one.
    *  It's possible we may have duplicates where the titles are the same, but the album
    *  names are different. In this case, we post-fix the names with _1, _2, etc.
    */
    private List<AlbumSync> getRemoteDownloadList(List<AlbumEntry> remoteAlbums,
                                                          final File rootFolder,
                                                    LocalDateTime oldestDate )
                                        throws ServiceException, IOException
    {
        HashSet<String> uniqueNames = new HashSet<String>();
        List<AlbumSync> result = new ArrayList<AlbumSync>();

        // If this is false, we only care about instant upload albums.
        boolean nonInstantUploadAlbums = settings.getDownloadChanged() ||
                settings.getUploadChanged() ||
                settings.getDownloadNew() ||
                settings.getUploadNew();

        for (AlbumEntry album : remoteAlbums) {

            String title = album.getTitle().getPlainText();
            boolean isInstantUploadType = PicasawebClient.isInstantUpload(album);

            if( oldestDate.isAfter( getTimeFromMS( album.getUpdated().getValue() ) ) ) {
                log.debug("Album update date (" + album.getUpdated() + ") too old. Skipping " + title);
                continue;
            }

            if (!settings.getAutoBackupDownload() && isInstantUploadType)
            {
                log.info("Skipping Auto-Backup album: " + title);
                continue;
            }

            if( settings.getExcludeDropBox() && title.equals("Drop Box") )
            {
                log.info("Skipping DropBox album.");
                continue;
            }

            String suffix = "";

            if (uniqueNames.contains(title) )
            {
                log.info(" Duplicate online album: " + title + " (" + album.getName() + ") - skipping...");
                continue;
            }

            uniqueNames.add(title);

            // Might need to convert some auto-backup style folder names, which have slashes
            File albumFolder = PicasawebClient.getFolderNameForAlbum( rootFolder, album );

            if( ! isInstantUploadType && ! suffix.isEmpty() ) {

                // If it's not AutoBackup, add the suffix to differentiate duplicate titles
                albumFolder = new File( albumFolder.getParent(), albumFolder.getName() + suffix );
            }

            if( ! isInstantUploadType && ! nonInstantUploadAlbums )
                continue;

            result.add(new AlbumSync(album, albumFolder, this, settings ));
        }

        return result;
    }

    private File initFolder() throws IOException {

        File rootFolder = settings.getPhotoRootFolder();

        if (!rootFolder.exists()) {
            log.info("Creating root folder: " + rootFolder.getName());
            if (!rootFolder.mkdirs())
                throw new IOException("Unable to create root folder " + rootFolder);
        }

        return rootFolder;
    }

    private List<File> getNewSubFolders(List<AlbumEntry> albums, File rootFolder ) throws ServiceException
    {
        List<File> result = new ArrayList<File>();

        log.info("Looking for new local sub folders in " + rootFolder + "...");

        final HashSet<String> albumNameLookup = new HashSet<String>();
        for( AlbumEntry remoteAlbum : albums )
            albumNameLookup.add( remoteAlbum.getTitle().getPlainText() );

        File[] newFolders = rootFolder.listFiles(new FilenameFilter()
        {
            public boolean accept(File current, String name)
            {
                File file = new File(current, name);
                if (file.isDirectory() && !file.isHidden())
                {
                    if (!albumNameLookup.contains(file.getName()) &&
                            !file.getName().equals(PicasawebClient.AUTO_BACKUP_FOLDER))
                    {
                        return true;
                    }
                }
                return false;
            }
        });

        if( newFolders != null )
        {
            result = Arrays.asList( newFolders );

            TimeUtils.sortFoldersNewestFirst(result);
        }

        return result;
    }

    public boolean isDeleted(UniquePhoto up)
    {
        if( deletedPhotos.contains( up.getUniqueIdentifier() ))
            return true;

        return false;
    }
}

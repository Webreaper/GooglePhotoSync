package com.otway.picasasync.syncutil;

import com.google.gdata.data.photos.PhotoEntry;
import com.google.gdata.util.ServiceException;
import com.otway.picasasync.config.Settings;
import com.otway.picasasync.metadata.ImageInformation;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.sanselan.ImageReadException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static com.otway.picasasync.utils.TimeUtils.getTimeFromMS;

/**
 * File to Determine the change state of a local/remote image pair
 */
public class ImageSync
{
    public enum UpdateAction {
        none,
        download,
        upload
    }

    // Time difference allowed between local and remote
    // before triggering an upload or download
    private final int TIME_DELTA_SECS = 5;

    private final PhotoEntry remotePhoto;
    private final File localFile;
    private String localMd5CheckSum;

    public String getLocalMd5CheckSum() { return localMd5CheckSum; }
    public PhotoEntry getRemotePhoto() {
        return remotePhoto;
    }

    public File getLocalFile() {
        return localFile;
    }

    public ImageSync(PhotoEntry remotePhoto, File localFileName)
    {
        this.remotePhoto = remotePhoto;
        this.localFile = localFileName;
    }

    // Checks that neither the local file or remote file are newer
    // than a certain date. If either is newer, we'll consider it
    // for upload/download. If not, we'll skip it.
    public boolean newerThan( LocalDateTime threshold ) throws ServiceException
    {

        boolean isNewer = false;
        boolean localFileExists = localFile.exists();

        if( localFileExists )
        {
            LocalDateTime localTimeStamp = getTimeFromMS( localFile.lastModified() );

            // There's a local file. See if it's newer
            if( localTimeStamp.isAfter(threshold))
                isNewer = true;
        }

        if( remotePhoto != null )
        {
            LocalDateTime remoteTimeStamp = LocalDateTime.ofInstant(remotePhoto.getTimestamp().toInstant(), ZoneId.systemDefault());

            // There's a remote file. See if it's newer
            if( remoteTimeStamp.isAfter( threshold ))
                return true;
        }

        return false;
    }

    // Currently unused, but if Picasa ever fixed it so the checksum was
    // actually updated whenever an image changes, we could use this.
    private void generateMd5CheckSum()
    {
        final boolean USE_CHECKSUMS = false;

        if( USE_CHECKSUMS )
        {
            try
            {
                FileInputStream fis = new FileInputStream(localFile);
                localMd5CheckSum = org.apache.commons.codec.digest.DigestUtils.md5Hex(fis);
                fis.close();
            }
            catch( Exception ex )
            {
                log.warn("Unable to calculate MD5 checksum", ex);
            }
        }
    }
    /*
    * Figure out what we actually need to do.
    */
    public UpdateAction evaluateAction( Settings settings, boolean isAutoBackup ) {

        boolean canDownloadNew = settings.getDownloadNew();
        boolean canDownloadChanged = settings.getDownloadChanged();
        boolean canUploadNew = settings.getUploadNew();
        boolean canUploadChanged = settings.getUploadChanged();

        if( isAutoBackup ){
            canDownloadNew = settings.getAutoBackupDownload();
            canUploadChanged = canUploadNew = settings.getAutoBackupUpload();
        }

        UpdateAction action = UpdateAction.none;
        boolean hasLocalFile = localFile.exists();
        boolean hasRemoteFile = remotePhoto != null;
        boolean isNewFile = false;

        if( hasLocalFile )
            generateMd5CheckSum();

        if( hasLocalFile && hasRemoteFile )
        {
            // We have both. See if they're the same...
            if (remotePhoto.getChecksum() != null && remotePhoto.getChecksum().equals( localMd5CheckSum) )
            {
                // We have an MD5 checksum, and it matches. Happy days.
                log.debug("MD5 checksum matched. No action required");
                action = UpdateAction.none;
            }
            else
            {
                // There's a remote photo. See which is newer.
                LocalDateTime localMod = getTimeFromMS(localFile.lastModified());
                LocalDateTime remoteMod = getTimeFromMS(remotePhoto.getUpdated().getValue());
                long seconds = ChronoUnit.SECONDS.between(remoteMod, localMod);
                if( Math.abs( seconds ) > TIME_DELTA_SECS )
                {
                    if( seconds > 0 )
                    {
                        log.info("Image " + localFile.getName() + " is " + seconds + "s newer than remote image, so will be uploaded");
                        action = UpdateAction.upload;
                    }
                    else
                    {
                        log.info("Image " + localFile.getName() + " is " + seconds + "s older than remote image, so will be downloaded");
                        action = UpdateAction.download;
                    }
                }
            }
        }
        else
        {
            if( hasLocalFile )
            {
                // No remote file. It's a new file to upload
                action = UpdateAction.upload;
                isNewFile = true;
            }
            else if( hasRemoteFile )
            {
                // No local file. It's a new file to download
                action = UpdateAction.download;
                isNewFile = true;
            }
        }

        // Now we've established what the action we'd *like* to
        // do is, now see if we're actually allowed to do it.
        if( action == UpdateAction.download )
        {
            if (isNewFile )
            {
                if (!canDownloadNew)
                    action = UpdateAction.none;
            }
            else
            {
                if (!canDownloadChanged)
                    action = UpdateAction.none;
            }
        }
        else
        {
            if (isNewFile )
            {
                if (!canUploadNew)
                    action = UpdateAction.none;
            }
            else
            {
                if (!canUploadChanged)
                    action = UpdateAction.none;
            }
        }

        return action;
    }

    // TODO: Probably not used. Maybe refactor - if we care about image size
    private static final Logger log = Logger.getLogger( ImageSync.class );
    private boolean localCopyNeedsUpdating( PhotoEntry photo, File localPath )throws ImageReadException, IOException, ServiceException
    {
        boolean updateLocal = false;
        if( localPath.exists() ) {

            LocalDateTime localMod = getTimeFromMS( localPath.lastModified() );
            LocalDateTime remoteMod = getTimeFromMS(photo.getUpdated().getValue());
            long seconds = ChronoUnit.SECONDS.between(localMod, remoteMod);
            if( Math.abs( seconds ) > 1 )
                return true;

            long localFileSize = localPath.length();
            long remoteFileSize = photo.getSize();
            if( localFileSize != remoteFileSize )
            {
                log.info(String.format( "File sizes are different: (local %s vs remote %s). Local file will be updated.",
                        FileUtils.byteCountToDisplaySize(localFileSize), FileUtils.byteCountToDisplaySize( remoteFileSize) ));
                return true;
            }

            ImageInformation localInfo = ImageInformation.safeReadImageInformation(localPath);

            if (localInfo != null ) {

                Integer rotation = photo.getRotation();
                if( rotation != null )
                    log.info("PhotoEntry rotation was set!");

                // Make sure we take into account the rotation of the image when comparing width/height
                long localWidth = localInfo.getWidthHeightTransposed() ? localInfo.getHeight() : localInfo.getWidth();
                long localHeight = localInfo.getWidthHeightTransposed() ? localInfo.getWidth() : localInfo.getHeight();

                if (localWidth != photo.getWidth() || localHeight != photo.getHeight())
                {
                    log.info(String.format( "Image dimensions are different: (local %dx%d vs remote %dx%d). Local file will be updated.", localInfo.getWidth(), localInfo.getHeight(), photo.getWidth(), photo.getHeight() ));

                    return true;
                }
            }
            else
            {
                log.warn("Local file was not image! Renaming before overwrite. (" + localPath.getName() + ")");

                File renamed = new File( localPath + ".old" );
                if( ! localPath.renameTo( renamed ) )
                    log.warn( "Unable to rename file");

                updateLocal = true;
            }

        }
        else
        {
            log.debug("No local file existed: " + localPath );
            // Nothing here, so always write
            updateLocal = true;
        }

        return updateLocal;
    }


    // Todo - optimise the number of exists() calls to reduce I/O
    public String getName()
    {
        if( remotePhoto != null )
            return remotePhoto.getTitle().getPlainText();
        if( localFile.exists() )
            return localFile.getName();

        throw new IllegalArgumentException("No local or remote file. What?");
    }
}

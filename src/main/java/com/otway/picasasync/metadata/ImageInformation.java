package com.otway.picasasync.metadata;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.iptc.IptcDescriptor;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import com.sun.xml.internal.ws.api.wsdl.parser.MetaDataResolver;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

// Inner class containing image information
public class ImageInformation {

    static final Logger log = Logger.getLogger(ImageInformation.class);

    private final int orientation;
    private final int width;
    private final int height;
    private final boolean hasDeleteTag;
    private final Date dateTaken;
    private final String uniqueId;

    public boolean getWidthHeightTransposed() {
        return ( orientation == 6 || orientation == 8 || orientation == 7 || orientation == 5 );
    }

    public int getHeight() {
        return height;
    }

    public Date getDateTaken() { return dateTaken; }
    public String getUniqueId() { return uniqueId; }
    public int getOrientation() {
        return orientation;
    }
    public int getWidth() {
        return width;
    }
    public boolean getDeleteTag() { return hasDeleteTag; }

    public ImageInformation(int orientation, int width, int height, boolean hasDeleteTag, String uniqueId, Date dateTaken) {
        this.orientation = orientation;
        this.width = width;
        this.height = height;
        this.hasDeleteTag = hasDeleteTag;
        this.uniqueId = uniqueId;
        this.dateTaken = dateTaken;
    }

    public String toString() {
        return String.format("%dx%d,%d", this.width, this.height, this.orientation);
    }

    public static ImageInformation safeReadImageInformation( File imageFile )
    {
        ImageInformation info = null;
        try{
            info = readImageInformation( imageFile );
        }
        catch( Exception ex )
        {
            log.debug("Unable to get Exif info for file " + imageFile );

            SimpleImageInfo sii = SimpleImageInfo.getInfo(imageFile);
            if( sii != null )
            {
                info = new ImageInformation(1, sii.getWidth(), sii.getHeight(), false, "", getCreationTime( imageFile) );
            }
            else
                log.warn("Unable to get image information for " + imageFile );
        }

        return info;
    }

    public static ImageInformation readImageInformation(File imageFile)  throws IOException, MetadataException, ImageProcessingException {
        Metadata metadata = ImageMetadataReader.readMetadata(imageFile);
        ExifDirectoryBase exifDirectoryBase = metadata.getFirstDirectoryOfType(ExifDirectoryBase.class);
        JpegDirectory jpegDirectory = metadata.getFirstDirectoryOfType(JpegDirectory.class);
        IptcDirectory ipTCdirectory = metadata.getFirstDirectoryOfType(IptcDirectory.class);

        int orientation = 1;
        Date dateTaken = null;
        String uniqueID = "";

        if( exifDirectoryBase != null )
        {
            if( exifDirectoryBase.containsTag(ExifIFD0Directory.TAG_ORIENTATION))
                orientation = exifDirectoryBase.getInt(ExifIFD0Directory.TAG_ORIENTATION);

        }

        ExifSubIFDDirectory exifSubIFDDirectory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);

        if( exifSubIFDDirectory != null )
        {
            if( exifSubIFDDirectory.containsTag( ExifDirectoryBase.TAG_IMAGE_UNIQUE_ID ))
                uniqueID = exifSubIFDDirectory.getString(ExifDirectoryBase.TAG_IMAGE_UNIQUE_ID);

            if( exifSubIFDDirectory.containsTag( ExifDirectoryBase.TAG_DATETIME ))
                dateTaken = exifSubIFDDirectory.getDate( ExifDirectoryBase.TAG_DATETIME );

            if( dateTaken == null && exifSubIFDDirectory.containsTag( ExifIFD0Directory.TAG_DATETIME_ORIGINAL ))
               dateTaken = exifSubIFDDirectory.getDate(ExifDirectoryBase.TAG_DATETIME_ORIGINAL);

            if( dateTaken == null && exifSubIFDDirectory.containsTag( ExifIFD0Directory.TAG_DATETIME_DIGITIZED ))
                dateTaken = exifSubIFDDirectory.getDate(ExifDirectoryBase.TAG_DATETIME_DIGITIZED);

            if( dateTaken == null )
                dateTaken = getCreationTime( imageFile );
        }

        boolean hasDeleteTag = false;

        if( ipTCdirectory != null && ipTCdirectory.getKeywords() != null )
        {
            if( ipTCdirectory.getKeywords().contains("delete") )
            {
                hasDeleteTag = true;
            }
        }

        int width = jpegDirectory.getImageWidth();
        int height = jpegDirectory.getImageHeight();

        return new ImageInformation(orientation, width, height, hasDeleteTag, uniqueID, dateTaken );
    }

    private static Date getCreationTime( File imageFile )
    {
        try
        {
            Path path = Paths.get(imageFile.toString());
            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            return new Date( attr.creationTime().toMillis() );
        }
        catch( Exception ex ){
            log.warn("Unable to read creation datetime from " + imageFile);
        }

        return null;
    }
}
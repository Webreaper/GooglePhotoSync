package com.otway.picasasync.metadata;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.jpeg.JpegDirectory;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;

// Inner class containing image information
public class ImageInformation {

    static final Logger log = Logger.getLogger(ImageInformation.class);

    private final int orientation;
    private final int width;
    private final int height;

    public boolean getWidthHeightTransposed() {
        return ( orientation == 6 || orientation == 8 || orientation == 7 || orientation == 5 );
    }

    public int getHeight() {
        return height;
    }

    public int getOrientation() {
        return orientation;
    }

    public int getWidth() {
        return width;
    }

    public ImageInformation(int orientation, int width, int height) {
        this.orientation = orientation;
        this.width = width;
        this.height = height;
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
                info = new ImageInformation(1, sii.getWidth(), sii.getHeight() );
            }
            else
                log.warn("Unable to get image information for " + imageFile );
        }

        return info;
    }

    public static ImageInformation readImageInformation(File imageFile)  throws IOException, MetadataException, ImageProcessingException {
        Metadata metadata = ImageMetadataReader.readMetadata(imageFile);
        Directory directory = metadata.getDirectory(ExifIFD0Directory.class);
        JpegDirectory jpegDirectory = metadata.getDirectory(JpegDirectory.class);

        int orientation = 1;

        if( directory != null ){
            if( directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        }

        int width = jpegDirectory.getImageWidth();
        int height = jpegDirectory.getImageHeight();

        return new ImageInformation(orientation, width, height);
    }
}
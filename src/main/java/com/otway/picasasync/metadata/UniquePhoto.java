package com.otway.picasasync.metadata;

import com.google.gdata.data.photos.PhotoEntry;
import com.google.gdata.util.ServiceException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

/**
 * Created by markotway on 28/04/15.
 */
public class UniquePhoto
{
    public UniquePhoto( File localFile ) throws IOException
    {
        setFileName( localFile.getName() );

        Path fp = Paths.get(localFile.getPath());
        BasicFileAttributes attr = Files.readAttributes(fp, BasicFileAttributes.class);
        setDateTimeTaken( attr.creationTime().toMillis() );
    }

    public UniquePhoto( PhotoEntry photo )
    {
        try{
            setDateTimeTaken(photo.getTimestamp().getTime());
        }
        catch( Exception ex)
        {
            // don't care
        }
        setUniqueExifID(photo.getExifTags().getImageUniqueID());
        setCheckSum(photo.getChecksum());
        setFileName( photo.getTitle().getPlainText() );
    }

    public String toString() {
        return String.format("%s - %s (%s)", this.fileName, new Date( this.dateTimeTaken ).toString(), this.uniqueExifID );
    }
    public void setCheckSum(String checkSum)
    {
        this.checkSum = checkSum;
    }
    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }
    public void setUniqueExifID(String uniqueExifID)
    {
        this.uniqueExifID = uniqueExifID;
    }
    public void setDateTimeTaken(long dateTimeTaken)
    {
        this.dateTimeTaken = dateTimeTaken;
    }

    public String getUniqueIdentifier()
    {
        if( uniqueExifID != null )
            return uniqueExifID;

        return String.format( "%s_%d", fileName, dateTimeTaken );
    }


    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UniquePhoto that = (UniquePhoto) o;

        if (dateTimeTaken != that.dateTimeTaken) return false;
        return !(fileName != null ? !fileName.equals(that.fileName) : that.fileName != null);

    }

    @Override
    public int hashCode()
    {
        if( uniqueExifID != null )
            return uniqueExifID.hashCode();

        int result = fileName != null ? fileName.hashCode() : 0;
        result = 31 * result + (int) (dateTimeTaken ^ (dateTimeTaken >>> 32));
        return result;
    }

    private String checkSum;
    private String fileName;
    private String uniqueExifID;
    private long dateTimeTaken;
}

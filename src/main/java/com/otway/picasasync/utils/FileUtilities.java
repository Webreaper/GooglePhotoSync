package com.otway.picasasync.utils;

import com.sun.jna.platform.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * Created by markotway on 28/04/15.
 */
public class FileUtilities
{
    public static Date getLatestDatefromDir(File directory)
    {
        long newest = 0;

        if (directory != null)
        {
            File[] files = directory.listFiles();

            if (files != null)
            {

                for (File file : files)
                {
                    String ext = FilenameUtils.getExtension( file.getName() );
                    if( ext.equals( "ini") )
                        continue;

                    if( newest < file.lastModified() )
                        newest = file.lastModified();
                }
            }
        }

        return new Date( newest );
    }

    public static boolean moveToTrash(File fileToDelete)
    {
        FileUtils fileUtils = FileUtils.getInstance();
        if (fileUtils.hasTrash())
        {
            try
            {
                fileUtils.moveToTrash( new File[] { fileToDelete } );
                return true;

            } catch (IOException ioe)
            {
            }
        }

        return false;
    }
}
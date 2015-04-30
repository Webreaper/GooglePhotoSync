package com.otway.picasasync.utils;

import com.sun.jna.platform.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * Created by markotway on 28/04/15.
 */
public class FileUtilities
{

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
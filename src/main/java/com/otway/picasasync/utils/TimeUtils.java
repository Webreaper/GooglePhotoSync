package com.otway.picasasync.utils;

import com.google.gdata.data.photos.AlbumEntry;
import com.google.gdata.data.photos.PhotoEntry;
import com.otway.picasasync.syncutil.AlbumSync;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Wrapper for useful time utilities...
 */
public class TimeUtils {
    public static LocalDateTime getTimeFromMS( long milli ) {
        Instant instant = Instant.ofEpochMilli(milli);
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    public static void sortPhotoEntriesNewestFirst( List<PhotoEntry> photos )
    {
        Collections.sort(photos, new Comparator<PhotoEntry>()
        {
            @Override
            public int compare(PhotoEntry x, PhotoEntry y)
            {
                return y.getUpdated().compareTo(x.getUpdated());
            }
        });
    }

    public static void sortAlbumEntriesNewestFirst( List<AlbumEntry> albums )
    {
        Collections.sort(albums, new Comparator<AlbumEntry>() {
            @Override
            public int compare(AlbumEntry x, AlbumEntry y) {
                return y.getUpdated().compareTo(x.getUpdated());
            }
        });
    }

    public static void sortFoldersNewestFirst( List<File> files )
    {
        Collections.sort(files, new Comparator<File>()
        {
            @Override
            public int compare(File x, File y)
            {
                return Long.compare(y.lastModified(), (x.lastModified()));
            }
        });
    }

    public static void sortSyncNewestFirst( List<AlbumSync> sync )
    {
        Collections.sort(sync, new Comparator<AlbumSync>()
        {
            @Override
            public int compare(AlbumSync x, AlbumSync y)
            {
                return y.localChangeDate().compareTo( x.localChangeDate() );
            }
        });
    }
}

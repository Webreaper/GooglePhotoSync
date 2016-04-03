package com.otway.picasasync.picasaini;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.log4j.Logger;

/**
 * @author Ilya Boyandin
 */
public class PicasaIniParser {

    private static Logger logger = Logger.getLogger(PicasaIniParser.class.getName());

    private static final String[] PICASA_INI_FILENAMES = new String[] { ".picasa.ini", "Picasa.ini" };

    private static final Set<String> SERVICE_SECTIONS = new HashSet<String>(
            Arrays.asList("Picasa", "encoding"));

    private static final Pattern sectionPattern = Pattern.compile("^\\[(.+)\\]$");
    private static final Pattern propertyValuePattern = Pattern.compile("^([A-Za-z0-9\\-]+)=(.*)$");

    private final File picasaIni;
    private final Set<String> starred = new HashSet<String>();
    private String albumName;

    public PicasaIniParser(File folder) throws IOException {
        this.picasaIni = picasaIniFor(folder);
        if (picasaIni == null) {
            throw new IOException("Folder " + folder + " is not managed by Picasa");
        }
    }

    public static PicasaIniParser getPicasaIni( File folder )
    {
        try
        {
            return new PicasaIniParser( folder );
        }
        catch( IOException ex )
        {
            return null;
        }
    }

    public int getNumStarred() {
        return starred.size();
    }

    public String getAlbumName() {
        if (albumName != null)
            return albumName;
        else
            return picasaIni.getParentFile().getName();
    }

    public void parse() throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("Parsing '" + picasaIni.getAbsolutePath() + "'");
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(picasaIni), "utf-8"));

        try {
            String section = null, property, value;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0) {
                    continue;
                }

                if (section != null) {
                    Matcher vm = propertyValuePattern.matcher(line);
                    if (vm.matches()) {
                        property = vm.group(1);
                        value = vm.group(2);

                        if (section.equals("Picasa")) {
                            if (property.equals("name")) {
                                albumName = value;
                            }
                        } else
                        if (property.equals("star")  &&  value.equals("yes")  &&
                                !SERVICE_SECTIONS.contains(section)) {
                            starred.add(section);
                        }
                    }
                }

                Matcher sm = sectionPattern.matcher(line);
                if (sm.matches()){
                    section = sm.group(1);
                }
            }
        } finally {
            reader.close();
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Parsing finished");
        }
    }

    public boolean isStarred(String name) {
        return starred.contains(name);
    }

    public static boolean isManagedByPicasa(File folder) {
        return (picasaIniFor(folder) != null);
    }

    public static File picasaIniFor(File folder) {
        for (String fname : PICASA_INI_FILENAMES) {
            File iniFile = new File(folder, fname);
            if (iniFile.exists()) {
                return iniFile;
            }
        }
        return null;
    }

}
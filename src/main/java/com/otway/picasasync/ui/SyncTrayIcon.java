package com.otway.picasasync.ui;

import com.google.gdata.data.DateTime;
import com.otway.picasasync.config.Settings;
import com.otway.picasasync.syncutil.SyncManager;
import com.otway.picasasync.webclient.PicasawebClient;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;

public class SyncTrayIcon {
    private java.awt.TrayIcon trayIcon;
    private static final Logger log = Logger.getLogger(SyncTrayIcon.class);
    private SyncManager manager;
    private MenuItem syncMenuItem;
    private MenuItem statisticsMenuItem;
    private MenuItem statusMenuItem;
    private Settings settings;
    private ArrayList<CheckboxMenuItem> ranges = new ArrayList<CheckboxMenuItem>();

    private Image mainImage;
    private Image errorImage;
    private Image[] images;
    private int imageId = 0;
    private static long lastFrameChange = 0;

    public void Initialise( Settings settings, SyncManager manager ) throws IOException {

        if(!SystemTray.isSupported()){
            log.error("System tray is not supported !!! ");
            return;
        }

        this.settings = settings;
        this.manager = manager;
        this.manager.getSyncState().setTrayIcon( this );

        SystemTray systemTray = SystemTray.getSystemTray();

        loadImages();

        trayIcon = new TrayIcon( mainImage, "Picasa Sync" );
        trayIcon.setImageAutoSize(true);

        try{

            systemTray.add(trayIcon);
            setupPopupMenu("Idle");

        }catch(AWTException awtException){

            log.error("Unable to initialise System tray icon.");
            log.error( awtException );
        }
    }

    private void loadImages(){

        mainImage = new ImageIcon(SyncTrayIcon.class.getResource("/picasa.png")).getImage();
        errorImage = new ImageIcon(SyncTrayIcon.class.getResource("/picasa-err.png")).getImage();

        // Load the animations
        images = new Image[5];

        for( int i = 1; i <= 5; i++ )
        {
            URL url = SyncTrayIcon.class.getResource("/picasa-" + i + ".png");
            ImageIcon icon = new ImageIcon(url);
            images[i -1 ] = icon.getImage();
        }
    }

    private void setupPopupMenu( String statusMessage ){

        PopupMenu trayPopupMenu = new PopupMenu();

        syncMenuItem = new MenuItem("Synchronise Now");
        syncMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                if( manager.getSyncState().getIsInProgress() ){
                    log.info( "Sync cancelled via GUI");
                    manager.getSyncState().cancel(false);
                }
                else{
                    log.info("Sync started via System Tray");
                    manager.startSync();
                }
            }
        });

        trayPopupMenu.add(syncMenuItem);

        trayPopupMenu.addSeparator();

        createFolderMenu(trayPopupMenu);

        trayPopupMenu.addSeparator();

        createDownloadOptionsMenu(trayPopupMenu);
        createDateRangeMenu(trayPopupMenu);

        trayPopupMenu.addSeparator();

        statusMenuItem = new MenuItem( statusMessage );
        statusMenuItem.setEnabled( false );
        trayPopupMenu.add(statusMenuItem);

        statisticsMenuItem = new MenuItem( "No sync started." );
        statisticsMenuItem.setEnabled( false );
        trayPopupMenu.add( statisticsMenuItem );

        trayPopupMenu.addSeparator();

        MenuItem logoutMenuItem = new MenuItem("Log out of Picasa");
        logoutMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                settings.setRefreshToken( null );
                manager.invalidateWebClient();
                log.info( "Auth Refresh token cleared.");
            }
        });

        trayPopupMenu.add(logoutMenuItem);

        trayPopupMenu.addSeparator();

        MenuItem quitMenu = new MenuItem("Exit");
        quitMenu.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                manager.shutDown();
                System.exit(0);
            }
        });

        trayPopupMenu.add(quitMenu);

        trayIcon.setPopupMenu(trayPopupMenu);

        log.info("Tray popup menu initialised.");
    }

    private Menu createDownloadOptionsMenu(PopupMenu parentMenu) {
        Menu syncOptionsMenu = new Menu( "Sync Settings");
        parentMenu.add(syncOptionsMenu);

        final CheckboxMenuItem downloadNewMenu = new CheckboxMenuItem( "Download New Files");
        downloadNewMenu.setState(settings.getDownloadNew());
        downloadNewMenu.addItemListener(new ItemListener()
        {
            public void itemStateChanged(ItemEvent e)
            {
                log.info("Download New Toggled");
                settings.setDownloadNew(e.getStateChange() == ItemEvent.SELECTED);
            }
        });
        syncOptionsMenu.add(downloadNewMenu);

        final CheckboxMenuItem downloadChangedMenu = new CheckboxMenuItem( "Download Changed Files");
        downloadChangedMenu.setState(settings.getDownloadChanged());
        downloadChangedMenu.addItemListener(new ItemListener()
        {
            public void itemStateChanged(ItemEvent e)
            {
                log.info("Download Changed Toggled");
                settings.setDownloadChanged(e.getStateChange() == ItemEvent.SELECTED);
            }
        });
        syncOptionsMenu.add(downloadChangedMenu);


        final CheckboxMenuItem uploadNewMenuItem = new CheckboxMenuItem( "Upload new files");
        uploadNewMenuItem.setState(settings.getUploadNew());
        uploadNewMenuItem.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                log.info("Upload new Toggled");
                settings.setUploadNew(e.getStateChange() == ItemEvent.SELECTED);
            }
        });
        syncOptionsMenu.add(uploadNewMenuItem);

        final CheckboxMenuItem uploadChangedMenuItem = new CheckboxMenuItem( "Upload changed files");
        uploadChangedMenuItem.setState(settings.getUploadChanged());
        uploadChangedMenuItem.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                log.info("Upload changed Toggled");
                settings.setUploadChanged(e.getStateChange() == ItemEvent.SELECTED);
            }
        });
        syncOptionsMenu.add(uploadChangedMenuItem);

        syncOptionsMenu.addSeparator();

        createAutoBackupMenu(parentMenu);

        syncOptionsMenu.addSeparator();

        createExclusionsMenu(syncOptionsMenu);

        return syncOptionsMenu;
    }

    private void createAutoBackupMenu(PopupMenu parentMenu) {
        Menu autoBackupMenu = new Menu( "Auto Backup");
        parentMenu.add(autoBackupMenu);

        final CheckboxMenuItem downloadAutoBackupMenu = new CheckboxMenuItem( "Download");
        downloadAutoBackupMenu.setState(settings.getAutoBackupDownload());
        downloadAutoBackupMenu.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                log.info("AutoBackup download Enabled");
                settings.setAutoBackupDownload(e.getStateChange() == ItemEvent.SELECTED);
            }
        });
        autoBackupMenu.add(downloadAutoBackupMenu);

        final CheckboxMenuItem uploadAutoBackupMenu = new CheckboxMenuItem( "Upload");
        uploadAutoBackupMenu.setState(settings.getAutoBackupUpload());
        uploadAutoBackupMenu.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                log.info("AutoBackup upload Enabled");
                settings.setAutoBackupUpload(e.getStateChange() == ItemEvent.SELECTED);
            }
        });
        autoBackupMenu.add(uploadAutoBackupMenu);
    }

    private void createExclusionsMenu(Menu parentMenu) {
        final CheckboxMenuItem excludeVideoMenu = new CheckboxMenuItem( "Exclude Videos");
        excludeVideoMenu.setState(settings.getExcludeVideos());
        excludeVideoMenu.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                log.info("Exclude video Toggled");
                settings.setExcludeVideo(e.getStateChange() == ItemEvent.SELECTED);
            }
        });
        parentMenu.add( excludeVideoMenu );

        final CheckboxMenuItem excludeDropBoxMenu = new CheckboxMenuItem( "Exclude DropBox");
        excludeDropBoxMenu.setState(settings.getExcludeDropBox());
        excludeDropBoxMenu.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                log.info( "Exclude Exclude DropBox Toggled");
                settings.setExcludeDropBox(e.getStateChange() == ItemEvent.SELECTED);
            }
        });
        parentMenu.add(excludeDropBoxMenu);
    }

    private void createDateRangeMenu(PopupMenu trayPopupMenu) {
        Menu syncAgeMenu = new Menu("Date Range to Sync");
        addDateRangeMenuItem(syncAgeMenu, 7, "One Week");
        addDateRangeMenuItem(syncAgeMenu, 14, "Two Weeks");
        addDateRangeMenuItem(syncAgeMenu, 30, "One Month");
        addDateRangeMenuItem(syncAgeMenu, 90, "Three Months");
        addDateRangeMenuItem(syncAgeMenu, 180, "6 Months");
        addDateRangeMenuItem(syncAgeMenu, 365, "1 Year");
        addDateRangeMenuItem(syncAgeMenu, 365 * 5, "5 Years");
        addDateRangeMenuItem(syncAgeMenu, 365 * 10, "10 Years");
        trayPopupMenu.add(syncAgeMenu);
    }

    private void createFolderMenu(PopupMenu parentMenu) {
        Menu photoFolderOpenMenu = new Menu( "Folder");
        parentMenu.add(photoFolderOpenMenu);

        if (Desktop.isDesktopSupported()) {
            MenuItem openFolderMenuItem = new MenuItem("Open in Finder");
            openFolderMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    log.info("Exploring root folder");
                    try {
                        Desktop.getDesktop().open(settings.getPhotoRootFolder());
                    }
                    catch( Exception ex )
                    {
                        log.error("Unable to open folder: " + settings.getPhotoRootFolder() );
                    }
                }
            });

            photoFolderOpenMenu.add(openFolderMenuItem);
        }

        final MenuItem chooseFolderMenu = new MenuItem( "Select Download Folder");
        chooseFolderMenu.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                log.info("Choose download folder");
                settings.setPhotoRootFolder();
            }
        });
        photoFolderOpenMenu.add( chooseFolderMenu );
    }

    private void addDateRangeMenuItem( final Menu parent, final Integer termDays, final String description )
    {
        final CheckboxMenuItem child = new CheckboxMenuItem( description );

        child.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                log.info("Sync date range set to " + termDays + " (" + description + ")");
                settings.setSyncDateRange(termDays);
                for (CheckboxMenuItem menu : ranges) {
                    if( menu != child )
                        menu.setState(false);
                }
            }
        });

        child.setState(settings.getSyncDateRange() == termDays);
        parent.add(child);
        ranges.add( child );
    }

    public void setStatus( String status, String stats, boolean inProgress, boolean errorState ){

        statusMenuItem.setLabel(status);
        statisticsMenuItem.setLabel( stats );
        syncMenuItem.setLabel(inProgress ? "Cancel Sync" : "Synchronise Now");
        trayIcon.setToolTip( status );

        long timeNow = DateTime.now().getValue();

        if( inProgress ) {
            if( timeNow - lastFrameChange > 500 ){
                lastFrameChange = timeNow;

                trayIcon.setImage(images[imageId]);
                imageId = (imageId + 1) % 5;
            }
        }
        else
        {
            if( errorState )
                trayIcon.setImage(errorImage);
            else
                trayIcon.setImage(mainImage);
        }
    }
}
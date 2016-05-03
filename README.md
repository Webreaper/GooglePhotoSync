# PicasawebSync
## Two-way sync client for Google Picasa, for OSX.

### What does it do?

I found it annoying that the Google Picasa application doesn't just sync all my photos with the cloud - I have to manually download cloud albums to my Mac, and set the 'sync' flag on each album. If I forget, the albums won't be backed up to my Picasaweb account. I really want a background sync process which just silently and efficiently syncs photos up to the cloud and down to the Mac as they're modified or updated.

### How does it work?

If you've used the Google Drive application on OSX, you'll be familiar with this - it puts an icon in the menu toolbar, and works in the background synchronising your pictures. 

The first time you run it you'll need to pick the root folder of your albums, and log into Picasa (the app uses OAuth, so 2-Factor auth is supported). You can configure the behaviour (uploads only, downloads only, and whether to sync new, changed or all files, and whether to include Auto Backup albums) and then leave it running in the background. 

### Features

- Background Sync of your local photos to Picasaweb
- OAuth for Picasaweb login, including support for 2-Factor Auth
- Sync configuration Options:
- Download New files
- Download Changed files
- Upload New files
- Upload changed files
- Download and Upload Autobackup folder
- Exclude videos
- Exclude Drop Box folder
- Specify Date range for Sync

### Excluding albums from the Sync

If you want to skip certain albums from the sync, put a file called 'exclude.txt' in the root folder to be synced. Any album names matching entries in the file will be skipped.

### Running Headless

If you want to run this in a headless, non-GUI mode on a platform without JavaFX, you can do this by doing the following:

1. Run PicasawebSync on your Mac, and authenticate against Picasa/Google Photos
2. Use the 'Export settings' option, which will save a picasasync_settings.xml file in your photos folder
3. Download the PicasawebSync-headless.zip from the Github releases
4. Run the headless app, passing in the settings file which has the auth key and sync settings.

An example command-line for the headless app is:

    java -cp picasawebsync.jar:* com.otway.picasasync.Main.Main -settings=picasasync_settings.xml

Note that you should be cautious with the settings file, as the Auth key could allow somebody to access your Google Photos account. If you think it has been compromised, you should clear the token via the Google Account app settings screen.

### Disclaimer

I accept no liability for any data loss or corruption caused by the use of this application. Your use of this app is entirely at your own risk - please ensure that you have adequate backups before you use this software.


Software (C) Copyright 2015 Mark Otway

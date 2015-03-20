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

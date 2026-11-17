Warning: truncated output (original token count: 24579)
Total output lines: 1607

# سجل تحديثات ناش | NASH

## الاسم الجديد — متاح في المصدر

- اعتماد «ناش | NASH» للاسم الظاهر في التطبيق وقائمة المشاركة والإعدادات.
- الوصف: «تنزيل الفيديو والصوت».
- الشعار المصوّر ما زال يحمل الاسم السابق. تغيير الاسم لا يحل عائق توقيع تحديث النسخ المثبتة.


## 1.8.9.7 — تعديلات جاهزة، غير منشورة للتثبيت

- إشعار مختصر يعرض اسم المقطع ونسبته وشريط التقدم، دون سجل محرك التنزيل أو مسارات الملفات.
- إظهار 100% بعد معالجة الملف وحفظه.
- زر «مشاركة» واضح لكل ملف في التنزيلات، يدعم إرسال ملف واحد أو عدة ملفات عبر قائمة أندرويد.
- منع بناء Release باستخدام مفتاح Debug تلقائي. نشر تحديث متوافق مع النسخة الحالية متوقف على توفر مفتاح توقيعها الأصلي.

## 1.8.9.6

- فصل الخانات والقوائم والشريط السفلي عن الخلفية السوداء باستخدام أسطح رمادية واضحة.
- تحسين تباين النصوص والأيقونات وبطاقات خيارات المزيد.
- اسم حمّل بلون فاتح ولقطات جديدة من التطبيق.

## 1.8.9.5

- واجهة سوداء وشعار داخلي بخلفية سوداء.
- أربعة خيارات تنزيل مباشرة في «المزيد»، بدل سطر الأوامر والسجلات وقوالب الأوامر.
- قسم «حول» باسم نشمي ورقم الإصدار، دون روابط المجموعات والترجمة القديمة.
- توثيق التطبيق وسياسة الخصوصية وصور الواجهة.

## 1.8.9.4

- اعتماد شعار حمّل الجديد.
- إصلاح فحص تحديثات التطبيق وعدم عرض الإصدار المثبت مجددًا.
- تنزيل الفيديو عند مشاركة الرابط إلى حمّل وتحسين استقبال الروابط والأذونات.

---

## سجل المشروع الأصلي

الملاحظات التالية تخص إصدارات YTDLnis التي بُني عليها المشروع، ولا تمثّل أرقام إصدارات حمّل.

# YTDLnis Changelog

> # 1.8.9.1 (2026-06)

# What's Changed

## Note
If app is crashing when searching for something on home screen you are facing a newpipe extractor bug. Change the data fetching extractor to yt-dlp in general settings to resolve it

## Other urgent fixes
- Fix bug in history deleting whole history list 💀
- Update newpipe extractor to snapshot
- Add reset button in crop bottom sheet
- Fix bug in history when deleting selected item, deletes whole folder in sd card

> # 1.8.9 (2026-06)

# What's Changed

## Video cropping

Now in the video section of the download card u can crop a video however you like and the app applies a ffmpeg postprocessor with yt-dlp. I have to note that in vp9 and av1 formats cropping might need higher versions of ffmpeg, so i recommend upgrading to ffmpeg 7.1.1 from the packages settings.

## Subfolder download preference

I have to tell people that the app has a filename template system where u can have endless possibilities on how to name your files, but for some people they just need basic structure so i added this. U can select playlist name, website and download type

## Other stuff

- Fix webview not working for titkok and facebook
- Make webview go back when swiping back on the device
- Enabled by default download over metered networks
- Show progress in the updating app dialog
- Fix app inverting the order of items when restoring backup
- Added placeholder url in po token generation
- Validate url properly for the cookies screen
- fix app using search query for playlist url when use original url as playlist url toggle is enabled
- disable some website search options (nico video, google video) as they are broken
- fix parse metadata playlist name
- Fix The cache folder preference can't be interacted immediately (remains grayed out) after you grant all files perms
- remove reduntant -c:v libx264 -c:a aac -profile:v baseline for compat mode
- Use codec in -S instead of vcodec and acodec
- enable embed metadata in video downloads too
- remove yt-dlp from /raw to make fdroid buildable
- fix format card ui to have wrapping text instead of being cut off
- other stuff i forgor

> # 1.8.8 (2026-03)

# What's Changed

## Package Integration

This was long overdue, and the next progression from this app. Instead of releasing new versions of the app when packages like python, ffmpeg etc need upgrades, now the app can manage both bundled and downloadable packages.
Packages will be published in a separate repository.

You can also import zip if you have it locally.
Currently what is published is nodejs 25.3.0 and previous versions of ffmpeg and python.
I downgraded ffmpeg to 7.0.1 for this release to reduce filesize but also to resolve issues with users who cant run higher version ffmpeg.
ffmpeg 7.1.1 is available to download of course.

Since beyond skd28 downloadable executables can't execute, the only way to make them executable is if they are part of jniLibs inside the app OR inside another app. So now downloadable packages are shipped as apks u can install. The app will immediately recognize them.

The package apks are simply empty apps with jniLibs folder in them containing libxxx.so and libxxx.zip.so. libxxx.zip.so are extracted within the ytdlnis internal directory, and libxxx.so is accessible throughout the android system.

## YoutubeDL-Android rewrite

Since the package integration, the library had to undergo many changes so i decided to rewrite everything and integrate it within the app. Also fixed some other issues. In future releases i will have more control how commands are executed.

## Android 16 Live Notifications

Now the app supports the new live notification feature for download jobs. If you have concurrent downloads, it will make separate live notifications for each.

## Everything else
- Remove error code 100 from success codes, it was causing errored downloads to not go to errored screen
- Made app not hardcode @latest when using custom yt-dlp update repository
- fix beta1 crashing when sharing link to the app, sorry lol
- fix app putting premerged videos before video only formats when fetching from newpipe
- made app pre-sort audio formats from newpipe by highest bitrate, for default users
- fix app not applying embed thumbnail in compatible mode for some reason
- add AI policy to contributing.md
- add feature to reapply old codec and video container preferences when disabling compatible mode
- remove redundant +br +fps +res from worst audio in quick download
- fix app showing repeated aria2c logs in logs screen
- made app give more importance to preferred language than prefer drc audio on quick download
- made use_app_language_for_metadata false by default
- fix ytdlp not auto downloading when app starts and erroring out, made it wait for runtime to start
- fix exporting the temp files generates a folder named "0" and an extra "storage/emulated/0/…" directory inside, LOL
- fix app not showing generic format ids in format selection screen, without restarting app
- made cache export only export temporary download files and not other config files like infojsons or other ytdlp temp files
- add --replace-in-metadata "playlist,playlist_title" "^.*$" "NAME" if use item url instead of playlist url is applied
- add %(rownumber)s as metadata instead of modifying autonumber start. use that in filename template to do the same thing as autonumber when downloading multiple items at once
- add 968kbps for audio bitrate setting
- merge thumbnail chips in audio download in a single dialog to save space
- Add remove audio as preference in the settings
- Add also download as audio as preference in the settings
- fix app not adding trailing spaces when adding command templates in the download card
- remove hardcoded --no-cache-dir from youtubedl-android
- add vorbis to audio containers
- some metadata command rework for title and author
- for errored downloads, long press redownloads and press sends to download card
- delete info json files after updating yt-dlp
- tried fixing po token no auth generation, lmk
- add badge to the filters in download history fragment
- fix app sometimes showing "watch" in result titles
- apply prefer drc audio format in quick downloads for video tab
- move use header from cookies screen to advanced since UA overriding breaks the tabs (playlist, releases, etc.) extraction, too
- add write info json when fetching formats aswell
- fix preference compatible video not applying in download card
- enforce avc and acc codecs when enabling compatible video
- made double tap of the downloads screen to open the search bar
- update to newpipe 0.25.2
- use --ppa VideoConvertor+ffmpeg_o:-c:v libx264 -c:a aac -profile:v baseline for compatibility mode toggle, should now be truly compatible lol
- apply prefer drc preference even in quick download
- truncate match filter logs to prevent crashes
- add --cache-dir always on downloads
- Fix app applying replace in metadata title for playlist items when quick downloaded and data was fetched after a while
- dont apply write info json on yt playlist links
- add back btn to webview in the menu
- add green and blue icons
- remove app autofocusing on the command textview in the command tab

> # 1.8.7.2 (2026-01)

# What's Changed

## F-Droid integration

- disabled auto-update of the app. (I will try to make build flavors in the following release so i can re-enable it for just github releases)
- Added Queue as default tab in the bottom navigation
- Add Hook in sponsorblock categories
- More translations

> # 1.8.7.1 (2026-01)

# What's Changed

## Filename Template Live Preview & Info JSON

Brought back info json feature to prevent the app from calling servers twice. Also with all the metadata stored in a file, i added the feature to preview the filename template.
You can turn off using info json if you'd like in advanced settings.

## Icon Picker

You can select the icon and not have it update depending on theme. I am open to custom icons if you'd like, ill add them to the app.
Looking at you decipher :P

## Other stuff

- Fix app closing webview when leaving the app. You have to return from recents and not relaunch the app.
- Add time to the backup filename
- Reversed don't prefer DRC audio to prefer DRC audio preference
- Fix items in navigation rail in landscape being cut off
- Fix youtube playlist link fetching being null
- Add Hausa language
- Add intent for quick download in the share menu. You can enable it in the general settings. This ignores the download card and downloads immediately.
- Consider "creators" field when parsing author in the app
- Add youtube charts support for newpipe home recommendations
- Fix observe sources items not showing properly in light mode

> # 1.8.7 (2025-11)

# What's Changed

## Add QuickJS runtime

Implements yt-dlp's --js-runtimes command to help with many website challenges while fetching data and downloading.
Hopefully with this many of the current issues are resolved

- Upgrade to ffmpeg 7.1.1
- Fix hardcoded user id in path so it supports multiple users
- fix --cache-dir not properly formatted when used as command template
- Add toggle to always update yt-dlp before downloading

> # 1.8.6 (2025-10)

# What's Changed

- Fix app sometimes giving you dubbed audio format instead of original audio
- Fix app crashing sometimes when pasting a link to terminal, also btw u can enable color highlight if disabled, wont crash the app i guess
- Make sponsorblock preference clear by default
- For playlists app now uses --lazy-playlist to load large playlists continuously in pages like newpipe does, its faster
- Enable / Disable certain cookie records instead of all at once
- Add option to write cookie description or label
- Add GIF to video containers
- Don't apply sponsorblock api url if sponsorblock is disabled
- Add --no-check-certificates command when aria2 is applied
- Add embed thumbnail toggle in adjust video chips
- Some video chips are combined into one with hidden dialog menu when clicked to save space
- Add badges on the adjust audio/video chips with hidden menus so u can know how many settings are active on each of them
- Don't apply generated po tokens when cookies are disabled
- remove data sync id fetch from normal cookies webview, only in po token webview
- Add filename template selection in the terminal screen
- Add toggle to not prefer DRC Audio formats
- Add ability to long press the terminate button to show the warning dialog even if u checked to not show it
- Add warning in the download card if selected format is too big (if format size is known, also this is an approximate as format size cou…18579 tokens truncated…nd File Sizes on Results
- Added ability to change download path from the download card
- Combined Audio / Video / Command in a single card through tabs
- Added ability to download multiple vids at the same time (Limit is 5 for now)
- Added ability to schedule downloads. You can put the date and time.
- Fixed File Scanning
- Made downloads download to temporary folders. Each version of the file depending on the url and format type has its own folder. If you cancel a download and restart it, it will resume the downloads based on the correct format. After download, temp folder gets axed.
- Fixed downloads not getting cancelled sometimes
- Added ability to update yt-dlp from a nightly build
- Changed search suggestion provider to google's own api
- Added more sorting options in the history fragment
- Implemented Language switcher in the app. For devices on Android 13 and up, go to App Details and change it there
- Added material date and time pickers
- Updated bottom sheet design
- Added separate format selection card
- Implemented bottom card in the share menu instead of opening the whole app. If its a txt file it will open the app
- Added m-time as a preference
- Added ability to get result item formats if you searched for it or if it is a playlist item since they don't have formats
- Added Download Logs
- Added Search History
- Added Separate Download Queue Screen. (Double Click the download icon or go to more section to access)
- Fixed bugs on terminal mode
- Fixed downlaod logs crashing on older apis
- Added long press to open download card even if the setting is disabled
- Fixed bug of download card duplicating when switching formats in share activity
- Implemented Custom Command Tab in the download card. (Disabled by default. You need to create at least one command template in the more section)
- Added tabs to download queue section (running, queued, cancelled, errored)
- Added ability to update custom command right from the download card & modify the chosen one just for that download
- Fixed search history storing duplicate queries
- Implemented Preferred Download Type. On Share Activity or when searching for one result, it will open the preferred tab on the download card
- Implemented processing multiple items from a playlist in the share activity. You can select a portion of them, then you can modify each item just like you would on a normal link. You can change the download directory for all items at once, and you can schedule all of them like a normal download. You can also change their type to audio/video/command all at once
- Fixed Download All button not showing up after reopening app with a playlist as result
- Added copy log in the download log screen
- Fixed download logs not properly live scrolling
- Added ability to retry cancelled downloads
- Added ability to open logs directly from the list of errored downloads (if logs were enabled for that download at the time)
- Showed link copied to clipboard as item suggestion in searchview

> # 1.4.9 (2023-01)

# What's Changed
Fixed results duplicating when reopening the app
Fixed no results icon and app icon in more section not showing as good in smaller phones
Fixed white space on bottom nav bar #17
Fixed opus songs not saving thumbnail
Added Share Intent for Custom Command. You can find it with the name 'Run Command'
Made app create its own folder in the downloads directory since the app wouldn't download properly on some devices on a fresh install
Added new Albanian Strings from Hosted Weblate by @weblate in #15
Added new Japanese Strings from Hosted Weblate by @weblate in #16
Added Russian (thank you AntonDzutstsati) from Hosted Weblate by @weblate in #18
# Info
Future updates of this app might not be as often since im focusing on my studies. Feel free to open issues anyway, if you need to. I will check them out when i find the time

> # 1.4.8 (2023-01)

# What's Changed
fixed app not recognising youtube shorts links
fixed app sometimes not removing the downloading card from the downloads tab when it is cancelled
converted more components to kotlin

> # 1.4.7 (2023-01)

# What's Changed
added other sponsorblock categories you can remove from the download. You can select multiple of them too
added fast youtube playlist data fetching without an API key through invidious
fixed the list flashing when loading a large playlist or when sharing a text file to the app with multiple lines

> # 1.4.6 (2023-01)

# What's Changed
added invidous as another method for fetching youtube data faster. This removes the issue of having to make an API key but for some countries you won't be able to get trending videos.
added search suggestions

> # 1.4.5 (2022-12)

# What's Changed
fixed app not downloading files with some bad characters coming from twitter
added worst video quality as selection & default video format
Added Polish from Hosted Weblate by @weblate in #12

> # 1.4.4 (2022-12)

# What's Changed
Added Japanese (thank you HiSubway) from Hosted Weblate by @weblate in #9
Added check for permissions when opening the app

> # 1.4.3 (2022-12)

Added Turkish Language (thank you TRojen610)

> # 1.4.2 (2022-11)

Uninstall the app before updating. Singing key changed
New Features
Added Norwegian Bokmål [incomplete] (thank you comradekingu)
Added German Language (thank you eUgEntOptIc44)
Added French [incomplete] (thank you eUgEntOptIc44)

> # 1.4.1 (2022-10)

fixed app crashing when using links that don't have data like thumbnail, title author etc.
hid download all button when using single links through the search bar

> # 1.4.0 (2022-10)

# PLEASE READ

Due to high app usage, my personal youtube api key was throttled. I removed the built-in api key for default yt-dlp requests. They are slower but usable to everyone. You can add your own youtube api key in the setttings for faster responses. Refer to the readme for steps on how to get your own key. 

## NEW FEATURES

- made clicked audio/video buttons cancellable so you dont have to cancel everything
- made progress bar as thick as the card and somewhat transparent, looks nicer
- made the download progress not cut off and be seamless when transitioning from intermediate to solid colors
- renamed history tab to downloads
- downloaded items if they are not in the filesystem, they are greyed out
- added sorting and filtering chips on top of the downloads tab. You can filter between each website, file type and sort from oldest or newest
- made checking for updates as a toggle in the main settings page.
- downloading items now show in the downloads tab too and you can cancel them there aswell.
- added incognito mode. If on, downloaded stuff wont be added to history / downloads tab
- added a cancel button in the notification itself. It will cancel everything and clear the queue
- added bottom cards when you click audio / video buttons to configure some settings before downloading. You can change title and artist name, change format, and quality if you are choosing video. If you have selected mutliple items or are downloading everything present, you can't edit title and artist because you are dealing with multiple items.
- added a button to open the downloaded file. If the file is greyed out (file is not present) the button won't be there
- added ability to import a txt file. Create a simple txt file contaning any link that yt-dlp supports. You can also use youtube playlist links or simple queries. The app will read each line and update the screen. When finished, download all floating action button will appear. To use this feature just share the txt file to this app

## FIXES
- fixed downloaded items other than youtube not having proper download links
- fixed app crashing when pressing download all button
- fixed not getting all playlist items from generic ytdl request

> # 1.3.1 (2022-09)

added app icon (thank you decipher3114), adaptive depending on system theme
fixed app crashing when you multiselect and unselect
removed utc from history
updated mp3 and mp4 tags to audio and video

> # 1.3.0 (2022-09)

changed folder structure to ytdlnis instead of ytdl
when downloads are finished, history fragment updates itself
changed video duration of livestreams to LIVE
Fixed update dialog showing when you dont have internet
removed download functionality from home fragment and put it all on downloadservice. This makes the download stateless and the ui can be updated when you go back to the app
did the same for custom command activity
slight changes on result card ui
added fastlane metadata
added support for other yt-dlp videos instead of just focusing on youtube. Youtube is still the main focus
updated database
slight design changes
made bottom navigation bar the same color as the background
changed download buttons background colors inside cards
fixed result items changing download status icons when scrolling through recyclerview

> # 1.2.1 (2022-09)

Fixed file dates to be time of creation instead of time of upload
Putting url's on the search bar will clear the previous results and not append
fixed issue if you didn't give storage permission to the app, custom command would fail. Now it asks beforehand
added cancel floating action bar on custom command

> # 1.2.0 (2022-09)

# New Features

Added SponsorBlock Functionality. You can remove non music parts from audio files.
You can make the app put chapters to a downloaded video using sponsorblock / youtube chapters.
Downloading now happens as a background service so even if you kill the app, it will continue to download.
If you are downloading and press another item, it will get added to the queue instead of not doing anything
Converted Lists to Recyclerviews for more performance. The app can now load playlists with Thousands of items without breaking a sweat.
Implemented alot of other notable yt-dlp features as preferences
App can self-update and can even check if there is a new update every time you open the app
You can download partitions of a playlist too instead of just downloading all
You can select videos by long pressing one and then pressing the others to download just those as audio or video

# User Interface 

Fixed Time Formatting
Removed Hardcoded Strings
Ordered History items from new to old
Show no results image when history is empty
Separate Activity for Custom Commands (Do not write the output path, instead configure it in settings)
-Removed download all card, and replaced it with a Floating Action Button
Added floating action buttons for selected items

> # 1.1.0 (2022-08)

removed programmatical creation of cards and instead inflated them through layouts
created shimmer card effects when cards are loading from the api
made api calls in threads so that the app won't hang loading tons of data
made buttons adapt to Material U colors
made result cards show which videos are downloaded already by changing the download icons
showed video duration in result files
added trending videos if results are empty, based on your location
made the cards 16:9 no all devices
removed old menubar and now it blends with the statusbar
When switching to light mode, the status bar now changes as expected
added download progress notification
removed unnecessary toasts
fixed card scrolling when downloading a playlist
added floating action button to scroll to top
made appbar clickable that will also scroll to top
made bottom nav bar buttons scroll to top if you are clicking the highlighted button
fixed text sizes and positions
removed youtubedl-android folders and instead used modules (it now works, previously i had to manually add them)
removed hard coded strings which most were albanian. Now they are english by default and if you change the language in your phone's settings, it changes language too. Currently the app supports only English and Albanian
and other small fixes and details

> # 1.0.1 (2022-06)

Fixed Share Feature
Ditched my custom api server for an internal one
Now the app shows all playlists items

> # 1.0.0 (2022-05)

First Release
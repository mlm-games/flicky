## v3.5.8

- Improve description viewing for TVs (fix #35)


## v3.5.7

- revert few #34 related changes
- Do not hide from recents


## v3.5.6

- A trial for #34


## v3.5.5

- Make root/shiziku installs sequential
- Improve browse screen colors, try showing stderr logs?


## v3.5.4

- #34 Use %d instead of %i


## v3.5.3

- [chore] weird aab compile err
- Hide updating count
- Fix BottomBar not staying on categories
- Update UI for settings screen options instantly


## v3.5.2

- Hide updating count
- Fix BottomBar not staying on categories
- Update UI for settings screen options instantly


## v3.5.1

- use mutable pending intent (session installer A14+ err) to try fixing #32
- prev. commit fixes, ui and perf. improvements for settings (reduce recomp calls)
- Better batch updates handling
- List shows vername instead of vercode
- delete old files
- Update short_description.txt


## v3.4.7

- Add preference options for individual apps (#30)
- Reduce strings in build.gradle.kts


## v3.4.6

- Try to solve #29 (Add anim debouncing for TV browseScreen)
- TaskStage progress for installs in UpdatesScreen too
- Use only TaskStage progress for installs in AppDetailScreen
- Installer cancellation changes for API 24-25
- Use more robust cancellation (send SIGKILL for cancel)
- repo seeding, HTTPClientProvider thread safety Improvements,
- PullToRefreshBox change fix
- repo seeding Improvements
- Use a normal box for TVs
- Add project level optins and rem unused imports


## v3.4.5

- Use a file wide optin instead and small structural refactor
- missed Opt-ins for experimental m3
- Add dropdowns for repo settings too (for test ping and forget last mirror buttons)
- Non mobile friendly extra settings options
- Add "use list layout" setting.


## v3.4.4

- missed Opt-ins for experimental m3
- Add dropdowns for repo settings too (for test ping and forget last mirror buttons)
- Non mobile friendly extra settings options
- Add "use list layout" setting.


## v3.4.3

- missed Opt-ins for experimental m3
- Add dropdowns for repo settings too (for test ping and forget last mirror buttons)
- Non mobile friendly extra settings options
- Add "use list layout" setting.


## v3.4.2

- Add dropdowns for repo settings too (for test ping and forget last mirror buttons)
- Non mobile friendly extra settings options
- Add "use list layout" setting.


## v3.4.1

- Shorten versions section
- Add pull to refresh syncing, add copy and share buttons in versions section
- Bump major ver (might need to clear cache)
- Add choosable versions to the bottom (repo specific)
- Other misc fixes
- Enable mirrors only for fdroid by default (with roundrobin)
- (misc) import colorscheme and typography directly
- Show default names for predefined repos
- only add an extra v if not present (for app versions)
- Update android.yml for GH release notes


## v3.3.6

- Update android.yml to use correct id


## v3.3.5

- fix yml
- support TV zoom too
- Pin workflow commit to prevent repro mismatches
- Full screen image viewer zoom support (only gestures work well for now)


## v3.3.4

- fix yml
- support TV zoom too
- Pin workflow commit to prevent repro mismatches
- Full screen image viewer zoom support (only gestures work well for now)


## v3.3.3

- Do not expose intent for install reciever


## v3.3.2

- Nvm, Do not show cancelled
- show cancelled for a sec before resetting
- remove progress when cancelled (from ui)
- bump major ver [skip ci]
- Add cancellation (fix #22)


## v3.3.1

- Nvm, Do not show cancelled
- show cancelled for a sec before resetting
- remove progress when cancelled (from ui)
- bump major ver [skip ci]
- Add cancellation (fix #22)


## v3.2.7

- Make install progress persist across screens
- Use install progress from tasks for app detail screen
- Show ignored button displays while hiding updates screen title (for mobile spacing)


## v3.2.6

- Show feedback (with ref to #11)


## v3.2.5

- Fix #17, fix #6 (App Details Category button redirects to categories, add a update button if present)
- linear progress fill max width
- Add exodus privacy and app info clickables (fix #12, fix #10)
- Add licenses link resolver (for clicking licenses)
- Search immediately for TV too (wasn't the cause of #16)
- Fix dual clicking for TV apps


## v3.2.4

- Search bar TV related fixes (use SearchBar composable instead)


## v3.2.3

- Fix install progress not being reflected


## v3.2.2




## v3.2.1

- Update build.gradle.kts [skip ci]
- Add Ui for clear cache (which reflects now), auto update (heavily OS dependent) and "show app icons" settings
- Icon hiding logic
- Update from the compatible latest version


## v3.1.3

- Add Ui for clear cache (which reflects now), auto update (heavily OS dependent) and "show app icons" settings
- Icon hiding logic
- Update from the compatible latest version


## v3.1.2

- Clear cache properly on force sync


## v3.1.1

- Enable browser repos by default
- Show Open/Uninstall after install (for system installer, others work alr)
- Fix #4 (support relative paths)
- Use 1024x1024 icon in fastlane
- Installer reflects download progress till 99% and verification for the rest
- Fix Mirror strategy and trust mode nto being selectable in TV
- Misc perf. improvements
- Add "Fail on trust errors" setting


## v3.0.1

- Cromite works, massive breaking changes, will be fixed in the next update or 2 (mainly regarding cache and repo settings)
- LibRetro works (readd indexv1)
- Commit Missed files
- Experimental mirrors (repo wise) settings ui and other misc changes
- Use stick last good mirror logic
- Index v1 changes (libretro trials) and add entry.json verif setting
- Comment out Cromite, IronFox, Brave Cryptomater and LibRetro (do not seem to work)
- Properly populate apkvariants and persist ui for background downloads
- Minor fixes
- Try adding mirrors rotation (experimental), unify installers
- Add few other commonly used repos (futo, brave, cryptomator, libretro)


## v2.5.2

- Use localised strings and misc formatting changes (fix #7)
- Add all the strings
- Use routes for screens, remove more redundant code
- remove unused files
- remove unused class


## v2.5.1

- Bump ver and test release (early enough)
- Add preferred repo setting
- Update android.yml
- Updates button works as described in #9 (fix #9)
- Fullscreen image viewer works as described in #13 (fix #13)
- Fullscreen image viewer works as described in #13 (fix #13)
- Use paging, increase timeout for older devices, add ignore version options similar to droidify, fix many other bugs
- Use paging, increase timeout for older devices, add ignore version options similar to droidify
- Try fixing force sync for older devices, setting to show incompatible apps
- Update 320.txt
- Update CHANGELOG.md
- Update CHANGELOG.md


## v2.4.1

- Bump maj ver
- add shizuku and root install options
- Show version name on updates screen while updating
- Add few common repos like neo-store and droidify
- Update README.md for izzy link


## v2.3.8

- try reducing proguard-rules.pro
- fix image viewer and apk installation improvements, sync setting options fix for 24hrs+ etc
- Update README.md
- Update build.gradle.kts


## v2.3.7

- Update strings.xml
- add universal apk -P flag
- rm default param
- add back unused files for title
- Update android.yml to rm deprecated parts


## v2.3.6

- add universal apk -P flag
- rm default param
- add back unused files for title
- Update android.yml to rm deprecated parts


## v2.3.5

- rm default param
- add back unused files for title
- Update android.yml to rm deprecated parts


## v2.3.4

- add back unused files for title
- Update android.yml to rm deprecated parts


## v2.3.3

- rm unused files
- Add proper syncing for screenshots and show what's new if present
- Update android.yml to fix gradle error
- Use mobile placeholder for tv too
- Try getting screenshots (overflow? or no tv space), fix read more button in tv
- Fix sort button, ui reflects for initial syncs
- Fix sort button, ui reflects for initial syncs
- make yml edit friendly for mobile
- Update build.gradle.kts


## v2.3.2

- Add proper syncing for screenshots and show what's new if present
- Update android.yml to fix gradle error
- Use mobile placeholder for tv too
- Try getting screenshots (overflow? or no tv space), fix read more button in tv
- Fix sort button, ui reflects for initial syncs
- Fix sort button, ui reflects for initial syncs
- make yml edit friendly for mobile
- Update build.gradle.kts


## v2.3.1

- Add proper syncing for screenshots and show what's new if present
- Update android.yml to fix gradle error
- Use mobile placeholder for tv too
- Try getting screenshots (overflow? or no tv space), fix read more button in tv
- Fix sort button, ui reflects for initial syncs
- Fix sort button, ui reflects for initial syncs
- make yml edit friendly for mobile
- Update build.gradle.kts


## v2.2.8

- Use mobile placeholder for tv too
- Try getting screenshots (overflow? or no tv space), fix read more button in tv
- Fix sort button, ui reflects for initial syncs
- Fix sort button, ui reflects for initial syncs
- make yml edit friendly for mobile
- Update build.gradle.kts


## v2.2.7

- Remove unused firebase lib and downgrade gradle for fdroid compat


## v2.2.6

- fix workflow
- use a property to toggle splitting
- Update android.yml to use gradle kts params instead
- Update android.yml
- Update android.yml
- Add app placeholder icons (if no icon)
- Force sync on changing repositories
- Fix fdroid archive crash on sync
- Try improving installer to prevent duplicate sha256 checks
- Fix app descriptions with <html> tags
- add tv screenshots
- add aab upload (try fooling gplay to allow?)
- Fix crash due to duplicate keys in updates screen


## v2.2.5

- use a property to toggle splitting
- Update android.yml to use gradle kts params instead
- Update android.yml
- Update android.yml
- Add app placeholder icons (if no icon)
- Force sync on changing repositories
- Fix fdroid archive crash on sync
- Try improving installer to prevent duplicate sha256 checks
- Fix app descriptions with <html> tags
- add tv screenshots
- add aab upload (try fooling gplay to allow?)
- Fix crash due to duplicate keys in updates screen


## v2.2.4

- Update android.yml to use gradle kts params instead
- Update android.yml
- Update android.yml
- Add app placeholder icons (if no icon)
- Force sync on changing repositories
- Fix fdroid archive crash on sync
- Try improving installer to prevent duplicate sha256 checks
- Fix app descriptions with <html> tags
- add tv screenshots
- add aab upload (try fooling gplay to allow?)
- Fix crash due to duplicate keys in updates screen


## v2.2.3

- Update android.yml
- Update android.yml
- Add app placeholder icons (if no icon)
- Force sync on changing repositories
- Fix fdroid archive crash on sync
- Try improving installer to prevent duplicate sha256 checks
- Fix app descriptions with <html> tags
- add tv screenshots
- add aab upload (try fooling gplay to allow?)
- Fix crash due to duplicate keys in updates screen


## v2.2.2

- Update android.yml
- Add app placeholder icons (if no icon)
- Force sync on changing repositories
- Fix fdroid archive crash on sync
- Try improving installer to prevent duplicate sha256 checks
- Fix app descriptions with <html> tags
- add tv screenshots
- add aab upload (try fooling gplay to allow?)
- Fix crash due to duplicate keys in updates screen


## v2.2.1

- Add app placeholder icons (if no icon)
- Force sync on changing repositories
- Fix fdroid archive crash on sync
- Try improving installer to prevent duplicate sha256 checks
- Fix app descriptions with <html> tags
- add tv screenshots
- add aab upload (try fooling gplay to allow?)
- Fix crash due to duplicate keys in updates screen


## v2.1.1

- Bump build.gradle.kts
- Add default sort and wifi-only setting
- Add theme and dynamic theme settings
- Add theme and dynamic theme setting
- Remove odd divider for TV


## v2.0.1

- revert build.gradle.kts
- Delete fastlane/metadata/android/en-US/changelogs/120.txt
- Delete fastlane/metadata/android/en-US/changelogs/150.txt
- Delete fastlane/metadata/android/en-US/changelogs/140.txt
- Delete fastlane/metadata/android/en-US/changelogs/130.txt
- Delete CHANGELOG.md
- Bump ver
- Update README.md
- Time for the initial kotlin release (satisfactory, need to open <html> "desc." issue, and screenshots)
- Make a prerelease (Stable enough / test on TV)
- UI changes
- Downloads, but to downloads folder
- Fix wrong indexv2 format usage
- Migrations related changes, and use alias for ksp
- Save 2
- Save for now
- Update README.md
- Update README.md
- Update README.md
- Update README.md
- first boot save (kotlin switch)
- Seperation refactor
- try also adding android ui
- rm unused repo service file
- delete duplicate theme, and other duplis
- Update README.md
- better theme
- focus for repository add button
- fix focus for categories
- log back to print
- warn fixes
- add icons properly
- wrap themeoption also in inkwell and add focusing
- Update README.md


# Changelog

## v2.0.4

- Delete CHANGELOG.md
- Bump ver
- Update README.md
- Time for the initial kotlin release (satisfactory, need to open <html> "desc." issue, and screenshots)
- Make a prerelease (Stable enough / test on TV)
- UI changes
- Downloads, but to downloads folder
- Fix wrong indexv2 format usage
- Migrations related changes, and use alias for ksp
- Save 2
- Save for now
- Update README.md
- Update README.md
- Update README.md
- Update README.md
- first boot save (kotlin switch)
- Seperation refactor
- try also adding android ui
- rm unused repo service file
- delete duplicate theme, and other duplis
- Update README.md
- better theme
- focus for repository add button
- fix focus for categories
- log back to print
- warn fixes
- add icons properly
- wrap themeoption also in inkwell and add focusing
- Update README.md


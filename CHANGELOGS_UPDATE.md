# Plus 1.3 (Capes, Babric, Screen Recorder & more)

### Capes
- New cape collection system with minecraftcapes.net gallery API
- Cape selector dialog with rename (edit icon), favorite, delete, "No Cape" option
- Cape gallery with download from official gallery
- Cropped cape back thumbnails (bilinear filtering)
- Upload cape from gallery button inside CapeSelectorDialog
- Button renames: "Select Cape" → "Capes", "Upload Cape from Gallery"
- Wardrobe preview fixes for cape changes
- Client-side ely.by cape support

### Babric Mod Loader
- Full Babric mod loader support for b1.7.3

### Screen Recorder
- Built-in game screen recorder with elapsed timer
- Audio support (PLAYBACK_CAPTURE), fallback to video-only

### Fixes
- OpenAL crash fix on null device (`ALSOFT_DISABLE_EVENTS`)
- Duplicate libopenal.so warning resolved
- Gallery cape thumbnail loading moved off main thread

### Other
- Player notice polling with retry + backoff
- Stable/Snapshots tab pill in GameVersionFilter
- SearchAssetsScreen improvements
- OSMesa updated to nightly build
- Default transition animation changed to SLICE_IN
- CI upgraded to action-gh-release v3
- Translations: zh-cn, vi-vn, tr updates
- Pull request template added

# Lockd In: Google Play internal-test bundle 83

Download `lockd-in-internal-83.aab` using GitHub’s file-page download arrow. Upload this file to Play Console → Lockd In → Internal testing → Create release. It is an Android App Bundle, not a directly installable APK.

- Package: `app.lockdin`
- Version code: `83`
- Source commit: `922831cabc11479fa78311108bc383d33d5a850d`
- Based on owner-reverted main `93a7172`; no app runtime/UI changes.
- Release build, 84 release unit tests, and release lint passed (0 lint errors).
- Private upload signature verified; not signed with the public debug key.
- Public upload certificate SHA-256: `3e78b347320812b05e8c09430ec5f8492ec8c15e87c6a826ce1f44a51d2ef76e`
- AAB SHA-256: `a7efc1dafa623752ab196ca4862f381d309cdefa5148e1cd66c73d51d8889cc0`

Use Google-generated Play App Signing when creating the first release. This branch contains only the bundle and **public** certificate; it does not contain the private upload key or password. The private key is outside Git in the execution workspace and needs a secure owner backup/CI secret setup.

No Play release was submitted or rolled out by the assistant. Add the Play Store account used on your phone as an internal tester. Address Console errors for that track before rollout.

The Play-installed app will have a different signature from the sideloaded debug APK. Plan data preservation before replacing the debug installation; do not uninstall blindly. Phone installation, real blocking and permissions still need testing.

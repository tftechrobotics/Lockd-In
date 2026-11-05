# Full Lockd In app: Play internal testing

This builds `app.lockdin`, not the old probes. No UI/session/blocking behavior
is changed by the upload-signing configuration. Default debug signing stays
unchanged. The workflow is manual and does not submit or roll out a Play release.

Use Google-generated Play App Signing on the first release. The private upload
key authenticates AAB uploads; Google holds a separate installed-app signing key.
Do not use the repository's public debug key for Play.

## Private upload signing

The initial upload key is generated in the execution workspace outside Git,
under `/workspace/.local/lockdin-signing/`, with directory mode 700/file mode 600.
Only the AAB and public certificate may be shared publicly. The private keystore
and password must be backed up using a secure transfer to owner-controlled
storage; never put them on a GitHub branch, website or in chat. Workspace storage
is not a substitute for the owner's secure backup. If the upload key is lost,
request an upload-key reset through Play Console; it is not the installed-app key.

For reproducible CI, configure these repository Actions secrets from the private
key via an approved secure channel:

- `LOCKDIN_APP_UPLOAD_KEYSTORE_B64`: base64 of `upload.p12` (base64 is not encryption).
- `LOCKDIN_APP_UPLOAD_STORE_PASSWORD`: the keystore password.

Alias: `lockdin-app-upload`. The PKCS12 store and key share the password. These
are deliberately separate from the older probe secrets. No secrets are printed
or committed. The new workflow will not run until available in the repository's
Actions workflow list (normally after merging its source branch).

Run **Android Play internal bundle**, selecting an unused positive version code.
Download artifact **lockdin-full-app-play-internal**, then upload only the `.aab`
to **Play Console → Internal testing → Create release**. A public certificate is
provided for fingerprint verification; never upload a private keystore to GitHub.

For a local build set `LOCKDIN_PLAY_BUILD=1`, `LOCKDIN_VERSION_CODE`,
`LOCKDIN_UPLOAD_STORE_FILE`, `LOCKDIN_UPLOAD_STORE_PASSWORD` and
`LOCKDIN_UPLOAD_KEY_ALIAS`; run from `apps/android`:

```
./gradlew --no-daemon bundleRelease testReleaseUnitTest lintRelease
```

Missing signing inputs fail explicitly for Play builds. Ordinary unsigned
release checks still work when `LOCKDIN_PLAY_BUILD` is not set.

## Console and phone

1. Accept Google-generated Play App Signing after reviewing the terms.
2. Upload the signed AAB, save the draft and address the errors Console actually
   reports for the internal track. Internal-only exemptions do not waive all
   permission policies. Do not claim a public-launch compliance audit passed.
3. Add the Google account used by the phone's Play Store to the internal tester
   list; save and select it. Roll out only to Internal testing, then use the
   opt-in link while signed in to that account.
4. Keep Samsung Auto Blocker enabled. Test installation and actual blocking,
   permissions, revocation and background behavior on the S24+.

A Google-generated app-signing key differs from the installed public-debug key.
The Play build normally cannot update that same-package debug installation.
Do not uninstall until the owner has saved needed data: backup is disabled and
uninstall deletes private local history/settings. A CSV history export is not a
full settings/partner/key backup. This setup does not migrate app data.

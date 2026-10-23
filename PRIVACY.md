# Privacy and screen access

Thor Auto Translate reads the foreground screen of games and emulators that you enable, using Android Accessibility. This permission detects the active app and captures its selected screen to recognize dialogue and game instructions. It also shows translations on the other screen. It does not click game controls or modify game files.

Reading begins only after the initial screen-reading disclosure has been accepted and setup is complete. It stops when you leave the enabled game, turn off translation, lock the device, or revoke the Accessibility permission. You can revoke permission at any time in Android settings.

Screenshots, the most recent OCR input and the tiny background image are held in memory for the current session. They are not saved as image files or uploaded. Recognized text is processed by the selected translation method:

- **On device:** OCR and translation are local. Google ML Kit downloads required language models; subsequent translation uses those models on the device.
- **Google online without a key:** Recognized text is sent over HTTPS to Google. The request also exposes normal connection information such as your IP address to Google.
- **Google Cloud:** Recognized text and your supplied API key are sent over HTTPS to Google Cloud Translation. Google’s service terms, privacy practices and billing apply.

Any files and download requests from the retired experimental model feature are removed locally on upgrade.

Settings and a user-supplied API key are stored in the app’s private Android storage. Settings and keys are excluded from Android cloud backup and device transfer. Recent translations and the translation cache are held in memory for the session, and are cleared when the game/session ends. No translation history is stored permanently. No separate app-operated analytics, advertisements or app-operated backend are included. Google ML Kit can send device/app information, per-installation identifiers, source/target language configuration and performance/usage metrics to Google for diagnostics and usage analytics. These metrics do not contain game images or the on-device translation input/output. See [ML Kit privacy](https://developers.google.com/ml-kit/terms) and [SDK data disclosures](https://developers.google.com/ml-kit/android-data-disclosure).

The Apps list reads launcher app names and package identifiers locally so you can choose which apps activate translation. No game library scan, ROM folder scan, online game catalog or translation-pack download is performed.

Uninstalling the app removes its private settings. Android may retain shared downloaded ML Kit models used by other apps. Google’s handling of data sent to its services is governed by Google’s policies. An open-source fork or modified build may behave differently; review the source of the build you install.

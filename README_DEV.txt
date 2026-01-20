== Release Instructions ==
1. Update README with the release notes
2. Update the versionCode and versionName in AndroidManifest.xml
3. Build the App Bundle
    - Build -> Generate Signed App Bundle or APK
    - Android App Bundle
    - Enter keystore details
4. Release the application on https://play.google.com/console/
5. Create a source tarball and upload to SourceForge (git archive master | gzip > upm-android-1.x-src.tar.gz)
6. Upload the APK to SourceForge
7. git tag -a v1.x -m 1.x
8. git push
9. git push --tags

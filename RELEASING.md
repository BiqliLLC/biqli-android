# Android SDK release checklist

Maven Central coordinates:

```text
li.biq:biqli-android:<version>
```

Canonical source repository:

```text
https://github.com/BiqliLLC/biqli-android
```

## Prepare the release

1. Update `VERSION_NAME`, `Biqli.SDK_VERSION`, and `CHANGELOG.md` together. Replace `Unreleased` with the ISO release date before building the final bundle.
2. Confirm the release tag in the POM will be `v<VERSION_NAME>`.
3. Build and test using JDK 17, Gradle 9.6, Android Gradle Plugin 9.4.0, compile/target SDK 37, and minimum SDK 23.
4. Run unit tests and build the release AAR in a clean environment.
5. Consume the staged coordinate from a separate clean sample app; inspect the merged manifest and confirm no `AD_ID` permission.
6. Test an installed App Link on minimum/current Android physical devices.
7. Test deferred attribution through a Google Play internal test track.
8. Verify a second launch does not create a second first-install attribution and an offline first launch resolves on a later launch.

## Build the Central bundle on Windows

Set the signing key only in the current PowerShell process:

```powershell
$env:BIQLI_MAVEN_SIGNING_KEY = Get-Content -Raw -LiteralPath 'C:\path\to\biqli-private-key.asc'
$biqliSigningPassword = Read-Host 'GPG key passphrase' -AsSecureString
$env:BIQLI_MAVEN_SIGNING_PASSWORD = [System.Net.NetworkCredential]::new('', $biqliSigningPassword).Password
```

Build the signed Maven-layout archive:

```powershell
.\gradlew.bat :biqli:centralBundle
```

The upload artifact is:

```text
biqli/build/central-bundle/biqli-android-<version>-central.zip
```

The task clears the old staging repository, publishes the AAR, POM, sources, documentation, and signatures, generates required MD5 and SHA-1 checksums, and packages only the selected version directory.

Clear the sensitive environment variables after the build:

```powershell
Remove-Item Env:BIQLI_MAVEN_SIGNING_KEY
Remove-Item Env:BIQLI_MAVEN_SIGNING_PASSWORD
```

## Publish

1. Confirm the corresponding immutable GitHub tag exists and matches the POM tag.
2. In Central Publisher Portal, choose **Publish Component**.
3. Use deployment name `li.biq:biqli-android:<version>`.
4. Upload the generated `-central.zip` file.
5. Wait for validation to pass, review the component list, and publish the deployment.
6. Confirm the coordinate resolves from Maven Central. Begin subsequent development with a new `Unreleased` changelog section; never modify the published release entry or tag.

Never reuse or replace a published Maven Central version or GitHub release tag. Publish a new version for every correction.

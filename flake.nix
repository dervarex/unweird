{
  description = "unweird: development shells";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        # The Android SDK is unfree and comes with Google's license terms; entering the shell accepts them.
        config = { allowUnfree = true; android_sdk.accept_license = true; };
      };
      # Keep these in step with unweird-android/build.gradle.kts (compileSdk, buildToolsVersion, ndkVersion, cmake).
      android = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "36" ];
        buildToolsVersions = [ "36.1.0" ];
        includeNDK = true;
        ndkVersions = [ "29.0.14206865" ];
        cmakeVersions = [ "3.31.6" ];
        includeEmulator = true;
        includeSystemImages = true;
        systemImageTypes = [ "google_apis" ];
        abiVersions = [ "x86_64" ];
      };
      sdk = "${android.androidsdk}/libexec/android-sdk";
    in {
      # nix develop .#android   then   ./gradlew :example-android:assembleDebug
      devShells.${system}.android = pkgs.mkShell {
        packages = [ android.androidsdk pkgs.jdk21 ];
        ANDROID_HOME = sdk;
        ANDROID_SDK_ROOT = sdk;
        ANDROID_NDK_ROOT = "${sdk}/ndk/29.0.14206865";
        JAVA_HOME = pkgs.jdk21.home;
        # Gradle downloads aapt2 from Google's Maven repository; that binary isn't built for NixOS. Use the SDK's.
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/build-tools/36.1.0/aapt2";
      };
    };
}

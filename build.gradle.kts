// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
}

tasks.register<Copy>("copyApkToDownloadFolder") {
  val buildApk = layout.projectDirectory.file("app/build/outputs/apk/debug/app-debug.apk")
  val platformApk = layout.projectDirectory.file(".build-outputs/app-debug.apk")
  
  if (buildApk.asFile.exists()) {
    from(buildApk)
  } else {
    from(platformApk)
  }
  
  into(layout.projectDirectory.dir("APK_DOWNLOAD"))
}

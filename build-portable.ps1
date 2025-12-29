# Set Paths from your environment
$env:Path = "$JAVA_HOME\bin;$M2_HOME\bin;" + $env:Path

# App Configuration
$APP_NAME = "AvroViewer"
$APP_VERSION = "0.3"
$MAIN_JAR = "avro-viewer-app-impl-$APP_VERSION-SNAPSHOT.jar"
$MAIN_CLASS = "com.dkostin.avro_viewer.app.Main"
$ICON_PATH = "impl/src/main/resources/icon.ico"

Write-Host "Step 1: Copying dependencies..." -ForegroundColor Cyan
& mvn dependency:copy-dependencies -DoutputDirectory=target/libs

Write-Host "Step 2: Preparing bundle folder..." -ForegroundColor Cyan
Remove-Item -Recurse -Force "impl/target/bundle" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "impl/target/bundle"
Copy-Item "impl/target/$MAIN_JAR" -Destination "impl/target/bundle"
Copy-Item "impl/target/libs/*.jar" -Destination "impl/target/bundle"

Write-Host "Step 3: Creating Portable App Image..." -ForegroundColor Cyan
& jpackage `
  --type app-image `
  --name "$APP_NAME" `
  --vendor "Denys Kostin" `
  --description "Avro file viewer" `
  --app-version "$APP_VERSION" `
  --input "impl/target/bundle" `
  --main-jar "$MAIN_JAR" `
  --main-class "$MAIN_CLASS" `
  --dest "dist" `
  --win-console `
  --module-path "impl/target/libs" `
  --add-modules javafx.controls,javafx.fxml,javafx.graphics `
  --java-options "--enable-native-access=javafx.graphics" `
  --java-options "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED" `
  --icon "$ICON_PATH" `
  --verbose

Write-Host "Done! Look into dist folder." -ForegroundColor Green
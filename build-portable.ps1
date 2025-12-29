# Set Paths from your environment
$env:Path = "$JAVA_HOME\bin;$M2_HOME\bin;" + $env:Path

# App Configuration
$APP_NAME = "AvroViewer"
$MAIN_JAR = "avro-viewer-app-impl-0.2-SNAPSHOT.jar"
$MAIN_CLASS = "com.dkostin.avro_viewer.app.Main"

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
  --input "impl/target/bundle" `
  --main-jar "$MAIN_JAR" `
  --main-class "$MAIN_CLASS" `
  --dest "dist" `
  --win-console `
  --module-path "impl/target/libs" `
  --add-modules javafx.controls,javafx.fxml,javafx.graphics `
  --verbose

Write-Host "Done! Look into dist folder." -ForegroundColor Green
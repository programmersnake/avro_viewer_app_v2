# Avro Viewer (JavaFX)

Desktop UI tool for viewing and filtering Avro files.

This project is a modular JavaFX desktop application. To ensure a "click-and-run" experience without requiring the user
to install Java or JavaFX separately, we bundle a stripped-down Java Runtime Environment inside the application.

## Commands

`mvn clean install`

`mvn -pl impl -q javafx:run`

# Release Guide (Avro Viewer v2)

## Prerequisites

- JDK 25 installed (`java -version`)
- Maven 3.9+
- PowerShell 5.1+ (For Windows build scripts)

---

## 🚀 How to Build (portable runtime image)

1. First, generate the application JAR and download all dependencies (Avro, Jackson, JavaFX, etc.):

```commandline
mvn clean install
```

2. Run the build script to create a standalone directory in ./dist. This process bundles the JRE, native JavaFX
   libraries, and your code. Run this in PowerShell:
   `Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process; .\build-portable.ps1`

3. Pack the generated image into a ZIP file for easy sharing:

```bash
Compress-Archive -Path .\dist.\* -DestinationPath .\dist.\avroviewer-windows-x64.zip -Force
```

---

## 📦 Distribution Structure

The resulting portable ZIP will have the following structure:

* AvroViewer.exe — The main entry point.
* app/ — Contains all JARs (including Avro as an automatic module).
* runtime/ — The custom-built private JRE.

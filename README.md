# Avro Viewer (JavaFX)

A lightweight desktop tool for viewing, filtering, and exporting Avro files.

Avro Viewer is a **modular JavaFX application** designed for fast inspection of large Avro datasets.
It provides a clean UI, flexible filtering, and a portable “click-and-run” distribution
with a bundled Java Runtime — no separate Java or JavaFX installation required.

---

## ✨ Features

- Open and inspect `.avro` files
- Dynamic filter builder (AND-combined filters)
- Pagination and result limiting
- Export filtered data to:
    - JSON
    - CSV
- Light / Dark theme
- Portable distribution with bundled JRE (Windows)

---

## 🧱 Architecture Overview

- **Java 25**
- **JavaFX (modular)**
- **Apache Avro**
- **Jackson (JSON / CSV export)**
- Custom Java runtime built via `jpackage`

---

## 🔧 Build & Run (Development)

### Prerequisites

- JDK 25 (`java -version`)
- Maven 3.9+

### Commands

1. Build:

```bash
mvn clean install
```

2. Run the JavaFX application (impl module):

```bash
mvn -pl impl javafx:run
```

---

## 🚀 Release Guide (Portable Runtime Image)

#### This project supports building a standalone portable distribution that includes:

* Application
* All dependencies
* A stripped-down JRE
* Native JavaFX libraries

##### Prerequisites (Windows):

* JDK 25
* Maven 3.9+
* PowerShell 5.1+

### Steps:

#### 1️⃣ Build application artifacts

`mvn clean install`

#### 2️⃣ Create portable runtime image

Run the PowerShell build script:
`Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process; .\build-portable.ps1`

This will generate a standalone application directory under: `./dist/`

#### 3️⃣ Package distribution (ZIP)

```bash
Compress-Archive -Path .\dist\* -DestinationPath .\dist\avroviewer-windows-x64.zip -Force
```

---

## 📦 Distribution Structure

The generated portable ZIP contains:

* `AvroViewer.exe`        # Main application launcher
* `app/`                  # Application JARs and dependencies
* `runtime/`              # Custom bundled Java runtime

Users can extract the archive and run the application immediately.

---

# 📜 License

This project is licensed under the MIT License.
See the [LICENSE](LICENSE)
file for details.

---

# 🛠️ Status

Avro Viewer is actively developed and intended for internal tooling,
data inspection, and developer workflows.

Contributions, issues, and suggestions are welcome.

---

## 📸 Screenshots

Dark main view:
![Main Window](docs/screenshots/main_view_dark.png)

Light main view:
![Main Window](docs/screenshots/main_view_light.png)
*Filtering and inspecting Avro records*

## Credits

- Application icon generated using Google Gemini (AI)

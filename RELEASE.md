# Release Guide (Avro Viewer v2)

This project is a JavaFX desktop application (Java 21).  
Because JavaFX is not bundled with the JDK, running a plain `java -jar` is not a reliable distribution method.
Instead, we ship a **self-contained runtime image** (portable app) and optionally an installer.

---

## Prerequisites

- JDK 25 installed (`java -version`)
- Maven 3.9+
- OS-specific packaging tools (optional)
    - Windows: WiX Toolset (only if you want `.msi`)
    - macOS: codesign/notarization (only for signed `.dmg/.pkg`)
    - Linux: `rpm`/`dpkg` (only for `.rpm/.deb`)

---

## Build (portable runtime image)

From `impl/`:

```
mvn -pl impl -DskipTests clean javafx:jlink
```

### Run
```bash
.\impl.\target\avroviewer\bin\AvroViewer.bat
```

### Create zip archive
```bash
Compress-Archive -Path .\impl.\target\avroviewer\* -DestinationPath .\impl.\target\avroviewer-windows-x64.zip -Force
```

## Versioning

We use tags like `v0.1.0`.

- Bump version (optional): update `impl/pom.xml` and/or root version.
- Commit changes.
- Tag and push.

```bash
git tag v0.1.0
git push origin v0.1.0
```

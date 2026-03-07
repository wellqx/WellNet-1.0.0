# WellNet

WellNet is a client-side Minecraft mod focused on smoother play in heavy modpacks, calmer world joins, and more stable local hosting.

## Current project line
- Mod version: `1.1.0`
- Minecraft version: `1.20.1`
- Loaders in source tree:
  - `Forge`
  - `Fabric`

## What the mod does
- Tracks session stability while you play.
- Reacts to harsh chunk-load and frame-spike moments more carefully than before.
- Helps keep local hosted worlds calmer under pressure.
- Works automatically in the background after you enter a world.

## Releases
Ready-to-use jar files are listed in [releases/README.md](releases/README.md).

Included release files:
- `wellnet-1.0.0-1.20.1-forge.jar`
- `wellnet-1.1.0-1.20.1-forge.jar`
- `wellnet-1.1.0-1.20.1-fabric.jar`

## Wiki
Player documentation is available in the GitHub Wiki:
- [WellNet Wiki](https://github.com/wellqx/WellNet/wiki)

## Build from source
- `.\gradlew.bat build`
- `.\gradlew.bat clean build`

## Project structure
- `src/` - shared source and Forge entrypoints
- `fabric/` - Fabric loader project
- `releases/` - ready-to-use published mod jars

## License
This repository is distributed under the [MIT License](LICENSE).

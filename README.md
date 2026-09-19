# Sculkborne

Standalone NeoForge sculk expansion for Minecraft 1.21.1.

## Requirements

- Java 21
- NeoForge 21.1.238
- GeckoLib 4.9.2

## Development

```powershell
.\gradlew.bat compileJava
.\gradlew.bat runServer
.\gradlew.bat runClient
```

The project is independent from EnderEchoing. When both mods are installed,
EnderEchoing detects `sculkborne` and delegates shared sculk content to this mod.

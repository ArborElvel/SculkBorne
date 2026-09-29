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

## Optional: Fresh Sculk

`freshsculk` is a separate client-side addon that supplies the Fresh Animations
style CEM poses for the sculk mobs. Sculkborne does not depend on it: the mobs
drive their poses through the client SPI in
`com.unddefined.sculkborne.client.model.cem` (`CemAnimatorRegistry` +
`CemEntityModel`), and freshsculk registers its animators there at client setup.

Without freshsculk the same mobs fall back to the GeckoLib keyframe animations in
`assets/sculkborne/animations/entity` (idle / walk / attack). The addon lives in
the sibling repository `../freshsculk`, which composes this repository through
`includeBuild` for development.

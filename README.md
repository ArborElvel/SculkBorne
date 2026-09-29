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
drive their poses through the client pose registry in
`com.unddefined.sculkborne.client.model.cem`, and freshsculk registers its
animators there at client setup.

That registry has two tiers. Sculkborne always registers a built-in tier that
ports the vanilla counterpart's `setupAnim` (`CreeperModel`, `HumanoidModel`,
`VexModel`, `EndermiteModel`, `SilverfishModel`, `EndermanModel`) onto the mob's
own skeleton — see `client/model/anim`. freshsculk registers the overriding tier,
so with the addon installed the mobs use the Fresh Animations poses and without
it they use the vanilla ones. The addon lives in the sibling repository
`../freshsculk`, which composes this repository through `includeBuild`.

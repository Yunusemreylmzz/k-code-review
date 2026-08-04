# K Code Review — JetBrains Marketplace package

## Assets prepared

| File | Use |
|------|-----|
| `marketplace/k-code-review-logo.png` | Master logo (transparent corners) |
| `marketplace/pluginIcon-40.png` … `512.png` | Listing / promo sizes |
| `src/main/resources/META-INF/pluginIcon.svg` | Required IDE + Marketplace plugin logo (40×40 SVG) |
| `src/main/resources/META-INF/pluginIcon_dark.svg` | Dark-UI variant |
| `build/distributions/k-code-review-1.0.2.zip` | Upload this ZIP |

## First publish (manual — required once)

1. Sign in with JetBrains Account: https://plugins.jetbrains.com/author/me  
2. Upload new plugin: **https://plugins.jetbrains.com/plugin/add**  
   (Account menu → **Upload plugin**)  
3. Choose file: `build/distributions/k-code-review-1.0.2.zip`  
4. Accept Developer Agreement + create Vendor profile if prompted  
5. License: MIT · Tags: VCS / Code tools · Channel: default (Stable)  
6. Wait for automated + manual review  

Docs: https://plugins.jetbrains.com/docs/marketplace/uploading-a-new-plugin.html

## Later versions (Gradle)

```bash
export ORG_GRADLE_PROJECT_intellijPlatformPublishingToken='YOUR_TOKEN'  # Profile → My Tokens
./gradlew publishPlugin
```

## Notes

- Plugin ID: `com.kcodereview.plugin`
- Version: `1.0.2`
- sinceBuild: `243` (IntelliJ 2024.3+)
- Description / change-notes live in `plugin.xml`

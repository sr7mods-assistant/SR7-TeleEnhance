# SR7 LSPatch module

This module is a minimal LSPosed/Xposed skeleton that applies runtime adjustments to org.telegram.messenger to emulate NagramX defaults:

- Keep Telegram default app and notification icons
- Block dynamic/pinned shortcuts that mention "nagram" to avoid extra launcher icons
- Rename settings title to "SR7 Settings" and update About text (best-effort)

Place further hooks under LSPatch/app/src/main/java/com/sr7mods/lspatch/

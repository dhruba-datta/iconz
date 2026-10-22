<p align="center">
  <img src="branding/icon-512.png" width="112" height="112" alt="Iconz app icon" />
</p>

<h1 align="center">Iconz</h1>

<p align="center">
  <strong>A free, open-source line icon pack for Android, made for Bangladeshi apps.</strong><br />
  bKash, Nagad, Rocket, Pathao, MyGP, Daraz, banks and more, in a clean white line style.
</p>

<p align="center">
  <a href="https://github.com/dhruba-datta/iconz/releases/latest/download/iconz.apk"><strong>Download for Android</strong></a> ·
  <a href="https://github.com/dhruba-datta/iconz/releases">All releases</a>
</p>

---

Most icon packs skip the apps people in Bangladesh use every day. Iconz draws them.

<p align="center"><img src="branding/icons.png" width="560" alt="The Iconz icons" /></p>

## Install

1. On your Android phone, download **[iconz.apk](https://github.com/dhruba-datta/iconz/releases/latest/download/iconz.apk)**.
2. Open it. If Android asks, allow your browser to **install unknown apps**, then tap **Install**.
3. Open Iconz and tap **Apply**, or pick Iconz in your launcher's icon pack setting.

Works with launchers that support icon packs: Lawnchair, Nova, Smart Launcher, Niagara,
Hyperion, Action Launcher and others. Pixel and Samsung's own launchers don't take icon
packs. Needs Android 8.0 or newer.

## Updates

Iconz checks this repository for a newer release and offers to install it (Check for
updates, plus a quiet check twice a day). Updates install over the existing app.

Every release is signed with the same key. Check a download with
`apksigner verify --print-certs iconz.apk`; the certificate's SHA-256 digest must be:

```
06e526fa283b9c5ba93c4074532c4780fe3528a9f838b3174bdd5b8a33588bd6
```

## Missing an app?

Open Iconz → **Missing icons** to see which of your apps have no icon yet, and share the
list in an [issue](https://github.com/dhruba-datta/iconz/issues).

## Building

```sh
python3 tool/build_icons.py      # writes appfilter.xml, drawable.xml from icons/icons.json
./gradlew assembleDebug          # JDK 17 and the Android SDK
```

Each icon is a VectorDrawable in `app/src/main/res/drawable/` (48×48, one white path,
stroke 2.2, round caps). Add it to `icons/icons.json`, run the tool, and preview all icons
with `python3 tool/preview_icons.py`.

## Licence

[MIT](LICENSE). The icons are original drawings. App names and logos they refer to are
trademarks of their owners; Iconz is not affiliated with or endorsed by any of them.

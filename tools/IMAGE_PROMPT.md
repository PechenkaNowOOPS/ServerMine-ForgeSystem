# Built-in image_gen — style-transfer

Input 1: deterministic 256×222 GUI geometry preview. Input 2: user-approved «Пиксельный интерфейс кузнечного горна.png».

Create a metal forge backing texture matching the approved reference: black hammered iron, silver rivets, embossed bronze seams, restrained ember accents and narrow red banners. Preserve the layout preview's outer silhouette and positions. Main casing x25..215, y0..221; gauge x214..228, y30..112; red close button x183,y107,width19,height19; blank title bar x38,y2,width164,height19. No tools, armor icons, writing or glyphs. All labels, numbers and slot outlines will be rendered programmatically afterward. Flat orthographic 2D pixel texture; hard edges, no perspective or blur. Transparent outside casing. One texture only.

The generated artwork did not maintain all slot geometry, so only its decorative metal is used. Interactive regions, slots, labels and heat overlays are regenerated from code. The saved final source asset is `tools/forge-art.png`; final runtime textures are under `resourcepack/assets/servermine/textures`.

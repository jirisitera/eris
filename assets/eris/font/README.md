# Shader fonts

The emote wheel uses a separate smooth, proportional Segoe UI atlas at
`textures/effect/emote_font.png`. Its ASCII cells are 48 by 72 pixels, with
16 columns. Regenerate the bitmap and matching `post/emote_font.glsl` advances
together on Windows with `pwsh -File tools/generate-emote-font.ps1`.
Wheel names and descriptions live in `post/emotes.glsl`; they use packed ASCII
labels and do not affect the other shader fonts.

Server code can display transitions with `com.japicraft.service.TransitionService`:

```java
TransitionService.show(player, TransitionService.Effect.LOADING);
TransitionService.

show(player, TransitionService.Effect.BACKGROUND, tint, 20,40,20);
TransitionService.

show(player, TransitionService.Effect.SHUTTER, tint, 10,20,10);
```

Timings are fade-in, stay, and fade-out in ticks. Fade-in covers the screen;
fade-out reveals it. Shutter is always black. The default is white tint with
20 ticks per phase. Transitions replace the player's current title, including
notifications. `TransitionService.title(...)` builds the same title without
sending it for callers that manage delivery themselves.

GUI text effects are implemented in `eris:text` shader includes. Each bitmap uses
its top-left pixel as metadata: RGB `(204, group * 4, type * 4)`.
The bitmap providers below already contain these markers; use the listed font
and character. Group 2 (hearts) is unsupported.

| Font              | Character | Image                       | Group/type | Effect                                                |
|-------------------|-----------|-----------------------------|------------|-------------------------------------------------------|
| `eris:transition` | `=`       | `transition/loading.png`    | 1/1        | Animated contour noise with a diagonal dot reveal     |
| `eris:transition` | `&#124;`  | `transition/background.png` | 1/2        | Scrolling tiled background with a diagonal dot reveal |
| `eris:transition` | `-`       | `transition/shutter.png`    | 1/3        | Circular black shutter                                |
| `eris:windup`     | `&#124;`  | `windup/progress.png`       | 3/1        | Progress fill                                         |
| `eris:windup`     | `-`       | `windup/ready.png`          | 3/2        | Pulsing ready indicator                               |

Transition glyphs cover the screen. Text alpha controls their reveal/shutter
progress; RGB tints the noise and scrolling background. `eris:transition` also
provides `/` as a -256 space for overlapping glyphs.

Windup glyphs retain their font-defined position and size (height 272, ascent 0).
Progress alpha controls a 40-pixel fill across the 256-pixel canvas and is not
used as output opacity. Pixel `(1, 0)` supplies the fill color; pixels matching
the marker or fill color are hidden. The ready glyph pulses in brightness and
opacity, with text alpha providing its base opacity. Bitmap alpha and the
renderer color modulator still apply.

Notification glyphs and notification text markers continue to use the existing
notification shader path. Recognized transition/windup markers take precedence
when their text color happens to match a notification marker.

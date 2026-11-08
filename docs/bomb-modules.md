# Bomb modules

Each instance owns a `BombModules` assembly. It is created when the case opens;
rejoining does not reshuffle or reset it. Every module owns its displays through
`ModuleContext`, and module displays use normal automatic viewing so everyone
in the instance can see them. The cursor indicator is visible only to the defusal
helper. Instance removal closes the assembly and its entities.

## First module: glyph buttons

Four white-concrete item models are tinted red, light red when hovered, and green
after a correct press. Each carries an upward-facing text display using the vanilla
`minecraft:alt` enchanting (Standard Galactic Alphabet) font. No additional font
asset is necessary. The letters `a`, `b`, `c`, and `d` are shuffled into a 2×2 layout
once per bomb; press their glyphs in alphabetical order. This is a prototype manual
rule; the module definition also accepts explicit labels and an index permutation.

Controls: ready with **F**, enter the surface view with **W**, hover with the mouse,
press with **left click**, and leave with **S**. Only the defuser can operate modules.
The helper can also use **W** to lean towards the bomb and **S** to return. Their
camera uses a separate target on the right of the case, looking down at its center,
while retaining the normal fullscreen cursor. Tune `CameraAnchor.HELPER_LEAN_POSITION`
to adjust this target independently of the defuser's camera. Both transitions use
the existing camera smoothing and leave the player's body in its seat.
Correct presses stay green; clicking an already accepted button does nothing.
A wrong unaccepted button resets the sequence and adds a case strike. All four
green buttons mean the module is solved and further clicks are ignored. Whole-game
win/loss handling is not yet connected to module completion or the strike limit.

The original shader projection probe remains at the case origin. A second item
display renders the existing cursor texture at the server's cursor position for
the defusal helper only, including on reconnect. It is hidden outside surface mode.
The defuser's shader cursor continues
to provide immediate local feedback; the shared indicator and hover colors follow
server-received mouse updates.

In surface mode only the projected rectangular cursor canvas has a black cover, with
a circular opening centered on the cursor tip. The rest of the screen is unchanged.
The opening reveals the scene after the existing blindness effect;
the cursor draws over the cover. Its radius is 12% of the viewport height, with a
one-pixel softened edge. Adjust `SURFACE_REVEAL_RADIUS` in `cursor.fsh` to resize it.

## Defining more modules

Implement `BombModule` for live state and `BombModule.Definition` for immutable
configuration. `mount(ModuleContext)` creates the state and displays. Use
`context.rect(u, v, width, height)` with module-local normalized coordinates and
`context.button` / `context.label` for the current display helpers. Other display
types can be added to the context. `hover(null)` means the defuser left the surface;
`press(point)` receives the cursor hotspot in bomb-normalized coordinates. Report
mistakes with `context.strike()`, expose completion with `solved()`, and release
the context in `close()`.

Add definitions in `BombModules.create`, for example:

```java
bomb.add(new GlyphButtons.Definition(
    List.of("d", "b", "a", "c"), List.of(2, 1, 3, 0)),
    new Surface.Rect(0, 0, 1, 1));
```

Slots are normalized rectangles over the case's 0.75 × 0.5 block work surface.
Keep slots disjoint: input is broadcast to modules, which test their own controls.
`Surface.cursor` accounts for the 0.08 block cursor footprint, matching `cursor.fsh`;
use it for both hit detection and indicators. Keep these constants aligned with
the shader and the corners encoded by `surface_cursor.json` if resizing the case.

## In-game verification

Start a game with all three roles ready. Enter surface mode as the defuser and
check each glyph is above its button and readable from that seat. Have the helper
watch the shared pointer and hover colors. Press the glyphs in order and check each
turns green; on a fresh puzzle, deliberately press out of order and check the strike
counter and reset. Leaving surface mode or disconnecting must clear hover and hide
the shared pointer. Rejoining an ongoing game must retain the puzzle's progress.

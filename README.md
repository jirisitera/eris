# Eris

A special type of Minecraft minigame. Made for vanilla Minecraft 26.3 and newer.

The item vertex shader is shared in `assets/eris/shaders/include/item_vertex.glsl`.
Minecraft's `item.vsh` and NeoForge's `item_unlit.vsh` are thin wrappers; the latter
disables directional item lighting while keeping the fragment shader's custom
outputs. Shader compatibility still depends on the client's Minecraft version.

## Roles and bomb controls

Instances hold up to three players. Joining players receive the first free role:
bomb defuser, manual expert, then defusal helper. Leaving frees that role without
changing the remaining players' roles; a fourth player starts another instance.
The action bar shows each player's role and controls.

`InstanceManager` defines the world origin and the defuser, helper, and expert
spawns. Each role's body stays at its own spawn, facing that spawn's yaw. Its
normal camera sits at eye height, 0.25 blocks behind that facing direction;
cursor rotation is relative to the spawn's yaw and pitch. Only the defuser's
camera leaves that position to lean over the case.

The bomb defuser presses **W** to lean over the open case and move a private
cursor along its surface. **S** returns to the normal camera and restores the
fullscreen cursor. **F** toggles emotes in normal mode; emotes are disabled while
leaning over the bomb. Manual experts and defusal helpers keep the normal cursor.

The surface plane is centered inside the open lower case. Its projection is
encoded by a private world item that stays spawned for the player's session.
The offhand data item stays equipped in both modes. A shader flag selects the
active cursor, using the underwater raw mouse angles for smooth rendering over
the visible case. Fullscreen and surface positions are remembered independently;
switching modes does not reset client rotation or wait for a correction echo.

The lean camera position is defined in `camera/BombView.java` relative to
`StructureLoader.CASE_SPAWN`. The plane geometry is in
`assets/eris/models/item/surface_cursor.json`.

Reload the resource pack after updating the server and pack.

Run `gradle test` to check role allocation, room overflow, role reuse, and cursor
mode/emote isolation.

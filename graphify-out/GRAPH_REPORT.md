# Graph Report - .  (2026-07-22)

## Corpus Check
- Large corpus: 397 files · ~616,275 words. Semantic extraction will be expensive (many Claude tokens). Consider running on a subfolder.

## Summary
- 1505 nodes · 3770 edges · 88 communities (72 shown, 16 thin omitted)
- Extraction: 88% EXTRACTED · 12% INFERRED · 0% AMBIGUOUS · INFERRED: 440 edges (avg confidence: 0.8)
- Token cost: 52,803 input · 0 output

## Community Hubs (Navigation)
- Dash Ghost Effect
- Item Generator
- Map Context Menu Input
- Creation Lighting & Store
- Map Object Rendering
- Object Editor Window
- System UI Input Handling
- Button Common Component
- Aura Renderer
- Skill Stat Formatting
- Object/NPC Field Editing
- Drop Manager Textures
- Base Object Behavior
- Drop Rendering & Lighting
- Editor Window Family
- Camera & Mouse Input
- Skill Tree Rendering
- Fire Skill Effects
- Items Editor Window
- Dash Effect
- User Interface Core
- Asset Picker Window
- Base View Input
- Map Object Rendering (Size/Position)
- Simulation Input Thread
- Background Worker Threads
- Tile Selector Window
- Map Chunk Generation
- Fireball Effect
- Loot Drop Rarity
- Main Application Entry
- Logo & Loading Screen
- Weather Renderer
- Window Section Layout
- Text Rendering Object
- Action Confirm Window
- Dialog Editor Window
- Base Object Helper
- Light Source
- Loading Window
- Fragility Particle Effect
- Weather Thread State
- Context Menu Window
- FX Context Drawing
- Map Terrain Generation
- Item Card Window
- Map Redirect Window
- Slash Swing Effect
- Wide Splash Effect
- Inventory Stack Manager
- Chain Bolt Effect
- Projectile Effect
- Perlin Noise
- Changelog: Windows & Quests
- Doom Spawner
- Ice Spike Effect
- Impact Anim Effect
- Speed Line Effect
- Ring Effect
- Streak Effect
- UI Tab Enum
- System UI Fonts
- Changelog: Window Redesign
- Changelog: Quest System Init
- Changelog: Dialog Window Setup
- Changelog: Dialog Editing
- Changelog: Lighting Tuning
- Touch Exec Callback
- Gradle Wrapper Script
- Changelog: Quest Deletion
- Changelog: Cursor Highlight
- Changelog: Text Editing Fixes
- Text Utility
- Desktop Launcher
- Changelog: Object Height/Occlusion
- Tile Picker Button
- Readme: Java Requirement

## God Nodes (most connected - your core abstractions)
1. `SystemUI` - 132 edges
2. `Store` - 86 edges
3. `ButtonCommon` - 74 edges
4. `BaseObject` - 70 edges
5. `ObjectEditorWindow` - 63 edges
6. `DropManager` - 58 edges
7. `Window` - 56 edges
8. `ItemsEditorWindow` - 54 edges
9. `Editor` - 53 edges
10. `NpcEditorWindow` - 50 edges

## Surprising Connections (you probably didn't know these)
- `UserInterface` --references--> `BaseObject`  [EXTRACTED]
  core/src/com/nicweiss/editor/components/UserInterface.java → core/src/com/nicweiss/editor/Generic/BaseObject.java
- `TileSelectorWindow` --references--> `BaseObject`  [EXTRACTED]
  core/src/com/nicweiss/editor/components/windows/TileSelectorWindow.java → core/src/com/nicweiss/editor/Generic/BaseObject.java
- `Creation` --inherits--> `BaseObject`  [EXTRACTED]
  core/src/com/nicweiss/editor/creations/Creation.java → core/src/com/nicweiss/editor/Generic/BaseObject.java
- `BaseObject` --references--> `Store`  [EXTRACTED]
  core/src/com/nicweiss/editor/Generic/BaseObject.java → core/src/com/nicweiss/editor/Generic/Store.java
- `Window` --references--> `BaseObject`  [EXTRACTED]
  core/src/com/nicweiss/editor/Generic/Window.java → core/src/com/nicweiss/editor/Generic/BaseObject.java

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Quest Management System (creation, objectives, editing, deletion, viewing, dedicated window)** — changes_quest_creation, changes_quest_objectives, changes_quest_title_description_edit, changes_quest_deletion, changes_quest_basic_view, changes_quest_management_window [INFERRED 0.80]
- **Dialog System (window, replicas, text editing, save/load, grouping, storage)** — changes_dialog_window, changes_dialog_replicas, changes_dialog_text_editing, changes_dialog_save_load, changes_dialog_grouping_by_object, changes_linkedhashmap_quests_dialogs [INFERRED 0.80]
- **Lighting Engine (height-aware, occlusion-aware, soft-transition, optimized recalculation)** — changes_obstacle_height_averaging, changes_soft_lighting_transitions, changes_lighting_recalc_optimization, changes_lighting_three_line_calc, changes_light_propagation_fix, changes_light_occlusion_calculation [INFERRED 0.85]

## Communities (88 total, 16 thin omitted)

### Community 0 - "Dash Ghost Effect"
Cohesion: 0.05
Nodes (26): DashGhostEffect, Override, SpriteBatch, Texture, Direction, DOWN, DOWN_LEFT, DOWN_RIGHT (+18 more)

### Community 1 - "Item Generator"
Cohesion: 0.07
Nodes (10): ItemGenerator, ItemModifierCatalog, ModifierDef, RarityDef, School, MAGIC, NEUTRAL, PHYSICAL (+2 more)

### Community 2 - "Map Context Menu Input"
Cohesion: 0.06
Nodes (13): Override, Override, MapContextMenuWindow, Override, SpriteBatch, Texture, NpcEditorWindow, Override (+5 more)

### Community 3 - "Creation Lighting & Store"
Cohesion: 0.05
Nodes (25): Creation, Override, ItemStack, SimCreature, Texture, TypeDef, SpawnManager, Faction (+17 more)

### Community 4 - "Map Object Rendering"
Cohesion: 0.07
Nodes (7): SpriteBatch, Batch, Color, MapObject, FileManager, Lighting, ZipOutputStream

### Community 5 - "Object Editor Window"
Cohesion: 0.09
Nodes (6): Override, Texture, ObjectEditorWindow, ObjectCatalog, TypeDef, SuppressWarnings

### Community 7 - "Button Common Component"
Cohesion: 0.14
Nodes (4): ButtonCommon, Batch, Texture, TypeDef

### Community 8 - "Aura Renderer"
Cohesion: 0.09
Nodes (19): AuraAnim, AuraRenderer, FrameAuraAnim, SpriteBatch, Texture, Mode, BREATHE, BURST (+11 more)

### Community 9 - "Skill Stat Formatting"
Cohesion: 0.09
Nodes (15): Branch, ELEMENTALIST, HERALD, WARRIOR, SkillCatalog, SkillDef, SkillKind, ACTIVE (+7 more)

### Community 10 - "Object/NPC Field Editing"
Cohesion: 0.09
Nodes (3): Override, Texture, QuestsEditorWindow

### Community 12 - "Base Object Behavior"
Cohesion: 0.08
Nodes (3): BaseObject, Batch, Texture

### Community 13 - "Drop Rendering & Lighting"
Cohesion: 0.15
Nodes (4): Drop, SpriteBatch, Texture, SpriteBatch

### Community 14 - "Editor Window Family"
Cohesion: 0.13
Nodes (5): Texture, Window, BaseCallBack, CallBack, Method

### Community 15 - "Camera & Mouse Input"
Cohesion: 0.15
Nodes (5): CameraSettings, Editor, Override, SpriteBatch, TileVisitor

### Community 17 - "Fire Skill Effects"
Cohesion: 0.11
Nodes (8): FireDoomEffect, FireWaveEffect, SpriteBatch, SkillEffect, StormBoltScheduler, Override, SpriteBatch, SkillEffectRenderer

### Community 18 - "Items Editor Window"
Cohesion: 0.15
Nodes (4): ItemsEditorWindow, Override, SpriteBatch, Texture

### Community 19 - "Dash Effect"
Cohesion: 0.19
Nodes (5): DashEffect, Override, SpriteBatch, EffectSink, MeleeStrikeEffects

### Community 20 - "User Interface Core"
Cohesion: 0.17
Nodes (3): Texture, UserInterface, PlayerProgress

### Community 23 - "Asset Picker Window"
Cohesion: 0.17
Nodes (5): AssetPickerWindow, Method, Override, SpriteBatch, Texture

### Community 24 - "Base View Input"
Cohesion: 0.18
Nodes (4): Override, SpriteBatch, View, InputProcessor

### Community 25 - "Map Object Rendering (Size/Position)"
Cohesion: 0.14
Nodes (3): SpriteBatch, SpriteBatch, SpriteBatch

### Community 26 - "Simulation Input Thread"
Cohesion: 0.18
Nodes (3): Controller, Override, SimulationInputThread

### Community 27 - "Background Worker Threads"
Cohesion: 0.18
Nodes (7): Store, CreationThread, Override, MagickThread, Override, PhysicThread, SkillCaster

### Community 28 - "Tile Selector Window"
Cohesion: 0.18
Nodes (6): Override, Texture, TileSelectorWindow, Texture, TextureObject, BOHelper

### Community 30 - "Fireball Effect"
Cohesion: 0.19
Nodes (6): FireballEffect, Texture, GroundFireEffect, Override, SpriteBatch, Texture

### Community 31 - "Loot Drop Rarity"
Cohesion: 0.16
Nodes (5): LabelGlow, HALO, NONE, OUTLINE, Uuid

### Community 32 - "Main Application Entry"
Cohesion: 0.30
Nodes (7): ApplicationAdapter, Override, SpriteBatch, Main, ExtendViewport, OrthographicCamera, Stage

### Community 33 - "Logo & Loading Screen"
Cohesion: 0.19
Nodes (6): Override, SpriteBatch, Texture, LoadProgress, Logo, ResourceLoader

### Community 34 - "Weather Renderer"
Cohesion: 0.31
Nodes (3): SpriteBatch, Texture, WeatherRenderer

### Community 35 - "Window Section Layout"
Cohesion: 0.29
Nodes (3): SpriteBatch, Texture, WindowSection

### Community 36 - "Text Rendering Object"
Cohesion: 0.23
Nodes (7): Batch, Override, TextObject, Font, Batch, BitmapFont, Color

### Community 38 - "Action Confirm Window"
Cohesion: 0.21
Nodes (4): ActionConfirnWindow, Override, SpriteBatch, Texture

### Community 41 - "Light Source"
Cohesion: 0.21
Nodes (3): Light, Transform, Texture

### Community 42 - "Loading Window"
Cohesion: 0.29
Nodes (4): Override, SpriteBatch, Texture, LoadingWindow

### Community 43 - "Fragility Particle Effect"
Cohesion: 0.30
Nodes (4): FragilityParticleEffect, Override, SpriteBatch, Texture

### Community 44 - "Weather Thread State"
Cohesion: 0.26
Nodes (6): Override, RainState, CLEAR, COOLDOWN, RAINING, WeatherThread

### Community 45 - "Context Menu Window"
Cohesion: 0.25
Nodes (3): ContextMenuWindow, SpriteBatch, Texture

### Community 46 - "FX Context Drawing"
Cohesion: 0.31
Nodes (3): FxContext, SpriteBatch, Texture

### Community 48 - "Item Card Window"
Cohesion: 0.24
Nodes (5): ItemCardWindow, Override, SpriteBatch, Texture, SpriteBatch

### Community 49 - "Map Redirect Window"
Cohesion: 0.29
Nodes (5): Override, SpriteBatch, Texture, MapRedirectWindow, SpriteBatch

### Community 50 - "Slash Swing Effect"
Cohesion: 0.38
Nodes (4): Override, SpriteBatch, Texture, SlashSwingEffect

### Community 51 - "Wide Splash Effect"
Cohesion: 0.38
Nodes (4): Override, SpriteBatch, Texture, WideSplashEffect

### Community 53 - "Chain Bolt Effect"
Cohesion: 0.33
Nodes (3): ChainBoltEffect, Override, SpriteBatch

### Community 54 - "Projectile Effect"
Cohesion: 0.36
Nodes (4): Override, SpriteBatch, Texture, ProjectileEffect

### Community 56 - "Changelog: Windows & Quests"
Cohesion: 0.25
Nodes (8): New Textures: Roads and River Bridge, Quest Management Window, Quest Objectives (creation, rename, exp reward, hide-on-start, delete), Separate-Surfaces Rendering Approach, Rework of Old Textures, Support for Two Sections in One Window, Separation of Window Content vs. Window Decoration, Window Content Split into Sections

### Community 58 - "Doom Spawner"
Cohesion: 0.39
Nodes (4): DoomSpawner, Override, SpriteBatch, Texture

### Community 60 - "Impact Anim Effect"
Cohesion: 0.39
Nodes (4): ImpactAnimEffect, Override, SpriteBatch, Texture

### Community 61 - "Speed Line Effect"
Cohesion: 0.36
Nodes (3): Override, SpriteBatch, SpeedLineEffect

### Community 62 - "Ring Effect"
Cohesion: 0.38
Nodes (3): Override, SpriteBatch, RingEffect

### Community 63 - "Streak Effect"
Cohesion: 0.38
Nodes (3): Override, SpriteBatch, StreakEffect

### Community 64 - "UI Tab Enum"
Cohesion: 0.29
Nodes (7): Tab, DROP, INVENTORY, MENU, QUESTS, SKILLS, STATS

### Community 67 - "System UI Fonts"
Cohesion: 0.40
Nodes (4): BitmapFont, GlyphLayout, Texture, Rectangle

### Community 68 - "Changelog: Window Redesign"
Cohesion: 0.40
Nodes (5): Fix: Light Propagation into Unlit Spaces, Window Redesign Toward Light Color Tones, Scroll Slider for List Windows, Window Border Rendering Redesign, Generic Window-Management Functionality

### Community 69 - "Changelog: Quest System Init"
Cohesion: 0.40
Nodes (5): Quests and Dialogs Migrated to LinkedHashMap, Inverted List Scrolling, Basic Quest Viewer, Quest Creation, Scrollable Text Input Field

### Community 71 - "Changelog: Dialog Window Setup"
Cohesion: 0.50
Nodes (4): Control Buttons as a Base-Window Section, Dialog Save/Load into Shared Map File, Dialog Window, Text Input Window and System

### Community 72 - "Changelog: Dialog Editing"
Cohesion: 0.50
Nodes (4): Dialogs of One Object Grouped Under Nested Structure, Dialog Line/Replica Functionality, Dialog Text Setting/Editing, Text Edit Window Rework

### Community 73 - "Changelog: Lighting Tuning"
Cohesion: 0.50
Nodes (4): Lighting Recalculation Optimization, Lighting Calculation Along Three Lines, Averaged Obstacle-Height Calculation for Lighting, Soft Lighting Transition Tuning

### Community 75 - "Gradle Wrapper Script"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 76 - "Changelog: Quest Deletion"
Cohesion: 0.67
Nodes (3): Action Confirmation Modal, Quest Deletion, Window Backdrop Render Fix (control-button windows)

### Community 77 - "Changelog: Cursor Highlight"
Cohesion: 0.67
Nodes (3): Context Menu Mechanic, Highlight/Border for Cursor-Hovered Placeable Element, Null/Zero Element Behavior (no placement, blue tile highlight)

### Community 78 - "Changelog: Text Editing Fixes"
Cohesion: 0.67
Nodes (3): Multiline Text Support in List Rendering, Quest Title/Description Editing, Text Edit Window Focus-Capture Fix

## Knowledge Gaps
- **73 isolated node(s):** `LoadProgress`, `TilePickerButton`, `NONE`, `OUTLINE`, `HALO` (+68 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **16 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Store` connect `Background Worker Threads` to `Dash Ghost Effect`, `Map Context Menu Input`, `Creation Lighting & Store`, `Map Object Rendering`, `Object Editor Window`, `System UI Input Handling`, `Object/NPC Field Editing`, `Drop Manager Textures`, `Base Object Behavior`, `Drop Rendering & Lighting`, `Editor Window Family`, `Camera & Mouse Input`, `Fire Skill Effects`, `Items Editor Window`, `User Interface Core`, `Asset Picker Window`, `Base View Input`, `Simulation Input Thread`, `Tile Selector Window`, `Main Application Entry`, `Logo & Loading Screen`, `Weather Renderer`, `Window Section Layout`, `Action Confirm Window`, `Dialog Editor Window`, `Base Object Helper`, `Light Source`, `Loading Window`, `Weather Thread State`, `Context Menu Window`, `FX Context Drawing`, `Item Card Window`, `Map Redirect Window`, `Inventory Stack Manager`, `System UI Fonts`?**
  _High betweenness centrality (0.547) - this node is a cross-community bridge._
- **Why does `SystemUI` connect `System UI Input Handling` to `UI Tab Enum`, `System UI Fonts`, `Creation Lighting & Store`, `Skill Stat Formatting`, `Skill Tree Rendering`, `Item Stats & Tooltips`, `Inventory Grid Management`, `Background Worker Threads`?**
  _High betweenness centrality (0.154) - this node is a cross-community bridge._
- **Why does `ObjectEditorWindow` connect `Object Editor Window` to `Map Context Menu Input`, `Window Builders`, `Action Confirm Window`, `Button Common Component`, `Dialog Editor Window`, `Object/NPC Field Editing`, `Editor Window Family`, `Map Redirect Window`, `User Interface Core`, `Asset Picker Window`, `Delete Confirmation Callbacks`, `Background Worker Threads`, `Tile Selector Window`?**
  _High betweenness centrality (0.085) - this node is a cross-community bridge._
- **What connects `LoadProgress`, `TilePickerButton`, `NONE` to the rest of the system?**
  _73 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Dash Ghost Effect` be split into smaller, more focused modules?**
  _Cohesion score 0.05251141552511415 - nodes in this community are weakly interconnected._
- **Should `Item Generator` be split into smaller, more focused modules?**
  _Cohesion score 0.06599597585513078 - nodes in this community are weakly interconnected._
- **Should `Map Context Menu Input` be split into smaller, more focused modules?**
  _Cohesion score 0.05662862159789289 - nodes in this community are weakly interconnected._
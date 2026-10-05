# Third-party components

The included Gradle Wrapper launcher and bootstrap JAR are distributed by the
Gradle project under the Apache License, Version 2.0.
https://github.com/gradle/gradle/blob/v8.8.0/LICENSE
https://www.apache.org/licenses/LICENSE-2.0

Minecraft, Forge, and their runtime dependencies are obtained by ForgeGradle
and are not redistributed in this source archive or the mod JAR. They retain
their respective licenses and terms.

The road code, small procedural textures, item models, and test structure in
this project were produced for this independent mod with AI assistance.

Stone, metal, soil and oak-leaf materials reference vanilla Minecraft runtime
textures. Those texture files are not copied into this mod or source archive.

## CityBuild / Yunbei Urban Construction traffic signal

- Supplied reference: `CityBuild-0.7.3-road-name-sign-thin-pillar-fix-source.zip`.
- Upstream: `BGSDT/yunbei-urban-construction`; MIT license, included in
  `THIRD_PARTY_LICENSES/LICENSE_MIT_YUNBEIUC.txt`.
- Reused: `traffic_lights_black_horizontal.json`, `traffic_lights_black.png`,
  and the green/amber/red frames of `lights/auto_signal_ns.png` from CityBuild.
- Reused support geometry: `road_pole_longitudinal.json`, `road_pole_horizontal.json`,
  and `road_pole_foundations.json`. The original elements and rotations are baked
  with `tools/import_cb_poles.py`; their source JSON files are included. SR places
  and scales these modular support models with world-lit metal material.
- The original authored cuboids, rotations and face UVs are baked into a mesh
  using `tools/import_cb_signal.py`. Its input JSON is included beside the mesh.
  Signal faces are oriented freely to each approach; the original CB/Yunbei
  BlockEntity, controller and network code are not included or required.
- Existing interchange signals use a shared 20-second phase clock. Independent
  junctions added in 0.20 use saved green, amber and all-red timings with approach
  phase groups. Only lamp textures are emissive; housing and supports receive
  world lighting.

## CityBuild / RoadChina asphalt texture (0.17)

- Supplied reference: `CityBuild-0.7.3-road-name-sign-thin-pillar-fix-source.zip`.
- Original asset: `assets/roadchina/textures/block/asphalt_road.png`.
- The supplied asset inventory attributes it to `Epochwood/RoadChina`,
  Forge 1.20.1-1.0.2 supplied JAR.
- Licence: GPL-3.0, reproduced in `THIRD_PARTY_LICENSES/LICENSE_GPL-3.0_ROADCHINA.txt`.
- The PNG is copied byte-for-byte to `assets/splineroads/textures/road/asphalt.png`;
  the bitmap is unmodified. SR's existing world-space UV repeat and lighting are retained.
- The complete editable PNG, provenance/hash record and corresponding SR source
  are distributed in the accompanying source archive. No RoadChina Java code is reused.

## SR0.23 gray vertical vehicle and pavement signals

- Supplied reference: `CityBuild-0.7.3-road-name-sign-thin-pillar-fix-source.zip`.
- CB asset names: `traffic_lights_gray_vertical` and `traffic_lights_pavement_gray`.
- Yunbei Urban Construction assets; MIT license included as `LICENSE_MIT_YUNBEIUC.txt`.
- Original JSON cuboids, rotations and UV coordinates are retained. The authored gray housing textures and vehicle/pedestrian animation frames are packed into an atlas. The source JSON, textures, baked meshes and reproducible `tools/import_cb_junction_signals.py` are included.
- SR supplies junction phase timing and placement. No CB controller/runtime code or runtime dependency is bundled. These models replace the TrafficCraft-based junction heads introduced in 0.22.

## SR 0.31 CityBuild / Yunbei road signs and municipal equipment

- Source: supplied `CityBuild-0.7.3-road-name-sign-thin-pillar-fix-source.zip`,
  CityBuild's Yunbei Urban Construction assets under the MIT license reproduced
  in `THIRD_PARTY_LICENSES/LICENSE_MIT_YUNBEIUC.txt`.
- Reused: 339 road-sign models; road detection camera, lighting lamp, radar speed
  detector, pole text display and solar panel; referenced textures and four
  expressway logo textures. No CB runtime/controller/network classes are bundled.
- Original model JSON, texture PNG and consulted text-layout renderer source are
  retained under `tools/cb_sign_sources/`. The importer records source hashes,
  resolves parent models, bakes authored rotations and face UVs, and normalizes
  the readable front. Runtime texture atlas limits large source textures to 128px;
  originals remain available in the source archive. The four route logos are
  included at their source resolution.
- Reproduction: `python tools/import_cb_signs.py PATH_TO_CB_SOURCE`.
  The generated catalog, mesh resources and atlas are included in the JAR.
  SR supplies road-relative placement, ownership, storage, editing and rendering.
- The new sign-editor and pole-installer item icons are original procedural SR
  artwork, distinct from the reused CB assets.

### 0.31.1 资源转换修正

保留 CB 原始模型与纹理，校正 UV 像素取样、element rotation rescale 和当前样式的渲染分支；将同向共面重叠面裁切为互不重叠的片，避免深度争抢。未另绘替代牌面。全部 344 个模型继续可选，49 个可编辑样式合计 169 个有效字段；0.31.0 错选其他分支的字段已移除。导入可通过 `tools/import_cb_signs.py` 重现。

## SR 0.35.2 CityBuild / Yunbei green sound barrier

- Source: the supplied CityBuild source, Yunbei Urban Construction assets under
  the MIT license included in `THIRD_PARTY_LICENSES/LICENSE_MIT_YUNBEIUC.txt`.
- Original `sound_barrier_1_green_normal.json` and
  `sound_barrier_1_green.png`; the PNG is copied without modification.
- `tools/import_cb_noise.py` bakes original cuboids, element rotations and UVs.
  Source JSON and SHA-256 provenance are included beside `cb_noise.mesh`.
- Near geometry retains 990 authored faces per two-metre module. Distant geometry
  omits subpixel louvres and thin end caps, retaining panel faces, bent cap,
  frame silhouette and translucent panes. No CityBuild runtime is required.

### 0.40 terrain-atlas copy

`assets/splineroads/textures/block/terrain_asphalt.png` is a byte-identical additional
copy of the above RoadChina asphalt texture for the block texture atlas. The same
upstream attribution and GPL-3.0 texture license apply; it is not a newly authored
SR texture. `terrain_paint.png` is copied from this project's existing `road/white.png`.

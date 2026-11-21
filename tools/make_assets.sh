#!/bin/bash
# Builds DeepDive's assets (run from the workspace root): models scaled by their largest extent,
# panorama and its light, the sea floor, music and the synthesized effects.
set -eu
A=Pico_Vibe_Lab/DeepDive/app/app/src/main/assets
L=assets_lab/deepsea
B=~/.local/blender-dl/blender-4.2.3-linux-x64/blender
CMGEN=~/.local/filament-dl/filament/bin/cmgen
mkdir -p $A/models $A/textures $A/audio $L/models
while read -r m faces tex; do
  [ -f $L/ai/$m.glb ] || { echo "no $m yet"; continue; }
  [ -f $L/models/$m.glb ] || $B -b --factory-startup --python tools/blender/prep_ai.py -- $L/ai/$m.glb $L/models/$m.glb 1 $faces $tex max >/dev/null 2>&1
  cp $L/models/$m.glb $A/models/
done <<'LIST'
sub 40000 2048
angler 30000 2048
jelly 20000 1024
squid 30000 2048
turtle 30000 2048
manta 20000 2048
lantern 8000 1024
wreck 40000 2048
vent 30000 1024
tube 25000 1024
whale 30000 2048
coral 25000 1024
chest 10000 1024
LIST
cp assets_lab/rail/models/trophy.glb $A/models/trophy.glb 2>/dev/null || true
cp Pico_Vibe_Lab/FarSight/app/app/src/main/assets/models/sky.glb $A/models/sky.glb
P=$L/tex/lab.jpeg
ffmpeg -loglevel error -y -i $P -vf scale=4096:2048 -q:v 3 $A/textures/sky_lab.jpg
if [ ! -f $A/textures/ibl_lab.ktx ]; then
  T=$(mktemp -d)
  $B -b --factory-startup --python Pico_Vibe_Lab/ClockShop/tools/ibl_source.py -- $P $T/lab.hdr 1.6 >/dev/null 2>&1
  (cd $T && $CMGEN --format=ktx --size=256 --deploy=$T/out lab.hdr >/dev/null 2>&1)
  cp $T/out/out_ibl.ktx $A/textures/ibl_lab.ktx
  rm -rf $T
fi
ffmpeg -loglevel error -y -i $L/tex/sand.jpeg -vf scale=1024:1024 -q:v 3 $A/textures/sand.jpg
uv run -q --with numpy python -I tools/sfx_synth.py $A/audio pop=ping camera=photo plop=sample chime:84=lit gong:1.0=zone click:60=low ding:88=goal fanfare=fanfare drum:0.9=lose
ffmpeg -loglevel error -y -i assets_lab/music_b3/deepsea.* -af "loudnorm=I=-22:TP=-1.5:LRA=11" -ar 44100 -ac 2 -c:a libvorbis -q:a 3 $A/audio/bgm.ogg
du -sh $A

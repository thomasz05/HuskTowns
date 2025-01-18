/*
 * This file is part of HuskTowns, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.william278.husktowns.hook.map;

import net.william278.husktowns.HuskTowns;
import net.william278.husktowns.claim.Chunk;
import net.william278.husktowns.claim.Claim;
import net.william278.husktowns.claim.TownClaim;
import net.william278.husktowns.claim.World;
import net.william278.husktowns.hook.MapHook;
import net.william278.husktowns.hook.PluginHook;
import net.william278.husktowns.town.Town;
import net.william278.husktowns.util.Task;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

public class DynmapHook extends MapHook {

    @Nullable
    private DynmapCommonAPI dynmapApi;
    @Nullable
    private MarkerSet markerSet;

    private Map<String, AreaMarker> existingAreaMarkers = new HashMap<>();

    private List<Town> refreshTowns = new ArrayList<>();
    private Task.Repeating task = null;

    @PluginHook(id = "Dynmap", register = PluginHook.Register.ON_ENABLE, platform = "common")
    public DynmapHook(@NotNull HuskTowns plugin) {
        super(plugin);
        DynmapCommonAPIListener.register(new DynmapCommonAPIListener() {
            @Override
            public void apiEnabled(@NotNull DynmapCommonAPI dynmapCommonAPI) {
                dynmapApi = dynmapCommonAPI;
                if (plugin.isLoaded()) {
                    onEnable();
                }
            }
        });
    }

    @Override
    public void onEnable() {
        getDynmap().ifPresent(api -> {
            clearAllMarkers();
            getMarkerSet();
            plugin.populateMapHook();

            task = plugin.getRepeatingTask(() -> {
                plugin.runAsync(() -> {

                    if (!refreshTowns.isEmpty()) {
                        for (Town town : refreshTowns) {

                            plugin.getClaimWorlds().forEach((world, claimWorld) -> {
                                claimWorld.getClaims().forEach((townID, claims) -> {
                                    if (townID == town.getId()) {
                                        try {
                                            handleTown(town, new HashMap<>(), world, claims);
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                });

                            });
                        }
                    }

                    refreshTowns.clear();
                });

            }, 20L * 20);
            task.run();
        });

    }

    private void addMarker(@NotNull TownClaim claim, @NotNull World world, @NotNull MarkerSet markerSet) {
        final Chunk chunk = claim.claim().getChunk();

        final String markerId = getClaimMarkerKey(claim, world);
        AreaMarker marker = markerSet.findAreaMarker(markerId);
        if (marker == null) {
            // Get the corner coordinates
            double[] x = new double[4];
            double[] z = new double[4];
            x[0] = chunk.getX() * 16;
            z[0] = chunk.getZ() * 16;
            x[1] = (chunk.getX() * 16) + 16;
            z[1] = (chunk.getZ() * 16);
            x[2] = (chunk.getX() * 16) + 16;
            z[2] = (chunk.getZ() * 16) + 16;
            x[3] = (chunk.getX() * 16);
            z[3] = (chunk.getZ() * 16) + 16;

            // Define the marker
            marker = markerSet.createAreaMarker(
                markerId,
                claim.town().getName(),
                false,
                world.getName(), x, z,
                false
            );
        }

        // Set the marker y level
        final double markerY = 64;
        marker.setRangeY(markerY, markerY);

        // Set the fill and stroke colors
        final int color = Integer.parseInt(claim.town().getColorRgb().substring(1), 16);
        marker.setFillStyle(0.5f, color);
        marker.setLineStyle(1, 1, color);
        marker.setLabel(claim.town().getName());
    }


      /*      plugin.runAsync(() -> {
            for (World world : plugin.getWorlds()) {
                plugin.getClaimWorld(world).ifPresent(claimWorld -> claimWorld.getClaims().forEach((townID, claims) -> {
                    Town town = plugin.findTown(townID).orElse(null);
                    handleClaimChunkOnWorld(town, world.getName(), claims);
                }));
            }
            });*/


    @Override
    public void removeClaimMarkers(@NotNull Town town) {
        plugin.getWorlds().forEach(world -> clearAllMarkers(world.getName(), town.getName()));
    }

    private void removeMarker(@NotNull TownClaim claim, @NotNull World world) {
        refreshTowns.add(claim.town());
    }


    @Override
    public void setClaimMarker(@NotNull TownClaim claim, @NotNull World world) {
        refreshTowns.add(claim.town());
    }

    @Override
    public void removeClaimMarker(@NotNull TownClaim claim, @NotNull World world) {
        plugin.runSync(() -> removeMarker(claim, world));
    }

    @Override
    public void setClaimMarkers(@NotNull List<TownClaim> townClaims, @NotNull World world) {

        HashMap<Town, List<Claim>> newWorldNameAreaMarkerMap = new HashMap<>();
        plugin.runSync(() -> getMarkerSet().ifPresent(markerSet -> {
            for (TownClaim claim : townClaims) {
                newWorldNameAreaMarkerMap.computeIfAbsent(claim.town(), k -> new ArrayList<>()).add(claim.claim());
            }
            plugin.runAsync(() -> newWorldNameAreaMarkerMap.forEach((town, claims) -> {
                HashMap<String, AreaMarker> newWorldNameMarkerMap = new HashMap<>();
                try {
                    handleTown(town, newWorldNameMarkerMap, world.getName(), claims);
                } catch (Exception e) {
                    System.out.println("Error adding area marker " + town.getName());
                }
            }));


        }));
    }

    @Override
    public void removeClaimMarkers(@NotNull List<TownClaim> townClaims, @NotNull World world) {
        plugin.runSync(() -> getMarkerSet().ifPresent(markerSet -> {
            for (TownClaim claim : townClaims) {
                removeMarker(claim, world);
            }
        }));
    }

    @Override
    public void clearAllMarkers() {
        plugin.runSync(() -> getMarkerSet().ifPresent(markerSet -> markerSet.getAreaMarkers()
            .forEach(AreaMarker::deleteMarker)));
    }

    public void clearAllMarkers(String world, String townName) {
        getMarkerSet().ifPresent(markerSet -> markerSet.getAreaMarkers()
                .forEach(areaMarker -> {
                    if (areaMarker.getWorld().equals(world) && areaMarker.getMarkerID().startsWith(townName)) {
                        areaMarker.deleteMarker();
                        existingAreaMarkers.remove(areaMarker.getMarkerID());
                    }
                }));
    }

    @NotNull
    private String getClaimMarkerKey(@NotNull TownClaim claim, @NotNull World world) {
        return plugin.getKey(
            Integer.toString(claim.town().getId()),
            Integer.toString(claim.claim().getChunk().getX()),
            Integer.toString(claim.claim().getChunk().getZ()),
            world.getName()
        ).toString();
    }

    private Optional<DynmapCommonAPI> getDynmap() {
        return Optional.ofNullable(dynmapApi);
    }

    private Optional<MarkerSet> getMarkerSet() {
        return getDynmap().map(api -> {
            final String setLabel = plugin.getSettings().getGeneral().getWebMapHook().getMarkerSetName();
            markerSet = api.getMarkerAPI().getMarkerSet(getMarkerSetKey());
            if (markerSet == null) {
                markerSet = api.getMarkerAPI().createMarkerSet(
                    getMarkerSetKey(),
                    setLabel,
                    api.getMarkerAPI().getMarkerIcons(),
                    false
                );
            } else {
                markerSet.setMarkerSetLabel(setLabel);
            }
            return markerSet;
        });
    }


    public enum direction {
        XPLUS,
        ZPLUS,
        XMINUS,
        ZMINUS;
    }

    public void floodFillTarget(TileFlags src, TileFlags dest, int x, int y) {
        int cnt = 0;
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{x, y});
        while (!stack.isEmpty()) {
            int[] nxt = stack.pop();
            x = nxt[0];
            y = nxt[1];
            if (src.getFlag(x, y)) {
                src.setFlag(x, y, false);
                dest.setFlag(x, y, true);
                cnt++;
                if (src.getFlag(x + 1, y))
                    stack.push(new int[]{x + 1, y});
                if (src.getFlag(x - 1, y))
                    stack.push(new int[]{x - 1, y});
                if (src.getFlag(x, y + 1))
                    stack.push(new int[]{x, y + 1});
                if (src.getFlag(x, y - 1))
                    stack.push(new int[]{x, y - 1});
            }
        }
    }


//NEW VERSION


    private void handleTown(Town town, Map<String, AreaMarker> newWorldNameAreaMarkerMap, String world, List<Claim> blocks) throws Exception {
        int poly_index = 0; /* Index of polygon for when a town has multiple shapes. */

        if (blocks.isEmpty())
            return;

        /* Build popup */
        clearAllMarkers(world, town.getName());


        StringBuilder membersList = new StringBuilder();

        town.getMembers().forEach((uuid, roleWeight) -> plugin.getDatabase().getUser(uuid)
                .ifPresent(user -> plugin.getRoles().fromWeight(roleWeight)
                        .ifPresent(role -> {
                            String roleName = role.getName();
                            roleName=   roleName.replace("娕", " \uD83D\uDC51");
                            Pattern pattern = Pattern.compile("§.");
                            roleName = pattern.matcher(roleName).replaceAll("");
                            membersList.append(user.user().getName()).append(" (").append(roleName).append("), ");
                        })
                ));

        String infoWindowPopup = "<div class=\"infowindow\">" +
                "<span style=\"font-size:120%;\"><b>" + town.getName() + "</b></span><br>" +
                "<span style=\"font-size:90%;\">" + town.getClaimCount() + " claims</span><br>" +
                "<span style=\"font-size:90%;\">" + town.getBio().orElse("No bio available") + "</span><br>" +
                "<span style=\"font-size:90%;\">" + town.getMembers().size() + " members: " + membersList + "</span>" +
                "</div>";

        //HashMap<String, TileFlags> worldNameShapeMap = new HashMap<>();
        LinkedList<Claim> townBlocksToDraw = new LinkedList<>();
        TileFlags currentShape = new TileFlags();
        ;

        /* Loop through blocks: set flags on blockmaps for worlds */
        for (Claim townBlock : blocks) {
            //   currentShape = worldNameShapeMap.get(world);  /* Find existing */
            //  if(currentShape == null) {
            //     currentShape = new TileFlags();
            //    worldNameShapeMap.put(world, currentShape);   /* Add fresh one */
            // }

            currentShape.setFlag(townBlock.getChunk().getX(), townBlock.getChunk().getZ(), true); /* Set flag for block */
            townBlocksToDraw.addLast(townBlock);

        }
        /* Loop through until we don't find more areas */
        while (townBlocksToDraw != null) {
            LinkedList<Claim> ourTownBlocks = null;
            LinkedList<Claim> townBlockLeftToDraw = null;
            TileFlags ourShape = null;
            int minx = Integer.MAX_VALUE;
            int minz = Integer.MAX_VALUE;
            for (Claim tb : townBlocksToDraw) {
                int tbX = tb.getChunk().getX();
                int tbZ = tb.getChunk().getZ();

                /* If we need to start shape, and this block is not part of one yet */
                if ((ourShape == null) && currentShape.getFlag(tbX, tbZ)) {
                    ourShape = new TileFlags();  /* Create map for shape */
                    ourTownBlocks = new LinkedList<Claim>();
                    floodFillTarget(currentShape, ourShape, tbX, tbZ);   /* Copy shape */
                    ourTownBlocks.add(tb); /* Add it to our node list */
                    minx = tbX;
                    minz = tbZ;
                }
                /* If shape found, and we're in it, add to our node list */
                else if ((ourShape != null) &&
                        (ourShape.getFlag(tbX, tbZ))) {
                    ourTownBlocks.add(tb);
                    if (tbX < minx) {
                        minx = tbX;
                        minz = tbZ;
                    } else if ((tbX == minx) && (tbZ < minz)) {
                        minz = tbZ;
                    }
                } else {  /* Else, keep it in the list for the next polygon */
                    if (townBlockLeftToDraw == null)
                        townBlockLeftToDraw = new LinkedList<>();
                    townBlockLeftToDraw.add(tb);
                }
            }
            townBlocksToDraw = townBlockLeftToDraw; /* Replace list (null if no more to process) */
            if (ourShape != null) {
                poly_index = traceTownOutline(town, newWorldNameAreaMarkerMap, poly_index, infoWindowPopup, world, ourShape, minx, minz);
            }
        }


        ///  drawTownMarkers(town, newWorldNameMarkerMap, townName, infoWindowPopup);
    }


    ;

    private int traceTownOutline(Town town, Map<String, AreaMarker> newWorldNameMarkerMap, int poly_index,
                                 String infoWindowPopup, String worldName, TileFlags ourShape, int minx, int minz) throws Exception {

        double[] x;
        double[] z;
        /* Trace outline of blocks - start from minx, minz going to x+ */
        int init_x = minx;
        int init_z = minz;
        int cur_x = minx;
        int cur_z = minz;
        direction dir = direction.XPLUS;
        ArrayList<int[]> linelist = new ArrayList<int[]>();
        linelist.add(new int[]{init_x, init_z}); // Add start point
        while ((cur_x != init_x) || (cur_z != init_z) || (dir != direction.ZMINUS)) {
            switch (dir) {
                case XPLUS: /* Segment in X+ direction */
                    if (!ourShape.getFlag(cur_x + 1, cur_z)) { /* Right turn? */
                        linelist.add(new int[]{cur_x + 1, cur_z}); /* Finish line */
                        dir = direction.ZPLUS;  /* Change direction */
                    } else if (!ourShape.getFlag(cur_x + 1, cur_z - 1)) {  /* Straight? */
                        cur_x++;
                    } else {  /* Left turn */
                        linelist.add(new int[]{cur_x + 1, cur_z}); /* Finish line */
                        dir = direction.ZMINUS;
                        cur_x++;
                        cur_z--;
                    }
                    break;
                case ZPLUS: /* Segment in Z+ direction */
                    if (!ourShape.getFlag(cur_x, cur_z + 1)) { /* Right turn? */
                        linelist.add(new int[]{cur_x + 1, cur_z + 1}); /* Finish line */
                        dir = direction.XMINUS;  /* Change direction */
                    } else if (!ourShape.getFlag(cur_x + 1, cur_z + 1)) {  /* Straight? */
                        cur_z++;
                    } else {  /* Left turn */
                        linelist.add(new int[]{cur_x + 1, cur_z + 1}); /* Finish line */
                        dir = direction.XPLUS;
                        cur_x++;
                        cur_z++;
                    }
                    break;
                case XMINUS: /* Segment in X- direction */
                    if (!ourShape.getFlag(cur_x - 1, cur_z)) { /* Right turn? */
                        linelist.add(new int[]{cur_x, cur_z + 1}); /* Finish line */
                        dir = direction.ZMINUS;  /* Change direction */
                    } else if (!ourShape.getFlag(cur_x - 1, cur_z + 1)) {  /* Straight? */
                        cur_x--;
                    } else {  /* Left turn */
                        linelist.add(new int[]{cur_x, cur_z + 1}); /* Finish line */
                        dir = direction.ZPLUS;
                        cur_x--;
                        cur_z++;
                    }
                    break;
                case ZMINUS: /* Segment in Z- direction */
                    if (!ourShape.getFlag(cur_x, cur_z - 1)) { /* Right turn? */
                        linelist.add(new int[]{cur_x, cur_z}); /* Finish line */
                        dir = direction.XPLUS;  /* Change direction */
                    } else if (!ourShape.getFlag(cur_x - 1, cur_z - 1)) {  /* Straight? */
                        cur_z--;
                    } else {  /* Left turn */
                        linelist.add(new int[]{cur_x, cur_z}); /* Finish line */
                        dir = direction.XMINUS;
                        cur_x--;
                        cur_z--;
                    }
                    break;
            }
        }
        /* Build information for specific area */
        String polyid = town.getName() + "__" + poly_index;
        int sz = linelist.size();
        x = new double[sz];
        z = new double[sz];
        for (int i = 0; i < sz; i++) {
            int[] line = linelist.get(i);
            x[i] = (double) line[0] * (double) 16;
            z[i] = (double) line[1] * (double) 16;
        }
        /* Find existing one */
        AreaMarker areaMarker = existingAreaMarkers.remove(polyid); /* Existing area? */
        if (areaMarker == null) {

            areaMarker = markerSet.createAreaMarker(polyid, town.getName(), false, worldName, x, z, false);
            if (areaMarker == null) {
                areaMarker = markerSet.findAreaMarker(polyid);
                if (areaMarker == null) {
                    throw new Exception("Error adding area marker " + polyid);
                }
            }
        } else {
            areaMarker.setCornerLocations(x, z); /* Replace corner locations */
            areaMarker.setLabel(town.getName());   /* Update label */
        }
        /* Set popup */
        areaMarker.setDescription(infoWindowPopup);
        /* Set line and fill properties */
        areaMarker.setFillStyle(0.5, Integer.parseInt(town.getColorRgb().substring(1), 16));
        areaMarker.setLineStyle(1, 0.8, Integer.parseInt(town.getColorRgb().substring(1), 16));

        /* Add to map */
        newWorldNameMarkerMap.put(polyid, areaMarker);
        poly_index++;
        return poly_index;
    }




}

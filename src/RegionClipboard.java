// $Id$
/*
 * WorldEdit
 * Copyright (C) 2010 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

import org.jnbt.*;
import java.io.*;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import com.sk89q.worldedit.*;

/**
 *
 * @author Albert
 */
public class RegionClipboard {
    private int[][][] data;
    private Point<Integer> min;
    private Point<Integer> max;
    private Point<Integer> origin;

    /**
     * Constructs the region instance. The minimum and maximum points must be
     * the respective minimum and maximum numbers!
     * 
     * @param min
     * @param max
     * @param origin
     */
    public RegionClipboard(Point<Integer> min, Point<Integer> max, Point<Integer> origin) {
        this.min = min;
        this.max = max;
        this.origin = origin;
        data = new int[(max.getX()) - min.getX() + 1]
            [max.getY() - min.getY() + 1]
            [max.getZ() - min.getZ() + 1];
    }

    /**
     * Get the width (X-direction) of the clipboard.
     *
     * @return
     */
    public int getWidth() {
        return max.getX() - min.getX() + 1;
    }

    /**
     * Get the length (Z-direction) of the clipboard.
     *
     * @return
     */
    public int getLength() {
        return max.getZ() - min.getZ() + 1;
    }

    /**
     * Get the height (Y-direction) of the clipboard.
     *
     * @return
     */
    public int getHeight() {
        return max.getY() - min.getY() + 1;
    }

    /**
     * Copy to the clipboard.
     *
     * @param editSession
     */
    public void copy(EditSession editSession) {
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    data[x - min.getX()][y - min.getY()][z - min.getZ()] =
                        editSession.getBlock(x, y, z);
                }
            }
        }
    }

    /**
     * Paste from the clipboard.
     *
     * @param editSession
     * @param origin Position to paste it from
     * @param noAir True to not paste air
     */
    public void paste(EditSession editSession, Point<Integer> newOrigin, boolean noAir) {
        int xs = getWidth();
        int ys = getHeight();
        int zs = getLength();

        int offsetX = min.getX() - origin.getX() + newOrigin.getX();
        int offsetY = min.getY() - origin.getY() + newOrigin.getY();
        int offsetZ = min.getZ() - origin.getZ() + newOrigin.getZ();
        
        for (int x = 0; x < xs; x++) {
            for (int y = 0; y < ys; y++) {
                for (int z = 0; z < zs; z++) {
                    if (noAir && data[x][y][z] == 0) { continue; }
                    
                    editSession.setBlock(x + offsetX, y + offsetY, z + offsetZ,
                                         data[x][y][z]);
                }
            }
        }
    }

    /**
     * Saves the clipboard data to a .schematic-format file.
     *
     * @param path
     * @throws IOException
     */
    public void saveSchematic(String path) throws IOException {
        int xs = getWidth();
        int ys = getHeight();
        int zs = getLength();

        HashMap<String,Tag> schematic = new HashMap<String,Tag>();
        schematic.put("Width", new ShortTag("Width", (short)xs));
        schematic.put("Length", new ShortTag("Length", (short)zs));
        schematic.put("Height", new ShortTag("Height", (short)ys));
        schematic.put("Materials", new StringTag("Materials", "Alpha"));

        // Copy blocks
        byte[] blocks = new byte[xs * ys * zs];
        for (int x = 0; x < xs; x++) {
            for (int y = 0; y < ys; y++) {
                for (int z = 0; z < zs; z++) {
                    int index = y * xs * zs + z * xs + x;
                    blocks[index] = (byte)data[x][y][z];
                }
            }
        }
        schematic.put("Blocks", new ByteArrayTag("Blocks", blocks));

        // Current data is not supported
        byte[] data = new byte[xs * ys * zs];
        schematic.put("Data", new ByteArrayTag("Data", data));

        // These are not stored either
        schematic.put("Entities", new ListTag("Entities", CompoundTag.class, new ArrayList<Tag>()));
        schematic.put("TileEntities", new ListTag("TileEntities", CompoundTag.class, new ArrayList<Tag>()));

        // Build and output
        CompoundTag schematicTag = new CompoundTag("Schematic", schematic);
        NBTOutputStream stream = new NBTOutputStream(new FileOutputStream(path));
        stream.writeTag(schematicTag);
        stream.close();
    }

    /**
     * Load a .schematic file into a clipboard.
     * 
     * @param path
     * @param origin
     * @return
     * @throws SchematicLoadException
     * @throws IOException
     */
    public static RegionClipboard loadSchematic(String path, Point<Integer> origin)
            throws SchematicLoadException, IOException {
        FileInputStream stream = new FileInputStream(path);
        NBTInputStream nbtStream = new NBTInputStream(stream);
        CompoundTag schematicTag = (CompoundTag)nbtStream.readTag();
        if (!schematicTag.getName().equals("Schematic")) {
            throw new SchematicLoadException("Tag \"Schematic\" does not exist or is not first");
        }
        Map<String,Tag> schematic = schematicTag.getValue();
        if (!schematic.containsKey("Blocks")) {
            throw new SchematicLoadException("Schematic file is missing a \"Blocks\" tag");
        }
        short xs = (Short)getChildTag(schematic, "Width", ShortTag.class).getValue();
        short zs = (Short)getChildTag(schematic, "Length", ShortTag.class).getValue();
        short ys = (Short)getChildTag(schematic, "Height", ShortTag.class).getValue();
        String materials = (String)getChildTag(schematic, "Materials", StringTag.class).getValue();
        if (!materials.equals("Alpha")) {
            throw new SchematicLoadException("Schematic file is not an Alpha schematic");
        }
        byte[] blocks = (byte[])getChildTag(schematic, "Blocks", ByteArrayTag.class).getValue();

        Point<Integer> min = new Point<Integer>(
                origin.getX(),
                origin.getY(),
                origin.getZ()
                );
        Point<Integer> max = new Point<Integer>(
                origin.getX() + xs - 1,
                origin.getY() + ys - 1,
                origin.getZ() + zs - 1
                );
        RegionClipboard clipboard = new RegionClipboard(min, max, origin);

        for (int x = 0; x < xs; x++) {
            for (int y = 0; y < ys; y++) {
                for (int z = 0; z < zs; z++) {
                    int index = y * xs * zs + z * xs + x;
                    clipboard.data[x][y][z] = blocks[index];
                }
            }
        }

        return clipboard;
    }

    private static Tag getChildTag(Map<String,Tag> items, String key, Class expected)
            throws SchematicLoadException {
        if (!items.containsKey(key)) {
            throw new SchematicLoadException("Schematic file is missing a \"" + key + "\" tag");
        }
        Tag tag = items.get(key);
        if (!expected.isInstance(tag)) {
            throw new SchematicLoadException(
                key + " tag is not of tag type " + expected.getName());
        }
        return tag;
    }
}

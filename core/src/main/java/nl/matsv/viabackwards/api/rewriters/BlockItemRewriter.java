/*
 * Copyright (c) 2016 Matsv
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package nl.matsv.viabackwards.api.rewriters;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import net.md_5.bungee.api.ChatColor;
import nl.matsv.viabackwards.api.BackwardsProtocol;
import nl.matsv.viabackwards.api.entities.blockitem.BlockItemSettings;
import nl.matsv.viabackwards.protocol.protocol1_11_1to1_12.data.BlockColors;
import nl.matsv.viabackwards.utils.Block;
import nl.matsv.viabackwards.utils.ItemUtil;
import us.myles.ViaVersion.api.minecraft.chunks.Chunk;
import us.myles.ViaVersion.api.minecraft.chunks.ChunkSection;
import us.myles.ViaVersion.api.minecraft.item.Item;
import us.myles.ViaVersion.protocols.protocol1_13to1_12_2.ChatRewriter;
import us.myles.viaversion.libs.opennbt.conversion.builtin.CompoundTagConverter;
import us.myles.viaversion.libs.opennbt.tag.builtin.*;

import java.util.HashMap;
import java.util.Map;

public abstract class BlockItemRewriter<T extends BackwardsProtocol> extends Rewriter<T> {

    private static final CompoundTagConverter converter = new CompoundTagConverter();
    private final Map<Integer, BlockItemSettings> replacementData = new HashMap<>();
    protected String nbtTagName;
    protected boolean jsonNameFormat = true;

    protected BlockItemRewriter(T protocol) {
        super(protocol);
        nbtTagName = "ViaBackwards|" + protocol.getClass().getSimpleName();
    }

    protected BlockItemSettings rewrite(int itemId) {
        BlockItemSettings settings = new BlockItemSettings(itemId);
        replacementData.put(itemId, settings);
        return settings;
    }

    public Item handleItemToClient(Item i) {
        if (i == null) return null;

        BlockItemSettings data = replacementData.get(i.getIdentifier());
        if (data == null) return i;

        Item original = ItemUtil.copyItem(i);
        if (data.hasRepItem()) {
            ItemUtil.copyItem(i, data.getRepItem());
            if (i.getTag() == null) {
                i.setTag(new CompoundTag(""));
            } else {
                // Handle colors
                CompoundTag tag = i.getTag().get("display");
                if (tag != null) {
                    StringTag nameTag = tag.get("Name");
                    if (nameTag != null) {
                        String value = nameTag.getValue();
                        if (value.contains("%vb_color%")) {
                            tag.put(new StringTag("Name", value.replace("%vb_color%", BlockColors.get(original.getData()))));
                        }
                    }
                }
            }

            // Backup data for toServer
            i.getTag().put(createViaNBT(original));

            // Keep original data (aisde from the name)
            if (original.getTag() != null) {
                for (Tag ai : original.getTag()) {
                    i.getTag().put(ai);
                }
            }

            i.setAmount(original.getAmount());
            // Keep original data when -1
            if (i.getData() == -1) {
                i.setData(original.getData());
            }
        }
        if (data.hasItemTagHandler()) {
            if (!i.getTag().contains(nbtTagName)) {
                i.getTag().put(createViaNBT(original));
            }
            data.getItemHandler().handle(i);
        }

        return i;
    }

    public Item handleItemToServer(Item item) {
        if (item == null) return null;
        if (item.getTag() == null) return item;

        CompoundTag tag = item.getTag();
        if (tag.contains(nbtTagName)) {
            CompoundTag via = tag.get(nbtTagName);

            short id = (short) via.get("id").getValue();
            short data = (short) via.get("data").getValue();
            byte amount = (byte) via.get("amount").getValue();
            CompoundTag extras = via.get("extras");

            item.setIdentifier(id);
            item.setData(data);
            item.setAmount(amount);
            item.setTag(converter.convert("", converter.convert(extras)));
            // Remove data tag
            tag.remove(nbtTagName);
        }
        return item;
    }

    public int handleBlockID(int idx) {
        int type = idx >> 4;
        int meta = idx & 15;

        Block b = handleBlock(type, meta);
        if (b == null) return idx;

        return (b.getId() << 4 | (b.getData() & 15));
    }

    public Block handleBlock(int blockId, int data) {
        BlockItemSettings settings = replacementData.get(blockId);
        if (settings == null || !settings.hasRepBlock()) return null;

        Block block = settings.getRepBlock();
        // For some blocks, the data can still be useful (:
        if (block.getData() == -1) {
            return block.withData(data);
        }
        return block;
    }

    protected void handleChunk(Chunk chunk) {
        // Map Block Entities
        Map<Pos, CompoundTag> tags = new HashMap<>();
        for (CompoundTag tag : chunk.getBlockEntities()) {
            if (!(tag.contains("x") && tag.contains("y") && tag.contains("z")))
                continue;
            Pos pos = new Pos(
                    (int) tag.get("x").getValue() & 0xF,
                    (int) tag.get("y").getValue(),
                    (int) tag.get("z").getValue() & 0xF);
            tags.put(pos, tag);

            // Handle given Block Entities
            ChunkSection section = chunk.getSections()[pos.getY() >> 4];
            if (section == null) continue;
            int block = section.getFlatBlock(pos.getX(), pos.getY() & 0xF, pos.getZ());
            int btype = block >> 4;

            BlockItemSettings settings = replacementData.get(btype);
            if (settings != null && settings.hasEntityHandler()) {
                settings.getBlockEntityHandler().handleOrNewCompoundTag(block, tag);
            }
        }

        for (int i = 0; i < chunk.getSections().length; i++) {
            ChunkSection section = chunk.getSections()[i];
            if (section == null) continue;

            boolean hasBlockEntityHandler = false;

            // Map blocks
            for (int j = 0; j < section.getPaletteSize(); j++) {
                int block = section.getPaletteEntry(j);
                int btype = block >> 4;
                int meta = block & 0xF;

                Block b = handleBlock(btype, meta);
                if (b != null) {
                    section.setPaletteEntry(j, (b.getId() << 4) | (b.getData() & 0xF));
                }

                // We already know that is has a handler
                if (hasBlockEntityHandler) continue;

                BlockItemSettings settings = replacementData.get(btype);
                if (section != null && settings.hasEntityHandler()) {
                    hasBlockEntityHandler = true;
                }
            }

            if (!hasBlockEntityHandler) continue;

            // We need to handle a Block Entity :(
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        int block = section.getFlatBlock(x, y, z);
                        int btype = block >> 4;
                        int meta = block & 15;

                        BlockItemSettings settings = replacementData.get(btype);
                        if (settings == null || !settings.hasEntityHandler()) continue;

                        Pos pos = new Pos(x, (y + (i << 4)), z);

                        // Already handled above
                        if (tags.containsKey(pos)) continue;

                        CompoundTag tag = new CompoundTag("");
                        tag.put(new IntTag("x", x + (chunk.getX() << 4)));
                        tag.put(new IntTag("y", y + (i << 4)));
                        tag.put(new IntTag("z", z + (chunk.getZ() << 4)));

                        settings.getBlockEntityHandler().handleOrNewCompoundTag(block, tag);
                        chunk.getBlockEntities().add(tag);
                    }
                }
            }
        }
    }

    protected boolean containsBlock(int block) {
        final BlockItemSettings settings = replacementData.get(block);
        return settings != null && settings.hasRepBlock();
    }

    protected boolean hasBlockEntityHandler(int block) {
        final BlockItemSettings settings = replacementData.get(block);
        return settings != null && settings.hasEntityHandler();
    }

    protected boolean hasItemTagHandler(int block) {
        final BlockItemSettings settings = replacementData.get(block);
        return settings != null && settings.hasItemTagHandler();
    }

    private CompoundTag createViaNBT(Item i) {
        CompoundTag tag = new CompoundTag(nbtTagName);
        tag.put(new ShortTag("id", (short) i.getIdentifier()));
        tag.put(new ShortTag("data", i.getData()));
        tag.put(new ByteTag("amount", i.getAmount()));
        if (i.getTag() != null) {
            tag.put(converter.convert("extras", converter.convert(i.getTag())));
        } else
            tag.put(new CompoundTag("extras"));
        return tag;
    }

    protected CompoundTag getNamedTag(String text) {
        CompoundTag tag = new CompoundTag("");
        tag.put(new CompoundTag("display"));
        text = ChatColor.RESET + text;
        ((CompoundTag) tag.get("display")).put(new StringTag("Name", jsonNameFormat ? ChatRewriter.legacyTextToJson(text) : text));
        return tag;
    }

    protected CompoundTag getNamedJsonTag(String text) {
        CompoundTag tag = new CompoundTag("");
        tag.put(new CompoundTag("display"));

        return tag;
    }

    private String getProtocolName() {
        return getProtocol().getClass().getSimpleName();
    }

    @Data
    @AllArgsConstructor
    @ToString
    @EqualsAndHashCode
    private static class Pos {

        private int x, y, z;
    }
}

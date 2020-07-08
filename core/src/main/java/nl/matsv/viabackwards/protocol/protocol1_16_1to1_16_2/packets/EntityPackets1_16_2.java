package nl.matsv.viabackwards.protocol.protocol1_16_1to1_16_2.packets;

import nl.matsv.viabackwards.api.rewriters.EntityRewriter;
import nl.matsv.viabackwards.protocol.protocol1_16_1to1_16_2.Protocol1_16_1To1_16_2;
import us.myles.ViaVersion.api.entities.Entity1_16Types;
import us.myles.ViaVersion.api.entities.Entity1_16_2Types;
import us.myles.ViaVersion.api.entities.EntityType;
import us.myles.ViaVersion.api.minecraft.item.Item;
import us.myles.ViaVersion.api.minecraft.metadata.MetaType;
import us.myles.ViaVersion.api.minecraft.metadata.Metadata;
import us.myles.ViaVersion.api.minecraft.metadata.types.MetaType1_14;
import us.myles.ViaVersion.api.remapper.PacketRemapper;
import us.myles.ViaVersion.api.type.Type;
import us.myles.ViaVersion.api.type.types.version.Types1_14;
import us.myles.ViaVersion.protocols.protocol1_16_2to1_16_1.ClientboundPackets1_16_2;
import us.myles.ViaVersion.protocols.protocol1_9_3to1_9_1_2.storage.ClientWorld;
import us.myles.viaversion.libs.gson.JsonElement;

public class EntityPackets1_16_2 extends EntityRewriter<Protocol1_16_1To1_16_2> {

    public EntityPackets1_16_2(Protocol1_16_1To1_16_2 protocol) {
        super(protocol);
    }

    @Override
    protected void registerPackets() {
        registerSpawnTrackerWithData(ClientboundPackets1_16_2.SPAWN_ENTITY, Entity1_16_2Types.EntityType.FALLING_BLOCK, Protocol1_16_1To1_16_2::getNewBlockStateId);
        registerSpawnTracker(ClientboundPackets1_16_2.SPAWN_MOB);
        registerExtraTracker(ClientboundPackets1_16_2.SPAWN_EXPERIENCE_ORB, Entity1_16_2Types.EntityType.EXPERIENCE_ORB);
        registerExtraTracker(ClientboundPackets1_16_2.SPAWN_PAINTING, Entity1_16_2Types.EntityType.PAINTING);
        registerExtraTracker(ClientboundPackets1_16_2.SPAWN_PLAYER, Entity1_16_2Types.EntityType.PLAYER);
        registerEntityDestroy(ClientboundPackets1_16_2.DESTROY_ENTITIES);
        registerMetadataRewriter(ClientboundPackets1_16_2.ENTITY_METADATA, Types1_14.METADATA_LIST);

        protocol.registerOutgoing(ClientboundPackets1_16_2.JOIN_GAME, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.INT); // Entity ID
                handler(wrapper -> {
                    boolean hardcore = wrapper.read(Type.BOOLEAN);
                    short gamemode = wrapper.read(Type.UNSIGNED_BYTE);
                    if (hardcore) {
                        gamemode |= 0x08;
                    }
                    wrapper.write(Type.UNSIGNED_BYTE, gamemode);
                });
                map(Type.BYTE); // Previous Gamemode
                map(Type.STRING_ARRAY); // World List
                map(Type.NBT); // Dimension Registry
                map(Type.STRING); // Dimension Type
                map(Type.STRING); // Dimension
                map(Type.LONG); // Seed
                handler(wrapper -> {
                    int maxPlayers = wrapper.read(Type.VAR_INT);
                    wrapper.write(Type.UNSIGNED_BYTE, (short) Math.max(maxPlayers, 255));
                });
                // ...
                handler(wrapper -> {
                    ClientWorld clientChunks = wrapper.user().get(ClientWorld.class);
                    String dimension = wrapper.get(Type.STRING, 0);
                    clientChunks.setEnvironment(dimension);
                    getEntityTracker(wrapper.user()).trackEntityType(wrapper.get(Type.INT, 0), Entity1_16_2Types.EntityType.PLAYER);
                });
            }
        });
    }

    @Override
    protected void registerRewrites() {
        registerMetaHandler().handle(e -> {
            Metadata meta = e.getData();
            MetaType type = meta.getMetaType();
            if (type == MetaType1_14.Slot) {
                meta.setValue(protocol.getBlockItemPackets().handleItemToClient((Item) meta.getValue()));
            } else if (type == MetaType1_14.BlockID) {
                meta.setValue(Protocol1_16_1To1_16_2.getNewBlockStateId((int) meta.getValue()));
            } else if (type == MetaType1_14.OptChat) {
                JsonElement text = meta.getCastedValue();
                if (text != null) {
                    protocol.getTranslatableRewriter().processText(text);
                }
            }
            return meta;
        });

        mapTypes(Entity1_16_2Types.EntityType.values(), Entity1_16Types.EntityType.class);
        mapEntity(Entity1_16_2Types.EntityType.PIGLIN_BRUTE, Entity1_16_2Types.EntityType.PIGLIN).jsonName("Piglin Brute");
    }

    @Override
    protected EntityType getTypeFromId(int typeId) {
        return Entity1_16Types.getTypeFromId(typeId);
    }
}

package com.osole.controlurdragon.tracking;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * World save data (data/controlurdragon_records.dat) holding a record and backup for every tamed dragon.
 */
public class DragonRecordsData extends SavedData {
    private static final String FILE_NAME = "controlurdragon_records";

    private final Map<UUID, DragonRecord> records = new HashMap<>();
    /** Game time of each player's last restore, for the cooldown. */
    private final Map<UUID, Long> lastRestore = new HashMap<>();

    public static DragonRecordsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DragonRecordsData::new, DragonRecordsData::load, null), FILE_NAME);
    }

    public DragonRecord getOrCreate(UUID dragonId) {
        return this.records.computeIfAbsent(dragonId, DragonRecord::new);
    }

    public DragonRecord get(UUID dragonId) {
        return this.records.get(dragonId);
    }

    public List<DragonRecord> getByOwner(UUID ownerId) {
        List<DragonRecord> list = new ArrayList<>();
        for (DragonRecord record : this.records.values())
            if (ownerId.equals(record.ownerId)) list.add(record);
        list.sort(Comparator.comparing((DragonRecord r) -> r.state).thenComparing(r -> r.name));
        return list;
    }

    public long getLastRestore(UUID playerId) {
        return this.lastRestore.getOrDefault(playerId, Long.MIN_VALUE);
    }

    public void setLastRestore(UUID playerId, long gameTime) {
        this.lastRestore.put(playerId, gameTime);
        this.setDirty();
    }

    public static DragonRecordsData load(CompoundTag tag, HolderLookup.Provider provider) {
        DragonRecordsData data = new DragonRecordsData();
        ListTag list = tag.getList("Dragons", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            DragonRecord record = DragonRecord.load(list.getCompound(i));
            data.records.put(record.dragonId, record);
        }
        ListTag restores = tag.getList("Restores", Tag.TAG_COMPOUND);
        for (int i = 0; i < restores.size(); i++) {
            CompoundTag entry = restores.getCompound(i);
            data.lastRestore.put(entry.getUUID("Player"), entry.getLong("Time"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (DragonRecord record : this.records.values()) list.add(record.save());
        tag.put("Dragons", list);
        ListTag restores = new ListTag();
        this.lastRestore.forEach((player, time) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.putLong("Time", time);
            restores.add(entry);
        });
        tag.put("Restores", restores);
        return tag;
    }
}

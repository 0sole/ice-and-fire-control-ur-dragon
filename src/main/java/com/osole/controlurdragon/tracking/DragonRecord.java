package com.osole.controlurdragon.tracking;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public final class DragonRecord {
    public enum State {
        ALIVE,

        UNLOADED,

        STORED,

        LOST,

        DEAD
    }

    public final UUID dragonId;
    public UUID ownerId;
    public String entityType = "";
    public String name = "";
    public String dimension = "";
    public double x, y, z;
    public int stage;
    public State state = State.ALIVE;
    public long lastSeenGameTime;
    public long lostGameTime;
    public String lostReason = "";
    public String lostTrace = "";
    public CompoundTag snapshot = new CompoundTag();

    public DragonRecord(UUID dragonId) {
        this.dragonId = dragonId;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("DragonId", this.dragonId);
        if (this.ownerId != null) tag.putUUID("OwnerId", this.ownerId);
        tag.putString("EntityType", this.entityType);
        tag.putString("Name", this.name);
        tag.putString("Dimension", this.dimension);
        tag.putDouble("X", this.x);
        tag.putDouble("Y", this.y);
        tag.putDouble("Z", this.z);
        tag.putInt("Stage", this.stage);
        tag.putString("State", this.state.name());
        tag.putLong("LastSeen", this.lastSeenGameTime);
        tag.putLong("LostAt", this.lostGameTime);
        tag.putString("LostReason", this.lostReason);
        tag.putString("LostTrace", this.lostTrace);
        tag.put("Snapshot", this.snapshot);
        return tag;
    }

    public static DragonRecord load(CompoundTag tag) {
        DragonRecord record = new DragonRecord(tag.getUUID("DragonId"));
        if (tag.hasUUID("OwnerId")) record.ownerId = tag.getUUID("OwnerId");
        record.entityType = tag.getString("EntityType");
        record.name = tag.getString("Name");
        record.dimension = tag.getString("Dimension");
        record.x = tag.getDouble("X");
        record.y = tag.getDouble("Y");
        record.z = tag.getDouble("Z");
        record.stage = tag.getInt("Stage");
        try {
            record.state = State.valueOf(tag.getString("State"));
        } catch (IllegalArgumentException ignored) {
            record.state = State.UNLOADED;
        }
        record.lastSeenGameTime = tag.getLong("LastSeen");
        record.lostGameTime = tag.getLong("LostAt");
        record.lostReason = tag.getString("LostReason");
        record.lostTrace = tag.getString("LostTrace");
        record.snapshot = tag.getCompound("Snapshot");
        return record;
    }
}

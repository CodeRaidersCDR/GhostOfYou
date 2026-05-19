package com.coderaiderscdr.ghostofyou.recording;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class ActionEvent {

    public enum Type {
        BREAK_BLOCK,
        PLACE_BLOCK,
        ATTACK_ENTITY,
        USE_ITEM,
        TAKE_DAMAGE,
        JUMP,
        SWAP_ITEM
    }

    public final Type type;

    public final BlockPos pos;

    public final BlockState blockState;

    public final String entityTypeId;

    public final String itemId;

    public final float damageAmount;

    public final String damageSourceType;

    public final int newSlot;

    private ActionEvent(Builder b) {
        this.type            = b.type;
        this.pos             = b.pos;
        this.blockState      = b.blockState;
        this.entityTypeId    = b.entityTypeId;
        this.itemId          = b.itemId;
        this.damageAmount    = b.damageAmount;
        this.damageSourceType = b.damageSourceType;
        this.newSlot         = b.newSlot;
    }

    public static Builder builder(Type type) {
        return new Builder(type);
    }

    public static final class Builder {
        final Type type;
        BlockPos   pos;
        BlockState blockState;
        String     entityTypeId;
        String     itemId;
        float      damageAmount;
        String     damageSourceType;
        int        newSlot;

        private Builder(Type type) { this.type = type; }

        public Builder pos(BlockPos p)                          { this.pos = p;               return this; }
        public Builder blockState(BlockState s)                 { this.blockState = s;        return this; }
        public Builder entityType(String id)                    { this.entityTypeId = id;     return this; }
        public Builder item(String id)                          { this.itemId = id;           return this; }
        public Builder damage(float amount, String sourceType)  { this.damageAmount = amount; this.damageSourceType = sourceType; return this; }
        public Builder slot(int s)                              { this.newSlot = s;           return this; }

        public ActionEvent build() { return new ActionEvent(this); }
    }
}

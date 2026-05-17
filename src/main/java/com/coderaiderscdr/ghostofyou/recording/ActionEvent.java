package com.coderaiderscdr.ghostofyou.recording;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Immutable action event stored alongside frame data.
 * Built with a fluent {@link Builder}.
 */
public final class ActionEvent {

    /** All supported action event types. */
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

    /** Block position — set for {@link Type#BREAK_BLOCK} and {@link Type#PLACE_BLOCK}. */
    public final BlockPos pos;

    /** Block state — set for {@link Type#BREAK_BLOCK} and {@link Type#PLACE_BLOCK}. */
    public final BlockState blockState;

    /** Entity registry key string — set for {@link Type#ATTACK_ENTITY}. */
    public final String entityTypeId;

    /** Item registry key string — set for {@link Type#USE_ITEM}. */
    public final String itemId;

    /** Damage amount — set for {@link Type#TAKE_DAMAGE}. */
    public final float damageAmount;

    /** Damage source type string — set for {@link Type#TAKE_DAMAGE}. */
    public final String damageSourceType;

    /** Slot index 0-8 — set for {@link Type#SWAP_ITEM}. */
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

    /** Start building an event of the given type. */
    public static Builder builder(Type type) {
        return new Builder(type);
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

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

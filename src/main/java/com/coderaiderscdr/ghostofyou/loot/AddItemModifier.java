package com.coderaiderscdr.ghostofyou.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Global loot modifier that appends a configurable number of a given item to any
 * loot table that passes the supplied conditions.
 *
 * <p>JSON format:
 * <pre>{@code
 * {
 *   "type": "ghostofyou:add_item",
 *   "conditions": [ ... ],
 *   "item": "ghostofyou:ghost_essence",
 *   "min": 1,
 *   "max": 2
 * }
 * }</pre>
 */
public class AddItemModifier extends LootModifier {

    public static final Supplier<Codec<AddItemModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst -> codecStart(inst)
                    .and(ForgeRegistries.ITEMS.getCodec()
                            .fieldOf("item").forGetter(m -> m.item))
                    .and(Codec.INT.optionalFieldOf("min", 1).forGetter(m -> m.minCount))
                    .and(Codec.INT.optionalFieldOf("max", 1).forGetter(m -> m.maxCount))
                    .apply(inst, AddItemModifier::new))
    );

    private final Item item;
    private final int  minCount;
    private final int  maxCount;

    public AddItemModifier(LootItemCondition[] conditions, Item item, int minCount, int maxCount) {
        super(conditions);
        this.item     = item;
        this.minCount = minCount;
        this.maxCount = maxCount;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot,
                                                           LootContext context) {
        int count = minCount == maxCount
                ? minCount
                : minCount + context.getRandom().nextInt(maxCount - minCount + 1);
        if (count > 0) {
            generatedLoot.add(new ItemStack(item, count));
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return ModLootModifiers.ADD_ITEM.get();
    }
}

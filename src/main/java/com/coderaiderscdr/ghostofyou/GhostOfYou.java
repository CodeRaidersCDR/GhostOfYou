package com.coderaiderscdr.ghostofyou;

import com.coderaiderscdr.ghostofyou.block.ModBlocks;
import com.coderaiderscdr.ghostofyou.config.ModConfig;
import com.coderaiderscdr.ghostofyou.entity.ModEntities;
import com.coderaiderscdr.ghostofyou.event.BlockActionHandler;
import com.coderaiderscdr.ghostofyou.event.PlayerDeathHandler;
import com.coderaiderscdr.ghostofyou.event.PlayerTickHandler;
import com.coderaiderscdr.ghostofyou.event.ServerTickHandler;
import com.coderaiderscdr.ghostofyou.item.ModItems;
import com.coderaiderscdr.ghostofyou.network.ModNetwork;
import com.coderaiderscdr.ghostofyou.sound.ModSounds;
import com.coderaiderscdr.ghostofyou.util.ModLogger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

@Mod(GhostOfYou.MOD_ID)
public class GhostOfYou {

    public static final String MOD_ID = "ghostofyou";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public GhostOfYou() {
        setupDedicatedLog();

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModConfig.register(ModLoadingContext.get());

        ModItems.ITEMS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.BLOCK_ITEMS.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);
        ModSounds.SOUNDS.register(modBus);

        modBus.addListener(this::commonSetup);

        // Forge event bus — game events
        MinecraftForge.EVENT_BUS.register(PlayerDeathHandler.class);
        MinecraftForge.EVENT_BUS.register(PlayerTickHandler.class);
        MinecraftForge.EVENT_BUS.register(BlockActionHandler.class);
        MinecraftForge.EVENT_BUS.register(ServerTickHandler.class);

        // Client-only registration via DistExecutor to avoid server-side class loading
        DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.coderaiderscdr.ghostofyou.client.GhostOfYouClient.register(modBus));

        ModLogger.MAIN.info("=== Ghost of You mod loaded. Dedicated log: logs/ghostofyou.log ===");
    }

    private static void setupDedicatedLog() {
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration cfg = ctx.getConfiguration();

            PatternLayout layout = PatternLayout.newBuilder()
                    .withPattern("[%d{yyyy-MM-dd HH:mm:ss.SSS}] [%t/%level] %logger: %msg%n%xThrowable")
                    .withConfiguration(cfg)
                    .build();

            FileAppender fa = FileAppender.newBuilder()
                    .withFileName("logs/ghostofyou.log")
                    .withAppend(false)          // overwrite on each launch
                    .withName("GhostOfYouFile")
                    .withIgnoreExceptions(false)
                    .setLayout(layout)
                    .setConfiguration(cfg)
                    .build();
            fa.start();
            cfg.addAppender(fa);

            String[] cats = {
                "GhostOfYou/Main", "GhostOfYou/Playback",
                "GhostOfYou/Recording", "GhostOfYou/Spawn", "GhostOfYou/Lifecycle"
            };
            for (String cat : cats) {
                LoggerConfig existing = cfg.getLoggerConfig(cat);
                if (cat.equals(existing.getName())) {
                    existing.addAppender(fa, Level.DEBUG, null);
                } else {
                    LoggerConfig lc = new LoggerConfig(cat, Level.DEBUG, true);
                    lc.addAppender(fa, Level.DEBUG, null);
                    cfg.addLogger(cat, lc);
                }
            }
            ctx.updateLoggers();
        } catch (Exception e) {
            LOGGER.error("[GhostOfYou] Failed to set up dedicated log file", e);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}

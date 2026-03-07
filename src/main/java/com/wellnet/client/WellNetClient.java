/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.CommandDispatcher
 *  net.minecraft.commands.CommandSourceStack
 *  net.minecraftforge.api.distmarker.Dist
 *  net.minecraftforge.client.event.RegisterClientCommandsEvent
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber$Bus
 */
package com.wellnet.client;

import com.mojang.brigadier.CommandDispatcher;
import com.wellnet.client.WellNetClientCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="wellnet", value={Dist.CLIENT}, bus=Mod.EventBusSubscriber.Bus.FORGE)
public class WellNetClient {
    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        WellNetClientCommands.register((CommandDispatcher<CommandSourceStack>)event.getDispatcher());
    }
}


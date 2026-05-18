package dev.forb.dforblock.fabric.mixin

import dev.forb.dforblock.core.MCAdvancementMadeData
import dev.forb.dforblock.core.PlayerData
import dev.forb.dforblock.fabric.DForBlockFabric
import net.minecraft.advancements.AdvancementHolder
import net.minecraft.advancements.AdvancementType
import net.minecraft.server.PlayerAdvancements
import net.minecraft.server.level.ServerPlayer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Suppress("NonJavaMixin")
@Mixin(PlayerAdvancements::class)
open class PlayerAdvancementTrackerMixin {
    @Shadow
    lateinit var player: ServerPlayer

    @Inject(
        method = ["award"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V"
        )]
    )
    private fun onAdvancementGain(
        holder: AdvancementHolder,
        criterion: String,
        callBackInfo: CallbackInfoReturnable<Boolean>
    ) {
        if (!DForBlockFabric.isInitialized)
            return

        val displayOptional = holder.value.display()
        if (displayOptional.isEmpty) return
        val displayInfo = displayOptional.get()
        if (!displayInfo.shouldAnnounceChat())
            return

        val actionType = when (displayInfo.type) {
            AdvancementType.TASK -> "made"
            else -> "completed"
        }

        val payload = MCAdvancementMadeData(
            advancementName = displayInfo.title.string,
            advancementDescription = displayInfo.description.string,
            advancementType = displayInfo.type.name.lowercase(),
            actionType = actionType,
            playerIdentity = PlayerData.Minecraft(player.uuid, player.name.string, player.displayName.string)
        )
        DForBlockFabric.INSTANCE.dForBlock?.launch { onMinecraftAdvancement(payload) }
    }
}
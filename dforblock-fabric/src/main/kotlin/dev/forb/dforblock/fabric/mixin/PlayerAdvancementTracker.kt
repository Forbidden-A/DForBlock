package dev.forb.dforblock.fabric.mixin

import dev.forb.dforblock.core.*
import dev.forb.dforblock.fabric.DForBlockFabric
import dev.forb.dforblock.fabric.isLuckperms
import net.minecraft.advancements.AdvancementHolder
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

    @Inject(method = ["award"], at = [At(value = "INVOKE", target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V")])
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

        var payload = MCAdvancementMadeData(
            playerName = player.displayName.string,
            advancementName = displayInfo.title.string,
            advancementDescription = displayInfo.description.string,
            skinHint = SkinHint.Minecraft(player.uuid, player.name.string)
        )
        if (isLuckperms)
            payload = payload.copy(
                prefix = luckpermsPrefixByUUID(player.uuid),
                suffix = luckpermsSuffixByUUID(player.uuid),
            )

        DForBlockFabric.INSTANCE.dForBlock.onMinecraftAdvancement(payload)
    }
}
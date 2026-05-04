package dev.forb.dforblock.fabric.mixin

import dev.forb.dforblock.core.*
import dev.forb.dforblock.fabric.isLuckperms
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

    @Inject(method = ["award"], at = [At("RETURN")])
    private fun onAdvancementGain(
        holder: AdvancementHolder,
        criterion: String,
        callBackInfo: CallbackInfoReturnable<Boolean>
    ) {
        if (!callBackInfo.returnValue) return

        @Suppress("CAST_NEVER_SUCCEEDS")
        if (!(this as PlayerAdvancements).getOrStartProgress(holder).isDone) return

        val displayOptional = holder.value.display()
        if (displayOptional.isEmpty) return
        val displayInfo = displayOptional.get()

        val advancementType = when (displayInfo.type) {
            AdvancementType.TASK -> MCAdvancementMadePayload.MCAdvancementType.TASK
            AdvancementType.GOAL -> MCAdvancementMadePayload.MCAdvancementType.GOAL
            AdvancementType.CHALLENGE -> MCAdvancementMadePayload.MCAdvancementType.CHALLENGE
        }
        var payload = MCAdvancementMadePayload(
            playerName = player.displayName.string,
            advancementName = displayInfo.title.string,
            advancementDescription = displayInfo.description.string,
            type = advancementType,
            skinHint = SkinHint.Minecraft(player.uuid, player.name.string)
        )
        if (isLuckperms) {
            payload = payload.copy(
                prefix = luckpermsPrefixByUUID(player.uuid),
                suffix = luckpermsSuffixByUUID(player.uuid),
            )
        }

        DForBlock.handleMCAdvancementMade(payload)
    }
}
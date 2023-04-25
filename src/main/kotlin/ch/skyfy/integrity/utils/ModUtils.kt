package ch.skyfy.integrity.utils

import net.minecraft.server.network.ServerPlayerEntity

object ModUtils {

    fun getPlayerNameWithUUID(spe: ServerPlayerEntity) = "${spe.name.string}#${spe.uuidAsString}"

    /**
     * https://www.baeldung.com/kotlin/compare-lists#:~:text=In%20Kotlin%2C%20we%20use%20structural,in%20exactly%20the%20same%20order.
     */
    infix fun <T> List<T>.equalsIgnoreOrder(other: List<T>) = this.size == other.size && this.toSet() == other.toSet()

}
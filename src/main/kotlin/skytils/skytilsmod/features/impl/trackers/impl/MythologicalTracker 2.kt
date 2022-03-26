/*
 * Skytils - Hypixel Skyblock Quality of Life Mod
 * Copyright (C) 2022 Skytils
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package skytils.skytilsmod.features.impl.trackers.impl

import com.google.gson.JsonObject
import gg.essential.universal.UChat
import gg.essential.universal.UResolution
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.client.gui.GuiScreen
import net.minecraft.network.play.server.S02PacketChat
import net.minecraft.network.play.server.S2FPacketSetSlot
import net.minecraft.util.ChatComponentText
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import skytils.skytilsmod.Skytils
import skytils.skytilsmod.core.SoundQueue
import skytils.skytilsmod.core.structure.FloatPair
import skytils.skytilsmod.core.structure.GuiElement
import skytils.skytilsmod.events.impl.PacketEvent
import skytils.skytilsmod.features.impl.events.GriffinBurrows
import skytils.skytilsmod.features.impl.handlers.AuctionData
import skytils.skytilsmod.features.impl.trackers.Tracker
import skytils.skytilsmod.utils.*
import skytils.skytilsmod.utils.NumberUtil.nf
import skytils.skytilsmod.utils.graphics.ScreenRenderer
import skytils.skytilsmod.utils.graphics.SmartFontRenderer
import skytils.skytilsmod.utils.graphics.colors.CommonColors
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.regex.Pattern
import kotlin.math.pow

class MythologicalTracker : Tracker("mythological") {

    private val rareDugDrop: Pattern = Pattern.compile("^RARE DROP! You dug out a (.+)!$")
    private val mythCreatureDug =
        Pattern.compile("^(?:Oi|Uh oh|Yikes|Woah|Oh|Danger|Good Grief)! You dug out (?:a )?(.+)!$")

    private var lastMinosChamp = 0L

    @Suppress("UNUSED")
    enum class BurrowDrop(
        val itemId: String,
        val itemName: String,
        val rarity: ItemRarity,
        val isChat: Boolean = false,
        val mobDrop: Boolean = false,
        var droppedTimes: Long = 0L
    ) {
        REMEDIES("ANTIQUE_REMEDIES", "Antique Remedies", ItemRarity.EPIC),

        // this does have a chat message but it's just Enchanted Book
        CHIMERA("ENCHANTED_BOOK-ULTIMATE_CHIMERA-1", "Chimera", ItemRarity.COMMON),
        COINS("COINS", "Coins", ItemRarity.LEGENDARY, isChat = true),
        PLUSHIE("CROCHET_TIGER_PLUSHIE", "Crochet Tiger Plushie", ItemRarity.EPIC),
        COG("CROWN_OF_GREED", "Crown of Greed", ItemRarity.LEGENDARY, true),
        STICK("DAEDALUS_STICK", "Daedalus Stick", ItemRarity.LEGENDARY, mobDrop = true),
        SHELMET("DWARF_TURTLE_SHELMET", "Dwarf Turtle Shelmet", ItemRarity.RARE),
        FEATHER("GRIFFIN_FEATHER", "Griffin Feather", ItemRarity.RARE, isChat = true),
        RELIC("MINOS_RELIC", "Minos Relic", ItemRarity.EPIC),
        WASHED("WASHED_UP_SOUVENIR", "Washed-up Souvenir", ItemRarity.LEGENDARY, true);

        companion object {
            fun getFromId(id: String?): BurrowDrop? {
                return values().find { it.itemId == id }
            }

            fun getFromName(name: String?): BurrowDrop? {
                return values().find { it.itemName == name }
            }
        }
    }

    @Suppress("UNUSED")
    enum class BurrowMob(
        val mobName: String,
        val modId: String,
        val plural: Boolean = false,
        var dugTimes: Long = 0L,
    ) {

        GAIA("Gaia Construct", "GAIA_CONSTRUCT"),
        CHAMP("Minos Champion", "MINOS_CHAMPION"),
        HUNTER("Minos Hunter", "MINOS_HUNTER"),
        INQUIS("Minos Inquisitor", "MINOS_INQUISITOR"),
        MINO("Minotaur", "MINOTAUR"),
        LYNX("Siamese Lynxes", "SIAMESE_LYNXES", plural = true);

        companion object {
            fun getFromId(id: String?): BurrowMob? {
                return values().find { it.modId == id }
            }

            fun getFromName(name: String?): BurrowMob? {
                return values().find { it.mobName == name }
            }
        }
    }

    @SubscribeEvent
    fun onJoinWorld(event: EntityJoinWorldEvent) {
        if (lastMinosChamp != 0L && Utils.inSkyblock && mc.thePlayer != null && Skytils.config.trackMythEvent && event.entity is EntityOtherPlayerMP && event.entity.getXZDistSq(
                mc.thePlayer
            ) < 5.5 * 5.5 && System.currentTimeMillis() - lastMinosChamp <= 2500
        ) {
            if (event.entity.name == "Minos Champion") {
                println("Dug is: Minos Champion")
                lastMinosChamp = 0L
                BurrowMob.CHAMP.dugTimes++
                UChat.chat("§bSkytils: §eYou dug up a §2Minos Champion§e!")
            } else if (event.entity.name == "Minos Inquisitor") {
                println("Dug is: Minos Inquisitor")
                lastMinosChamp = 0L
                BurrowMob.INQUIS.dugTimes++
                UChat.chat("§bSkytils: §eYou dug up a §2Minos Inquisitor§e!")
            }
        }
        if (lastMinosChamp != 0L && System.currentTimeMillis() - lastMinosChamp > 2500) {
            println("Dug is: Unknown")
            lastMinosChamp = 0L
            BurrowMob.CHAMP.dugTimes++
            UChat.chat("§bSkytils: §eNo idea what you dug, counting as §2Minos Champion§e!")
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    fun onReceivePacket(event: PacketEvent.ReceiveEvent) {
        if (!Utils.inSkyblock || (!Skytils.config.trackMythEvent && !Skytils.config.broadcastMythCreatureDrop)) return
        when (event.packet) {
            is S02PacketChat -> {
                if (event.packet.type == 2.toByte() || !Skytils.config.trackMythEvent) return
                val unformatted = event.packet.chatComponent.unformattedText.stripControlCodes()
                if (unformatted.startsWith("RARE DROP! You dug out a ")) {
                    val matcher = rareDugDrop.matcher(unformatted)
                    if (matcher.matches()) {
                        (BurrowDrop.getFromName(matcher.group(1)) ?: return).droppedTimes++
                        markDirty<MythologicalTracker>()
                    }
                } else if (unformatted.startsWith("Wow! You dug out ") && unformatted.endsWith(
                        " coins!"
                    )
                ) {
                    BurrowDrop.COINS.droppedTimes += unformatted.replace(Regex("[^\\d]"), "").toLong()
                } else if (unformatted.contains("! You dug out ")) {
                    val matcher = mythCreatureDug.matcher(unformatted)
                    if (matcher.matches()) {
                        val mob = BurrowMob.getFromName(matcher.group(1)) ?: return
                        //for some reason, minos inquisitors say minos champion in the chat
                        if (mob == BurrowMob.CHAMP) {
                            Utils.cancelChatPacket(event)
                            lastMinosChamp = System.currentTimeMillis()
                        } else {
                            mob.dugTimes++
                            markDirty<MythologicalTracker>()
                        }
                    }
                } else if (unformatted.endsWith("/4)") && (unformatted.startsWith("You dug out a Griffin Burrow! (") || unformatted.startsWith(
                        "You finished the Griffin burrow chain! (4"
                    ))
                ) {
                    burrowsDug++
                    markDirty<MythologicalTracker>()
                } else if (unformatted.startsWith("RARE DROP! ")) {
                    for (drop in BurrowDrop.values()) {
                        if (!drop.mobDrop) continue
                        if (unformatted.startsWith("RARE DROP! ${drop.itemName}")) {
                            drop.droppedTimes++
                            break
                        }
                    }
                }
            }
            is S2FPacketSetSlot -> {
                val item = event.packet.func_149174_e() ?: return
                if (event.packet.func_149175_c() != 0 || mc.thePlayer == null || mc.thePlayer.ticksExisted <= 1) return
                val drop = BurrowDrop.getFromId(AuctionData.getIdentifier(item)) ?: return
                if (drop.isChat || drop.mobDrop) return
                val extraAttr = ItemUtil.getExtraAttributes(item) ?: return
                if (!extraAttr.hasKey("timestamp")) {
                    if (Skytils.config.broadcastMythCreatureDrop) {
                        val text = "§6§lRARE DROP! ${drop.rarity.baseColor}${drop.itemName} §b(Skytils User Luck!)"
                        if (Skytils.config.autoCopyRNGDrops) GuiScreen.setClipboardString(text)
                        UChat.chat(text)
                        SoundQueue.addToQueue(
                            SoundQueue.QueuedSound(
                                "note.pling",
                                2.0.pow(-9.0 / 12).toFloat(),
                                volume = 0.5f
                            )
                        )
                        SoundQueue.addToQueue(
                            SoundQueue.QueuedSound(
                                "note.pling",
                                2.0.pow(-2.0 / 12).toFloat(),
                                ticks = 4,
                                volume = 0.5f
                            )
                        )
                        SoundQueue.addToQueue(
                            SoundQueue.QueuedSound(
                                "note.pling",
                                2.0.pow(1.0 / 12).toFloat(),
                                ticks = 8,
                                volume = 0.5f
                            )
                        )
                        SoundQueue.addToQueue(
                            SoundQueue.QueuedSound(
                                "note.pling",
                                2.0.pow(3.0 / 12).toFloat(),
                                ticks = 12,
                                volume = 0.5f
                            )
                        )
                    }
                    if (Skytils.config.trackMythEvent) {
                        drop.droppedTimes++
                        markDirty<MythologicalTracker>()
                    }
                }
            }
        }
    }

    override fun resetLoot() {
        burrowsDug = 0L
        BurrowDrop.values().forEach { it.droppedTimes = 0L }
        BurrowMob.values().forEach { it.dugTimes = 0L }
    }

    override fun read(reader: InputStreamReader) {
        val obj = gson.fromJson(reader, JsonObject::class.java)
        burrowsDug = obj.get("dug").asLong
        for (entry in obj.get("items").asJsonObject.entrySet()) {
            (BurrowDrop.getFromId(entry.key) ?: continue).droppedTimes = entry.value.asLong
        }
        for (entry in obj.get("mobs").asJsonObject.entrySet()) {
            (BurrowMob.getFromId(entry.key) ?: continue).dugTimes = entry.value.asLong
        }
    }

    override fun write(writer: OutputStreamWriter) {
        val obj = JsonObject()

        obj.addProperty("dug", burrowsDug)

        val itemObj = JsonObject()
        for (item in BurrowDrop.values()) {
            itemObj.addProperty(item.itemId, item.droppedTimes)
        }
        obj.add("items", itemObj)

        val mobObj = JsonObject()
        for (mob in BurrowMob.values()) {
            mobObj.addProperty(mob.modId, mob.dugTimes)
        }
        obj.add("mobs", mobObj)
        gson.toJson(obj, writer)
    }

    override fun setDefault(writer: OutputStreamWriter) {
    }

    companion object {
        var burrowsDug = 0L

        init {
            MythologicalTrackerElement()
        }
    }

    class MythologicalTrackerElement : GuiElement("Mythological Tracker", FloatPair(150, 120)) {
        override fun render() {
            if (toggled && Utils.inSkyblock && GriffinBurrows.hasSpadeInHotbar && SBInfo.mode == SkyblockIsland.Hub.mode) {
                val sr = UResolution
                val leftAlign = actualX < sr.scaledWidth / 2f
                val alignment =
                    if (leftAlign) SmartFontRenderer.TextAlignment.LEFT_RIGHT else SmartFontRenderer.TextAlignment.RIGHT_LEFT
                ScreenRenderer.fontRenderer.drawString(
                    "Burrows Dug§f: ${nf.format(burrowsDug)}",
                    if (leftAlign) 0f else width.toFloat(),
                    0f,
                    CommonColors.YELLOW,
                    alignment,
                    SmartFontRenderer.TextShadow.NORMAL
                )
                var drawnLines = 1
                for (mob in BurrowMob.values()) {
                    if (mob.dugTimes == 0L) continue
                    ScreenRenderer.fontRenderer.drawString(
                        "${mob.mobName}§f: ${nf.format(mob.dugTimes)}",
                        if (leftAlign) 0f else width.toFloat(),
                        (drawnLines * ScreenRenderer.fontRenderer.FONT_HEIGHT).toFloat(),
                        CommonColors.CYAN,
                        alignment,
                        SmartFontRenderer.TextShadow.NORMAL
                    )
                    drawnLines++
                }
                for (item in BurrowDrop.values()) {
                    if (item.droppedTimes == 0L) continue
                    ScreenRenderer.fontRenderer.drawString(
                        "${item.rarity.baseColor}${item.itemName}§f: §r${nf.format(item.droppedTimes)}",
                        if (leftAlign) 0f else width.toFloat(),
                        (drawnLines * ScreenRenderer.fontRenderer.FONT_HEIGHT).toFloat(),
                        CommonColors.CYAN,
                        alignment,
                        SmartFontRenderer.TextShadow.NORMAL
                    )
                    drawnLines++
                }
            }
        }

        override fun demoRender() {
            val sr = UResolution
            val leftAlign = actualX < sr.scaledWidth / 2f
            val alignment =
                if (leftAlign) SmartFontRenderer.TextAlignment.LEFT_RIGHT else SmartFontRenderer.TextAlignment.RIGHT_LEFT
            ScreenRenderer.fontRenderer.drawString(
                "Burrows Dug§f: 1000",
                if (leftAlign) 0f else width.toFloat(),
                0f,
                CommonColors.YELLOW,
                alignment,
                SmartFontRenderer.TextShadow.NORMAL
            )
            var drawnLines = 1
            for (mob in BurrowMob.values()) {
                ScreenRenderer.fontRenderer.drawString(
                    "${mob.mobName}§f: 100",
                    if (leftAlign) 0f else width.toFloat(),
                    (drawnLines * ScreenRenderer.fontRenderer.FONT_HEIGHT).toFloat(),
                    CommonColors.CYAN,
                    alignment,
                    SmartFontRenderer.TextShadow.NORMAL
                )
                drawnLines++
            }
            for (item in BurrowDrop.values()) {
                ScreenRenderer.fontRenderer.drawString(
                    "${item.rarity.baseColor}${item.itemName}§f: §r100",
                    if (leftAlign) 0f else width.toFloat(),
                    (drawnLines * ScreenRenderer.fontRenderer.FONT_HEIGHT).toFloat(),
                    CommonColors.CYAN,
                    alignment,
                    SmartFontRenderer.TextShadow.NORMAL
                )
                drawnLines++
            }
        }

        override val height: Int
            get() = ScreenRenderer.fontRenderer.FONT_HEIGHT * 17
        override val width: Int
            get() = ScreenRenderer.fontRenderer.getStringWidth("Crochet Tiger Plushie: 100")

        override val toggled: Boolean
            get() = Skytils.config.trackMythEvent

        init {
            Skytils.guiManager.registerElement(this)
        }
    }

}
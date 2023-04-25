import ch.skyfy.integrity.config.ModInfo
import ch.skyfy.integrity.config.IntegrityConfig
import ch.skyfy.integrity.config.ResourcepacksInfo
import ch.skyfy.integrity.utils.ModUtils.equalsIgnoreOrder
import com.google.common.hash.Hashing
import com.google.common.io.Files
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.nio.file.Paths
import kotlin.io.path.outputStream
import kotlin.test.Test

class Tests {

    /**
     * Just a code to sort some folders by date
     */
    @Test
    fun sortFolders() {
        val names1 = listOf(
            "dev",
            "tutorials",
            "resources",
            "tools",
            "release"
        )

        val names2 = listOf(
            "client",
            "server",
            "common"
        )


        val root = Paths.get("E:\\Tech\\Projects\\MC\\MTEA_2023\\0.0.1")

        var now = System.currentTimeMillis()
        names1.forEach {
            root.resolve(it).toFile().setLastModified(now)
            now += 60_000
        }

        names2.forEach {
            root.resolve(it).toFile().setLastModified(now)
            now += 60_000
        }

    }


    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun generateModPackModList() {
        // I removed logical zoom from the list, lets see
        val modsFolderPath = Paths.get("C:\\Users\\skyfy16\\curseforge\\minecraft\\Instances\\TestProfile_1.19.4\\mods")
        val resourcepacksFolderPath = Paths.get("C:\\Users\\skyfy16\\curseforge\\minecraft\\Instances\\TestProfile_1.19.4\\resourcepacks")
        val integrityConfig = IntegrityConfig()
        modsFolderPath.toFile().listFiles { dir -> dir.extension == "jar" }.forEach {
            val hc = Files.asByteSource(it).hash(Hashing.sha256())
            integrityConfig.modInfos.add(ModInfo(it.name, hc.toString()))
        }
        resourcepacksFolderPath.toFile().listFiles { dir -> true }.forEach {
            if(it.isDirectory){
                integrityConfig.resourcepacksInfos.add(ResourcepacksInfo(it.name, "IMPOSSIBLE HASH"))
                return@forEach
            }
            val hc = Files.asByteSource(it).hash(Hashing.sha256())

            integrityConfig.resourcepacksInfos.add(ResourcepacksInfo(it.name, hc.toString()))
        }

        val json = Json { prettyPrint = true }
        json.encodeToStream(integrityConfig, modsFolderPath.resolve("integrity.json").outputStream())
    }

    @Test
    fun t() {
        val list = mutableListOf<ModInfo>()
        list.add(ModInfo("1.jar", "1234"))
        list.add(ModInfo("2.jar", "12345"))

        val list2 = mutableListOf<ModInfo>()
        list2.add(ModInfo("2.jar", "12345"))
        list2.add(ModInfo("1.jar", "1234"))
        list2.add(ModInfo("3.jar", "12334"))

        if(list.toSet() == list2.toSet()){
            println("==")
        }

        if(list equalsIgnoreOrder list2){
            println("euqla")
        }


    }

}
import ch.skyfy.integrity.config.IntegrityConfig
import ch.skyfy.integrity.config.ModInfo
import ch.skyfy.integrity.config.ResourcepacksInfo
import ch.skyfy.integrity.utils.IOUtils
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.nio.file.Paths
import java.util.*
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


    /**
     * This fun will generate an integrity.json file based on the mods/resourcepacks folder you give
     */
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun generateIntegrityDotJson() {
        val modsFolderPath = Paths.get("C:\\Users\\skyfy16\\curseforge\\minecraft\\Instances\\TestProfile_1.19.4\\mods")
        val resourcepacksFolderPath = Paths.get("C:\\Users\\skyfy16\\curseforge\\minecraft\\Instances\\TestProfile_1.19.4\\resourcepacks")


        val integrityConfig = IntegrityConfig()

        modsFolderPath.toFile().listFiles { dir -> dir.extension == "jar" }.forEach {
            integrityConfig.modInfos.add(ModInfo(it.name, IOUtils.getHash(it)))
        }
        resourcepacksFolderPath.toFile().listFiles().forEach {
            integrityConfig.resourcepacksInfos.add(ResourcepacksInfo(it.name, IOUtils.getHash(it)))
        }

        val json = Json { prettyPrint = true }
        json.encodeToStream(integrityConfig, modsFolderPath.resolve("integrity.json").outputStream())
    }

    @Test
    fun getHashForAFile(){
        val stringPath = "C:\\Users\\skyfy16\\curseforge\\minecraft\\Instances\\TestProfile_1.19.4\\resourcepacks\\Better Xray [ Vanilla ] 1.19"
//        val stringPath = "C:\\Users\\skyfy16\\curseforge\\minecraft\\Instances\\TestProfile_1.19.4\\mods\\ferritecore-5.2.0-fabric.jar"
        val path = Paths.get(stringPath)
        println(IOUtils.getHash(path.toFile()))
    }

}
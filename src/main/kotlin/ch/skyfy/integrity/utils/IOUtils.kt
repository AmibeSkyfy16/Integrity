package ch.skyfy.integrity.utils

import com.google.common.hash.Hashing
import com.google.common.io.Files
import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.SequenceInputStream
import java.nio.file.Path
import java.util.*

object IOUtils {

    fun getHash(file: File) : String {
        if(file.isDirectory){
            return try { hashDirectory(file.toPath(), true) } catch (e: IOException) { "IOException" }
        }
        return try { Files.asByteSource(file).hash(Hashing.sha256()).toString() } catch (e: IOException) { "IOException" }
    }

    /**
     * https://stackoverflow.com/questions/3010071/how-to-calculate-md5-checksum-on-directory-with-java-or-groovy
     */
    @Throws(IOException::class)
    fun hashDirectory(directoryPath: Path, includeHiddenFiles: Boolean): String {
        val directory = directoryPath.toFile()
        require(directory.isDirectory) { "Not a directory" }
        val fileStreams: Vector<FileInputStream> = Vector()
        collectFiles(directory, fileStreams, includeHiddenFiles)
        SequenceInputStream(fileStreams.elements()).use { sequenceInputStream -> return DigestUtils.sha256Hex(sequenceInputStream) }
    }

    @Throws(IOException::class)
    private fun collectFiles(
        directory: File, fileInputStreams: MutableList<FileInputStream>,
        includeHiddenFiles: Boolean
    ) {
        val files = directory.listFiles()
        if (files != null) {
            Arrays.sort(files, Comparator.comparing { obj: File -> obj.name })
            for (file in files) {
                if (includeHiddenFiles || !file.isHidden) {
                    if (file.isDirectory) {
                        collectFiles(file, fileInputStreams, includeHiddenFiles)
                    } else {
                        fileInputStreams.add(FileInputStream(file))
                    }
                }
            }
        }
    }

}
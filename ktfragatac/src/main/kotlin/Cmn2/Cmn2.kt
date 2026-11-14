package Cmn2

import java.io.File
import java.util.jar.*
import java.net.JarURLConnection
import java.util.Date

class Log(val verbose: Boolean = true) {
    fun <T> toFile(fname: String, data: Iterable<IndexedValue<T>>) {
        File(fname).bufferedWriter().use { out ->
            data.forEach { e ->
                out.write("%d\t%s\n".format(e.index, e.value.toString()))
            }
            out.close()
        }
        if (verbose) {
            Log.err("# Written:%s".format(fname))
        }
    }

    companion object {
        private val defaultLogger = Log()
        operator fun invoke(msg: String) {
            System.out.println(msg)
        }

        fun out(m: String) {
            System.out.println(m)
        }

        fun err(m: String) {
            System.err.println(m)
        }

        fun <T> toFile(fname: String, data: Iterable<IndexedValue<T>>, logger: Log? = null) {
            val logobj = logger ?: defaultLogger
            logobj.toFile(fname, data)
        }
    }

}

class Cfg(val flags: HashSet<String>, val keys: HashMap<String, String>, val elements: ArrayList<String>) {
    fun has(e: String): Boolean {
        return flags.contains(e) || keys.containsKey(e)
    }

    fun key(e: String): String {
        return keys.get(e) ?: ""
    }

    companion object {
        @JvmField
        val version: String = "0.1"

        @JvmStatic
        fun fromArray(alist: Array<String>) = fromArray(alist, "-", ":")

        @JvmStatic
        fun fromArray(alist: Array<String>, delimitFlag: String, delimitKey: String): Cfg {
            val flags = HashSet<String>()
            val keys = HashMap<String, String>()
            val elements = ArrayList<String>()
            for (e in alist) {
                if (e.startsWith(delimitFlag)) {
                    val fields = e.slice(0 until e.length).split(delimitKey)
                    if (fields.size < 2) {
                        flags.add(e.slice(0 until e.length))
                    } else {
                        keys[fields[0]] = fields.slice(1 until fields.size).joinToString(delimitKey)
                    }
                } else {
                    elements.add(e)
                }
            }
            return Cfg(flags, keys, elements)
        }
    }
}

class Cmn {
    companion object {
        // https://github.com/eclipse/eclipse-collections
        // https://www.eclipse.org/collections/#refGuide
        fun linef(fileName: String): List<String> {
            val f = File(fileName).bufferedReader()
            val lines = f.readLines()
            f.close()
            return lines
        }
    }
}


class Meta {
    companion object {
        fun compileTimeSeconds(cl: Any): Long {
            val resourceName: String = cl.javaClass.name.replace('.', '/') + ".class"
            val jarConx: JarURLConnection =
                ClassLoader.getSystemResource(resourceName).openConnection() as JarURLConnection
            return jarConx.getJarFile().getEntry("META-INF/MANIFEST.MF").getTime()
        }
        fun compileTime(cl: Any): String {
            return try {
                val milliseconds = compileTimeSeconds(cl)
                val time = java.util.Date(milliseconds)
                time.toString()
            } catch (e: Exception) {
                "__couldNotGetComplileTime__"
            }


        }
    }
}


// Using ktor buildFatJar
// ~\.jdks\corretto-16.0.2\bin\java.exe -jar .\build\libs\fat.jar

// https://javadoc.io/doc/com.github.samtools/htsjdk/latest/index.html
import JavaPkg.JavaMain
import Cmn2.Log
import Cmn2.Cfg
import  Cmn2.Meta
import BamUtils.*

class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val help = "\n\t~\\.jdks\\corretto-16.0.2\\bin\\java.exe -jar  .\\build\\libs\\fat.jar -bam:\$HOME\\ENCFF710ELD.bam -outprefix:hello7 -coords:chr2:1000000-1049990 -cutoffs:100,200,300 -mapq:10\n\n"
            val cfg = Cfg.fromArray(args)
            if (cfg.has("-jmain")) {
                JavaMain.main(args)
            } else if (cfg.has("-h") || cfg.has("-help")) {
                Log.err(help)
                Log.err("# Compiled[%s]".format(Meta.compileTime(Bam(""))))
            } else {
                val outprefix = cfg.key("-outprefix")
                val bamfile = cfg.key("-bam")
                if(outprefix == "" || bamfile == ""){
                    Log("-bamfile/-outprefix missing")
                    return
                }
                val coords = cfg.key("-coords").split(",").map { Genome.Coord.parse(it).toInterval() }
                val cutOffs = cfg.key("-cutoffs").split(",").map { it.toInt() }
                coords.forEach { coord ->
                    val bam = Bam(bamfile)
                    val fragSize = FragSize(coord, cutOffs, cfg, cfg.key("-mapq").toInt())
                    val outfile = "%s.%s.bed".format(outprefix, fragSize.tag())
                    fragSize.analyze(bam) // reusing same fragSize object with multiple bams will pool the statistics together
                    fragSize.toFile(outfile)
                }
            }
        }
    }
}


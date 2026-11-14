package BamUtils

import htsjdk.samtools.SAMRecord
import htsjdk.samtools.SamReader
import htsjdk.samtools.SamReaderFactory
import htsjdk.samtools.ValidationStringency
import htsjdk.samtools.util.CloseableIterator
import htsjdk.samtools.util.Interval
import htsjdk.samtools.util.SamRecordIntervalIteratorFactory
import java.io.File
import kotlin.math.*
import Cmn2.Cfg
import Cmn2.Log

data class LociRecord<T>(val chrom: String, val left: Int, var right: Int, val data: T) {
    override fun toString(): String {
        return "%s:%d-%d\t%s".format(chrom, left, right, data.toString())
    }

    fun tryToRightExtend(e: LociRecord<T>): Boolean {
        if (this.data!! == e.data!!) {
            if (this.chrom == e.chrom && this.right == e.left) {
                this.right = e.right
                return true
            }
        }
        return false
    }
}

class FragSize(val loci: Interval, val cutOffs: List<Int>, val cfg: Cfg, val mapQ: Int = 10) {
    inner class FSRecord(private val i: Int) {
        override fun equals(other: Any?): Boolean {
            if (other == null || !(other is FSRecord)) { return false }
            val e: FSRecord = other!!
            return totalFragSize[this.i] == totalFragSize[e.i] && signal[this.i] == signal[e.i] &&
                    signalByFragSize.indices.all { k -> signalByFragSize[k][this.i] == signalByFragSize[k][e.i] }
        }
        override fun toString(): String {
            return "%f\t%d\t%d\t%s\n".format(
                avgFragSize(i), totalFragSize[i], signal[i],
                signalByFragSize.indices.map({ k -> signalByFragSize[k][this.i] }).joinToString("\t")
            )
        }
        override fun hashCode(): Int { return i }
    }

    private val verbose:Boolean = !cfg.has("-quiet")
    private val chrom: String = loci.getContig()
    private val start: Int = loci.getStart()
    private val end: Int = loci.getEnd()
    private val N: Int = end - start + 1
    private val signalByFragSize = cutOffs.map { _ -> IntArray(N) }.toTypedArray()
    private val signal = IntArray(N)
    private val totalFragSize = IntArray(N)
    private var nReads: Int = 0
    private var nReadsAccepted: Int = 0
    private val lociStr = "%s-%d-%d".format(chrom, start, end)
    private val fragDistRange = 1000
    private val fragDist = IntArray(fragDistRange + 1)


    fun avgFragSize(i: Int): Double {
        return if (signal[i] > 0) totalFragSize[i].toDouble() / signal[i].toDouble() else 0.0
    }

    fun tag(): String {
        return lociStr
    }

    fun toFile(f: String) {
        Log.toFile(f + ".fragDist.txt", fragDist.withIndex())
        File(f).bufferedWriter().use { out ->
            out.write(FragSize.header(cutOffs))
            var prevRecord: LociRecord<FSRecord> = LociRecord(chrom, start, start + 1, FSRecord(0))
            for (i in totalFragSize.indices) {
                if (i > 0) {
                    val currentRecord = LociRecord(chrom, start + i, start + i + 1, FSRecord(i))
                    if (!prevRecord.tryToRightExtend(currentRecord)) {
                        out.write(prevRecord.toString())
                        prevRecord = currentRecord
                    }
                }
            }
            out.write(prevRecord.toString())
            out.close()
            Log.err("# Written %s".format(f))
        }
    }

    fun analyze(bam: Bam) {
        val reader = Bam.samReader(bam.bamFile)
        val iterator = Bam.samIterator(reader, listOf(loci))
        while (iterator.hasNext()) {
            signalFromFragment(iterator.next())
        }
        reader.close()
    }

    fun signalFromFragment(e: SAMRecord): Boolean {
        val insertSize: Int = e.getInferredInsertSize()
        val accepted = !e.getReadUnmappedFlag() &&
                !e.getMateUnmappedFlag() &&
                e.getProperPairFlag() &&
                !e.getDuplicateReadFlag() &&
                !e.getNotPrimaryAlignmentFlag() &&
                e.getMappingQuality() >= this.mapQ &&
                insertSize > 0
        this.nReads += 1
        if (accepted) {
            this.fragDist[min(insertSize, fragDistRange)] += 1
            this.nReadsAccepted += 1
            signalPerBase(e.getAlignmentStart(), e.getAlignmentEnd(), e.getInferredInsertSize())
        }
        return true
    }

    private fun signalPerBase(startCoord: Int, endCoord: Int, insertSize: Int) {
        var index: Int = max(startCoord - this.start, 0)
        val rawStopIndex: Int = endCoord - this.start
        val stopIndex = min(rawStopIndex, N-1)
        if(stopIndex < rawStopIndex){ if(verbose){ Log.out("# Fragment exceeds the region end") } }
        while (index <= stopIndex) { // handle cigar string
            totalFragSize[index] += insertSize
            signal[index] += 1
            cutOffs.indices.forEach { i ->
                if (insertSize <= cutOffs[i]) {
                    val stratified = signalByFragSize[i]
                    stratified[index] += 1
                }
            }
            index = index + 1
        }
    }

    companion object {
        fun header(cutOffs: List<Int>): String {
            return "#chr\tstart\tend\tAvgFrag\ttotalFrag\tCov\t%s\n".format(
                cutOffs.map({ "cov:%s".format(it) }).joinToString("\t")
            )
        }
    }
}

class Genome {
    data class Coord(val chrom: String, val start: Int, val end: Int) {
        fun toInterval(): Interval {
            return Interval(this.chrom, this.start, this.end)
        }

        companion object {
            fun parse(e: String): Coord {
                val fields = e.split(":")
                val startEnd = fields[1].split("-")
                assert(fields.size == 2 && startEnd.size == 2)
                return Coord(fields[0], startEnd[0].toInt(), startEnd[1].toInt())
            }

            fun <T> arrayToCoordinateRep(valueArray: List<Array<T>>) {
                valueArray.indices.forEach { _ ->

                }
                // implement
            }
        }
    }

}

class Bam(val bamFile: String) {
    companion object {
        fun chromosomeSizes(reader: SamReader): HashMap<String, Int> {
            val sizes = HashMap<String, Int>()
            reader.getFileHeader().getSequenceDictionary().getSequences().iterator().forEach { x ->
                sizes.put(x.getSequenceName(), x.getSequenceLength())
            }
            return sizes
        }

        fun samReader(bamFile: String): SamReader {
            return SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT).open(File(bamFile))
        }

        fun samIterator(reader: SamReader, intervals: List<Interval>): CloseableIterator<SAMRecord> {
            return SamRecordIntervalIteratorFactory().makeSamRecordIntervalIterator(reader, intervals, true)
        }

        fun analyzeLoci(reader: SamReader, interval: Interval, analyze: (SAMRecord) -> Boolean) {
            analyzeLoci(reader, listOf(interval), analyze)
        }

        fun analyzeLoci(reader: SamReader, intervals: List<Interval>, analyze: (SAMRecord) -> Boolean) {
            val iterator = samIterator(reader, intervals)
            var okay = true
            while (iterator.hasNext() && okay) {
                okay = analyze(iterator.next())
            }
            iterator.close()
        }

        fun analyzeLoci(reader: SamReader, analyze: (SAMRecord) -> Boolean) {
            val iterator = reader.iterator()
            var okay = true
            while (iterator.hasNext() && okay) {
                okay = analyze(iterator.next())
            }
            iterator.close()
        }

    }
}


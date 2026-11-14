package JavaPkg;

import BamUtils.Bam;
import Cmn2.Cfg;
import htsjdk.samtools.metrics.VersionHeader;

public class JavaMain {
    public static void main(String[] args) {
        var opts = Cmn2.Cfg.fromArray(args);
        System.out.println(opts.getFlags());
        if(opts.has("info")){
            System.out.println(Cfg.version);
            System.out.println(opts.key("info"));
            System.out.println(JavaMain.htsjdk_version());
        }
    }

    public static String htsjdk_version() {
        VersionHeader header = new VersionHeader();
        header.setVersionString("__htsjdkAccessible__");
        header.setVersionedItem("!");
        return header.toString();
    }
}

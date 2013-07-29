package nl.esciencecenter.eAstroViz;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import nl.esciencecenter.eAstroViz.dataFormats.beamFormedData.BeamFormedData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConvertBeamFormed {
    private static final Logger logger = LoggerFactory.getLogger(ConvertBeamFormed.class);
    static final int MAX_TIME = 1000;

    private final String fileName;
    private final String outputFileName;

    private float[][][] data; // [time][subband][channel]
    private int nrSubbands;
    private int nrChannels;
    private int nrTimes;
    private int nrSamplesPerSecond;

    public ConvertBeamFormed(final String fileName, final String outputFileName) {
        this.fileName = fileName;
        this.outputFileName = outputFileName;
    }

    void read() {
        final BeamFormedData bfd = new BeamFormedData(fileName, Integer.MAX_VALUE, Integer.MAX_VALUE, 1024 /* zoom factor */);
        bfd.read();
        data = bfd.getData();
        nrSubbands = bfd.getNrSubbands();
        nrChannels = bfd.getNrChannels();
        nrTimes = bfd.getTotalTime();
        nrSamplesPerSecond = bfd.getNrSamplesPerSecond();
    }

    void write() throws IOException {
        final FileOutputStream out = new FileOutputStream(outputFileName);
        final BufferedOutputStream buf = new BufferedOutputStream(out);
        final DataOutputStream dataOut = new DataOutputStream(buf);

        if (MAX_TIME > 0) {
            nrTimes = MAX_TIME;
        }

        dataOut.writeInt(nrTimes);
        dataOut.writeInt(nrSubbands);
        dataOut.writeInt(nrChannels);
        dataOut.writeInt(nrSamplesPerSecond);

        logger.info("Writing output, nrTimes  = " + nrTimes + ", nrSubbands = " + nrSubbands + ", nrChannels = " + nrChannels);

        for (int time = 0; time < nrTimes; time++) {
            for (int sb = 0; sb < nrSubbands; sb++) {
                for (int ch = 0; ch < nrChannels; ch++) {
                    dataOut.writeFloat(data[time][sb][ch]);
                }
            }
        }

        dataOut.close();
    }

    public static void main(final String[] args) throws IOException {
        final ConvertBeamFormed cm = new ConvertBeamFormed(args[0], args[1]);
        cm.read();
        cm.write();
    }
}

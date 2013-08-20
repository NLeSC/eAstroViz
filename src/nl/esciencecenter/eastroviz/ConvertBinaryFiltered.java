package nl.esciencecenter.eastroviz;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import nl.esciencecenter.eastroviz.dataformats.DataProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConvertBinaryFiltered {
    private static final Logger logger = LoggerFactory.getLogger(ConvertBinaryFiltered.class);

    private final String fileName;
    private final String outputFileName;
    private final int maxNrTimes;

    private float[][][][][] data; // [station][time][subband][channel][pol]
    private int nrStations;
    private int nrSubbands;
    private int nrChannels;
    private int nrPolarizations;
    private int nrTimes;
    private String extension;

    public ConvertBinaryFiltered(final String fileName, final String outputFileName, final String extension, int maxNrTimes) {
        this.fileName = fileName;
        this.outputFileName = outputFileName;
        this.extension = extension;
        this.maxNrTimes = maxNrTimes;
    }

    public void write() throws IOException {
        final FileOutputStream out = new FileOutputStream(outputFileName);
        final BufferedOutputStream buf = new BufferedOutputStream(out);
        final DataOutputStream dataOut = new DataOutputStream(buf);

        dataOut.writeInt(nrStations);
        dataOut.writeInt(nrTimes);
        dataOut.writeInt(nrSubbands);
        dataOut.writeInt(nrChannels);
        dataOut.writeInt(nrPolarizations);

        for (int station = 0; station < nrStations; station++) {
            for (int time = 0; time < nrTimes; time++) {
                for (int sb = 0; sb < nrSubbands; sb++) {
                    for (int ch = 0; ch < nrChannels; ch++) {
                        for (int pol = 0; pol < nrPolarizations; pol++) {
                            dataOut.writeFloat(data[station][time][sb][ch][pol]);
                        }
                    }
                }
            }
        }

        dataOut.close();
    }

    public void read() {
        final File[] ls = new File(fileName).listFiles(new Viz.ExtFilter(extension));

        try {
            FileInputStream fin = new FileInputStream(ls[0]);
            BufferedInputStream bin = new BufferedInputStream(fin);
            DataInputStream in = new DataInputStream(bin);

            nrStations = (int) readuint32(in, false);
            nrSubbands = (int) readuint32(in, false);
            nrChannels = (int) readuint32(in, false);
            nrPolarizations = (int) readuint32(in, false);
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        logger.info("nrStations = " + nrStations + ", nrSubbands = " + nrSubbands + ", nrChannels = " + nrChannels);

        if (maxNrTimes > 0) {
            nrTimes = maxNrTimes;
        } else {
            long totalSize = 0;
            for (File element : ls) {
                totalSize += element.length();
            }
            logger.info("totalSize = " + totalSize);
            totalSize -= ls.length * 4 * 4;
            logger.info("totalSize - headers = " + totalSize);

            nrTimes =
                    (int) (totalSize / (nrSubbands * nrStations * (3 + nrChannels * nrPolarizations) * DataProvider.SIZE_OF_FLOAT));
            int rem =
                    (int) (totalSize % (nrSubbands * nrStations * (3 + nrChannels * nrPolarizations) * DataProvider.SIZE_OF_FLOAT));

            if (rem != 0) {
                logger.info("internal error, size wrong, leftover = " + rem);
            }
        }

        logger.info("nrTimes = " + nrTimes);
        data = new float[nrStations][nrTimes][nrSubbands][nrChannels][nrPolarizations];

        for (File element : ls) {
            try {
                readFile(element);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public void readFile(File f) throws FileNotFoundException {
        FileInputStream fin = new FileInputStream(f);
        BufferedInputStream bin = new BufferedInputStream(fin);
        DataInputStream in = new DataInputStream(bin);

        try {
            readuint32(in, false);
            readuint32(in, false);
            readuint32(in, false);
            readuint32(in, false);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        logger.info("Reading file: " + f + ", nrBlocks = " + nrTimes);

        while (true) {
            try {
                int block = (int) readuint32(in, false);
                if (block >= nrTimes) {
                    break;
                }
                int station = (int) readuint32(in, false);
                int subband = (int) readuint32(in, false);
                //                logger.debug("read block " + block + ", station = " + station + ", subband " + subband);
                for (int ch = 0; ch < nrChannels; ch++) {
                    for (int pol = 0; pol < nrPolarizations; pol++) {
                        float sample = readFloat(in);
                        data[station][block][subband][ch][pol] = sample;
                    }
                }
            } catch (EOFException e1) {
                // ignore
                break;
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }

        try {
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private long readuint32(final DataInputStream in, final boolean bigEndian) throws IOException {
        final int ch0 = in.read();
        final int ch1 = in.read();
        final int ch2 = in.read();
        final int ch3 = in.read();
        if ((ch0 | ch1 | ch2 | ch3) < 0) {
            throw new EOFException();
        }

        if (bigEndian) {
            return (ch0 << 24) + (ch1 << 16) + (ch2 << 8) + (ch3 << 0);
        } else {
            return (ch0 << 0) + (ch1 << 8) + (ch2 << 16) + (ch3 << 24);
        }
    }

    private float readFloat(final DataInputStream in) throws IOException {
        final int ch0 = in.read();
        final int ch1 = in.read();
        final int ch2 = in.read();
        final int ch3 = in.read();
        if ((ch0 | ch1 | ch2 | ch3) < 0) {
            throw new EOFException();
        }
        final float val = Float.intBitsToFloat((ch3 << 24) + (ch2 << 16) + (ch1 << 8) + (ch0 << 0));

        return val;
    }

    public static void main(final String[] args) throws IOException {
        final ConvertBinaryFiltered cm = new ConvertBinaryFiltered(args[0], args[1], args[2], Integer.parseInt(args[3]));
        cm.read();
        cm.write();
    }

    public String getFileName() {
        return fileName;
    }

    public String getOutputFileName() {
        return outputFileName;
    }

    public int getMaxNrTimes() {
        return maxNrTimes;
    }

    public float[][][][][] getData() {
        return data;
    }

    public int getNrStations() {
        return nrStations;
    }

    public int getNrSubbands() {
        return nrSubbands;
    }

    public int getNrChannels() {
        return nrChannels;
    }

    public int getNrPolarizations() {
        return nrPolarizations;
    }

    public int getNrTimes() {
        return nrTimes;
    }
}

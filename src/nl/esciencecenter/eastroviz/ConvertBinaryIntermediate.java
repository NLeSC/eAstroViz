/*
 * Copyright 2013 Netherlands eScience Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConvertBinaryIntermediate {
    private static final Logger logger = LoggerFactory.getLogger(ConvertBinaryIntermediate.class);
    private final String fileName;
    private final String outputFileName;
    private final String extension;
    private final int maxNrTimes;

    private float[][][][][] data; // [station][time][subband][channel][pol]
    private int nrStations;
    private int nrSubbands;
    private int nrChannels;
    private int nrTimes;
    private int nrPolarizations;

    public ConvertBinaryIntermediate(final String fileName, final String outputFileName, final String extension, int maxNrTimes) {
        this.fileName = fileName;
        this.outputFileName = outputFileName;
        this.extension = extension;
        this.maxNrTimes = maxNrTimes;
    }

    void write() throws IOException {
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

    void read() {
        final File[] ls = new File(fileName).listFiles(new Viz.ExtFilter(extension));

        FileInputStream fin;
        try {
            fin = new FileInputStream(ls[0]);
            BufferedInputStream bin = new BufferedInputStream(fin);
            DataInputStream in = new DataInputStream(bin);

            nrStations = (int) readuint32(in, false);
            nrSubbands = (int) readuint32(in, false);
            nrChannels = (int) readuint32(in, false);
            nrPolarizations = (int) readuint32(in, false);
            in.close();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        logger.info("nrStations = " + nrStations + ", nrSubbands = " + nrSubbands + ", nrChannels = " + nrChannels
                + ", nrPols = " + nrPolarizations);

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

            nrTimes = (int) (totalSize / (((5 + nrChannels) * nrSubbands * nrPolarizations * nrStations) * 4));
            int rem = (int) (totalSize % (((5 + nrChannels) * nrSubbands * nrPolarizations * nrStations) * 4));
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

    void readFile(File f) throws FileNotFoundException {
        FileInputStream fin = new FileInputStream(f);
        BufferedInputStream bin = new BufferedInputStream(fin);
        DataInputStream in = new DataInputStream(bin);

        // skip header
        try {
            readuint32(in, false);
            readuint32(in, false);
            readuint32(in, false);
            readuint32(in, false);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        logger.info("Reading file: " + f + ", nrTimes = " + nrTimes);

        while (true) {
            try {
                int time = (int) readuint32(in, false);
                int station = (int) readuint32(in, false);
                int pol = (int) readuint32(in, false);
                int subband = (int) readuint32(in, false);

                for (int ch = 0; ch < nrChannels; ch++) {
                    float sample = readFloat(in);
                    data[station][time][subband][ch][pol] = sample;
                }
            } catch (EOFException e1) {
                // ignore
                break;
            } catch (IOException e) {
                logger.info("" + e);
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
        final ConvertBinaryIntermediate cm = new ConvertBinaryIntermediate(args[0], args[1], args[2], Integer.parseInt(args[3]));
        cm.read();
        logger.info("Read done");
        cm.write();
    }
}

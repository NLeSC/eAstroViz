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
package nl.esciencecenter.eastroviz.dataformats.visibility;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import nl.esciencecenter.eastroviz.Viz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public final class MSReader {
    private static boolean NEW_FORMAT = true;
    private static boolean VERBOSE = true;

    private static final Logger LOGGER = LoggerFactory.getLogger(MSReader.class);

    private final String filename;
    private final int nrSubbands;
    private long sequenceNr;
    private DataInputStream in;
    private int subband;
    private MSMetaData metaData;
    private float[][][][] visData; // [nrBaselines][nrChannels][NR_CROSS_POLARIZATIONS][real/imag]
    private int[][] nrValidSamples; // [nrBaselines][nrChannels]

    private long maxFileSize;
    private long maxSecondsOfData;
    private int sizePerSecond;

    public static MSMetaData getMetaData(final String fileName) {
        MSReader r = null;
        try {
            r = new MSReader(fileName);
            r.openSubband(0);
            final MSMetaData m = r.getMetaData();
            r.close();
            return m;
        } catch (final IOException e) {
            return null;
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (final IOException e) {
                    // Ignore.
                }
            }
        }
    }

    public MSReader(final String filename) throws IOException {
        this.filename = filename;

        int tmp = 0;
        final File f = new File(filename);
        final File[] list = f.listFiles();
        for (final File element : list) {
            if (element.getName().startsWith("SB") && element.getName().endsWith(".MS")) {
                tmp++;

                final File dataFile = new File(element.getCanonicalPath() + File.separator + "table.f0data");
                final long l = dataFile.length();
                if (l > maxFileSize) {
                    maxFileSize = l;
                }
            }
        }
        nrSubbands = tmp;
    }

    public void openSubband(final int subband) throws IOException {
        this.subband = subband;

        String subbandString;
        if (subband < 10) {
            subbandString = "00" + subband;
        } else if (subband < 100) {
            subbandString = "0" + subband;
        } else {
            subbandString = "" + subband;
        }

        final String dirName = filename + File.separator + "SB" + subbandString + ".MS";

        LOGGER.debug("reading subband dir: " + dirName);

        readMeta(dirName);

        final File f = new File(dirName + File.separator + "table.f0data");

        if (VERBOSE) {
            LOGGER.debug("reading file: " + f.getCanonicalPath());
        }
        final FileInputStream fin = new FileInputStream(f);
        final BufferedInputStream bin = new BufferedInputStream(fin, 10*1024*1024);
        in = new DataInputStream(bin);
    }

    public void close() throws IOException {
        in.close();
    }

    /**
     * Just read only one baseline from the file, skip all other data.
     * 
     * @param baseline
     */
    public void readSecond(final int requiredBaseline) {

        // 32 bit sequence number
        try {
            readSequenceNr();
            if (sequenceNr < 0) {
                return;
            }

            readVisibilities(requiredBaseline);
            readFlags(requiredBaseline);
        } catch (final IOException e) {
            sequenceNr = -1;
            return;
        }
    }

    private void readFlags(final int requiredBaseline) throws IOException {
        for (int channel = 0; channel < metaData.getNrChannels(); channel++) {
            int nr = 0;

            if (metaData.getNrBytesPerValidSamples() == 1) {
                nr = readuint8(in);
            } else if (metaData.getNrBytesPerValidSamples() == 2) {
                nr = readuint16(in);
            } else {
                throw new RuntimeException("unsupported nr bytes per nrValidsamples: " + metaData.getNrBytesPerValidSamples());
            }
            nrValidSamples[requiredBaseline][channel] = nr;
        }
        int baselineFlagDataToSkip =
                (metaData.getNrBaselines() - requiredBaseline - 1) * metaData.getNrChannels()
                        * metaData.getNrBytesPerValidSamples();

        int alignmentToSkip =
                metaData.getAlignment()
                        - ((metaData.getNrBaselines() * metaData.getNrChannels() * metaData.getNrBytesPerValidSamples()) % metaData
                                .getAlignment());
        if (alignmentToSkip == metaData.getAlignment()) {
            alignmentToSkip = 0;
        }

        skip(in, baselineFlagDataToSkip + alignmentToSkip);
    }

    private void readVisibilities(final int requiredBaseline) throws IOException {
        skip(in, requiredBaseline * metaData.getNrChannels() * metaData.getNrCrossPolarizations() * 2 * 4);
        for (int channel = 0; channel < metaData.getNrChannels(); channel++) {
            for (int pol = 0; pol < metaData.getNrCrossPolarizations(); pol++) {
                visData[requiredBaseline][channel][pol][Viz.REAL] = readFloat(in);
                visData[requiredBaseline][channel][pol][Viz.IMAG] = readFloat(in);
            }
        }
        int baselineDataToSkip =
                (metaData.getNrBaselines() - requiredBaseline - 1) * metaData.getNrChannels()
                        * metaData.getNrCrossPolarizations() * 2 * 4;

        int processed = metaData.getNrBaselines() * metaData.getNrChannels() * metaData.getNrCrossPolarizations() * 2 * 4;
        int alignmentToSkip = metaData.getAlignment() - (processed % metaData.getAlignment());
        if (alignmentToSkip == metaData.getAlignment()) {
            alignmentToSkip = 0;
        }

        int baselineFlagDataToSkip = requiredBaseline * metaData.getNrChannels() * metaData.getNrBytesPerValidSamples();

        skip(in, baselineDataToSkip + alignmentToSkip + baselineFlagDataToSkip);
    }

    private void readSequenceNr() throws IOException {
        if (NEW_FORMAT) {
            final long magic = readuint32(in, false);
            if (magic != 0x0000DA7A) {
                LOGGER.info("data corrupted, magic is wrong! val = " + magic);
                sequenceNr = -1;
                throw new RuntimeException("data corrupted, magic is wrong!");
            }

            sequenceNr = readuint32(in, false);
            skip(in, metaData.getAlignment() - 8);
        } else {
            sequenceNr = readuint32(in, true);
            skip(in, metaData.getAlignment() - 4);
        }
    }

    private void readMeta(final String dirName) throws IOException {
        final File m = new File(dirName + File.separator + "table.f0meta");
        if (VERBOSE) {
            LOGGER.debug("reading META file: " + m.getCanonicalPath());
        }

        final FileInputStream fin = new FileInputStream(m);
        final BufferedInputStream bin = new BufferedInputStream(fin, 512);
        final DataInputStream din = new DataInputStream(bin);

        // skip header
        for (int i = 0; i < 2; i++) {
            final long l = readuint32(din, true);
            if (l != 0xbebebebe) {
                throw new IOException("data corrupted! magic value not correct");
            }
        }

        verifyString(din, "LofarStMan");

        final int version = (int) readuint32(din, true);
        if (VERBOSE) {
            LOGGER.debug("Lofar storage manager version: " + version);
        }
        // Now, two blocks, indicating the antenna orders.
        skip(din, 4);
        final int[][] stations1 = readIntBlock(din);

        skip(din, 4);
        final int[][] stations2 = readIntBlock(din);

        skip(din, 8);
        final double IONIntegrationTime = din.readDouble();
        if (VERBOSE) {
            LOGGER.debug("ION integration time = " + IONIntegrationTime);
        }

        final int nrChannels = (int) readuint32(din, true);
        if (VERBOSE) {
            LOGGER.debug("channels per subband: " + nrChannels);
        }

        final int nrCrossPolarizations = (int) readuint32(din, true);
        if (VERBOSE) {
            LOGGER.debug("nr cross polarizations : " + nrCrossPolarizations);
        }

        final int integrationTimeProd = (int) din.readDouble();
        if (VERBOSE) {
            LOGGER.debug("CN * ION integration time = " + integrationTimeProd);
        }

        final int alignment = (int) readuint32(din, true);
        if (VERBOSE) {
            LOGGER.debug("alignment : " + alignment);
        }

        final boolean isBigEndian = din.readByte() == 0 ? true : false;
        if (VERBOSE) {
            LOGGER.debug("big endian = " + isBigEndian);
        }

        final int nrBytesPerValidSamples = (int) readuint32(din, true);
        if (VERBOSE) {
            LOGGER.debug("nrBytesPerValidSamples : " + nrBytesPerValidSamples);
        }

        din.close();

        metaData =
                new MSMetaData(version, stations1, stations2, IONIntegrationTime, nrChannels, nrCrossPolarizations,
                        integrationTimeProd, alignment, isBigEndian, nrBytesPerValidSamples);

        visData = new float[metaData.getNrBaselines()][nrChannels][nrCrossPolarizations][2];
        nrValidSamples = new int[metaData.getNrBaselines()][nrChannels];

        sizePerSecond = metaData.getAlignment(); // header
        sizePerSecond += metaData.getNrBaselines() * metaData.getNrChannels() * metaData.getNrCrossPolarizations() * 2 * 4; // samples
        sizePerSecond += metaData.getNrBaselines() * metaData.getNrChannels() * metaData.getNrBytesPerValidSamples(); // flags

        maxSecondsOfData = (long) Math.ceil((double) maxFileSize / sizePerSecond);

        if (VERBOSE) {
            LOGGER.debug("maximum file size = " + maxFileSize + ", maximum number of seconds of data = " + maxSecondsOfData);
        }
    }

    private int[][] readIntBlock(final DataInputStream din) throws IOException {
        verifyString(din, "Block");
        final int dimX = (int) readuint32(din, true);
        final int dimY = (int) readuint32(din, true);

        final int[][] res = new int[dimY][dimX];

        for (int y = 0; y < dimY; y++) {
            for (int x = 0; x < dimX; x++) {
                final long station = (int) readuint32(din, true);
                res[y][x] = (int) station;
            }
        }

        return res;
    }

    private void verifyString(final DataInputStream din, final String expected) throws IOException {
        final long l = readuint32(din, true);
        if (l != expected.length()) {
            throw new IOException("data corrupted! Expected string length " + expected.length() + ", but got: " + l);
        }

        final String s = readByteString(din, expected.length());
        if (!s.equals(expected)) {
            throw new IOException("data corrupted! Expected " + expected + ", but got: " + s);
        }
    }

    private String readByteString(final DataInputStream in, final int len) throws IOException {
        StringBuffer res = new StringBuffer();
        for (int i = 0; i < len; i++) {
            final byte b = in.readByte();
            res.append((char) b);
        }

        return res.toString();
    }

    void skip(final DataInputStream in, final int bytes) throws IOException {
        long skipped = 0;
        while (skipped != bytes) {
            skipped += in.skip(bytes - skipped);
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

    private int readuint16(final DataInputStream in) throws IOException {
        final int ch0 = in.read();
        final int ch1 = in.read();
        if ((ch0 | ch1) < 0) {
            throw new EOFException();
        }

        final int res = (ch1 << 8) + (ch0 << 0);

        return res;
    }

    private int readuint8(final DataInputStream in) throws IOException {
        final int ch0 = in.read();
        if ((ch0) < 0) {
            throw new EOFException();
        }

        return ch0;
    }

    public float[][][] getVisibilities(final int baseline) {
        return visData[baseline];
    }

    public int[][] getNrValidSamples() {
        return nrValidSamples;
    }

    public int[] getNrValidSamples(final int baseline) {
        return nrValidSamples[baseline];
    }

    public long getSequenceNr() {
        return sequenceNr;
    }

    public int getNrSubbands() {
        return nrSubbands;
    }

    public MSMetaData getMetaData() {
        return metaData;
    }

    public int getMaxNrSecondsOfData() {
        return (int) maxSecondsOfData;
    }
}

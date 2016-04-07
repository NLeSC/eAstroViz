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
package nl.esciencecenter.eastroviz.dataformats.preprocessed;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;

import nl.esciencecenter.eastroviz.dataformats.DataProvider;
import nl.esciencecenter.eastroviz.dataformats.MinMaxVals;
import nl.esciencecenter.eastroviz.flaggers.Flagger;
import nl.esciencecenter.eastroviz.flaggers.IntermediateFlagger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A data container for pre-processed data. Note that this class holds the data for a time series, for all frequencies and
 * polarizations, but for one station only. Otherwise, the data would be too large.
 * 
 * @author rob
 */
public abstract class PreprocessedData extends DataProvider {

    private static final boolean SHOW_SMOOTH = false;
    private static final boolean SHOW_SMOOTH_DIFF = false;

    private static final Logger logger = LoggerFactory.getLogger(PreprocessedData.class);

    private float[][][][] data; // [time][nrSubbands][nrPolarizations][nrChannels]
    private boolean[][][] initialFlagged; // [time][nrSubbands][nrChannels]
    private boolean[][][] flagged; // [time][nrSubbands][nrChannels]
    private int nrStations;
    private int nrSubbands;
    private int nrChannels;
    private int nrTimes;
    private int nrPolarizations;
    private int integrationFactor;
    private MinMaxVals minMaxVals;
    private static final boolean SCALE_PER_SUBBAND = false;
    private float min;
    private float scaleValue;
    private int station1;
    private int pol;

    public PreprocessedData(final String fileName, final int integrationFactor, final int maxSequenceNr, final int maxSubbands,
            final String[] polList, final int station, final int pol) {
        super();
        init(fileName, maxSequenceNr, maxSubbands, polList, new String[] { "none", "Intermediate" });
        this.integrationFactor = integrationFactor;
        this.station1 = station;
        this.pol = pol;
    }

    @SuppressWarnings("unused")
    public void read() throws IOException {
        final FileInputStream fin = new FileInputStream(getFileName());
        final DataInputStream din = new DataInputStream(fin);

        nrStations = din.readInt();
        nrTimes = din.readInt() / integrationFactor;
        final int nrSubbandsInFile = din.readInt();
        nrChannels = din.readInt();
        nrPolarizations = din.readInt();

        nrSubbands = nrSubbandsInFile;
        if (getMaxSubbands() < nrSubbandsInFile) {
            nrSubbands = getMaxSubbands();
        }

        logger.info("nrTimes = " + (nrTimes * integrationFactor) + ", with integration, time = " + nrTimes + ", nrSubbands = "
                + nrSubbandsInFile + ", nrChannels = " + nrChannels);

        if (getMaxSequenceNr() < nrTimes) {
            nrTimes = getMaxSequenceNr();
        }

        data = new float[nrTimes][nrSubbands][nrPolarizations][nrChannels];
        flagged = new boolean[nrTimes][nrSubbands][nrChannels];
        initialFlagged = new boolean[nrTimes][nrSubbands][nrChannels];

        int stationBlockSize = integrationFactor * nrSubbandsInFile * nrChannels * nrPolarizations * DataProvider.SIZE_OF_FLOAT;

        din.skip(nrTimes * stationBlockSize * station1);

        final ByteBuffer bb = ByteBuffer.allocateDirect(stationBlockSize);
        bb.order(ByteOrder.BIG_ENDIAN);
        final FloatBuffer fb = bb.asFloatBuffer();
        final FileChannel channel = fin.getChannel();

        final long start = System.currentTimeMillis();

        for (int second = 0; second < nrTimes; second++) {
            if (second > getMaxSequenceNr()) {
                break;
            }

            bb.rewind();
            fb.rewind();
            final int res = channel.read(bb);
            if (res < 0) {
                break;
            }
            if (res != bb.capacity()) {
                logger.warn("read less bytes! Expected " + bb.capacity() + ", got " + res + "; continuing...");
            }

            for (int time = 0; time < integrationFactor; time++) {
                for (int sb = 0; sb < nrSubbandsInFile; sb++) {
                    for (int ch = 0; ch < nrChannels; ch++) {
                        for (int pol = 0; pol < nrPolarizations; pol++) {
                            float sample = fb.get();
                            if (sb < getMaxSubbands()) {
                                if (sample < 0.0f) {
                                    initialFlagged[second][sb][ch] = true;
                                    flagged[second][sb][ch] = true;
                                } else {
                                    data[second][sb][pol][ch] += sample;
                                }
                            }
                        }
                    }
                }
            }
        }

        final long end = System.currentTimeMillis();
        final double iotime = (end - start) / 1000.0;
        final double mbs = (integrationFactor * nrTimes * nrSubbandsInFile * nrChannels * 4.0) / (1024.0 * 1024.0);
        final double speed = mbs / iotime;
        logger.info("read " + mbs + "MB in " + iotime + " s, speed = " + speed + " MB/s.");

        fin.close();
        din.close();

        if (SHOW_SMOOTH || SHOW_SMOOTH_DIFF) {
            calcSmoothedIntermediate();
        }

        calcMinMax();
    }

    private void calcSmoothedIntermediate() {
        for (int time = 0; time < nrTimes; time++) {
            for (int sb = 0; sb < nrSubbands; sb++) {
                for (int pol = 0; pol < nrPolarizations; pol++) {
                    float[] tmp = new float[nrChannels];
                    for (int ch = 0; ch < nrChannels; ch++) {
                        tmp[ch] = data[time][sb][pol][ch];
                    }
                    float[] tmp2 = Flagger.oneDimensionalGausConvolution(tmp, 10.0f);
                    for (int ch = 0; ch < nrChannels; ch++) {
                        if (SHOW_SMOOTH) {
                            data[time][sb][pol][ch] = tmp2[ch];
                        } else if (SHOW_SMOOTH_DIFF) {
                            data[time][sb][pol][ch] = Math.abs(tmp2[ch] - data[time][sb][ch][pol]);
                        }
                    }
                }
            }
        }
    }

    /**
     * calc min and max for scaling set flagged samples to 0.
     */
    private void calcMinMax() {
        long initialFlaggedCount = 0;
        minMaxVals = new MinMaxVals(nrSubbands);
        for (int time = 0; time < nrTimes; time++) {
            for (int sb = 0; sb < nrSubbands; sb++) {
                for (int ch = 0; ch < nrChannels; ch++) {
                    for (int pol = 0; pol < nrPolarizations; pol++) {
                        if (initialFlagged[time][sb][ch]) {
                            data[time][sb][pol][ch] = 0.0f;
                            initialFlaggedCount++;
                        } else {
                            final float sample = data[time][sb][pol][ch];
                            minMaxVals.processValue(sample, sb);
                        }
                    }
                }
            }
        }
        min = minMaxVals.getMin();
        scaleValue = minMaxVals.getMax() - min;

        long nrSamples = nrTimes * nrSubbands * nrChannels * nrPolarizations;
        float percent = ((float) initialFlaggedCount / nrSamples) * 100.0f;

        logger.info("samples already flagged in data set: " + initialFlaggedCount + "(" + percent + "%)");
    }

    @Override
    public void flag() {
        for (int time = 0; time < nrTimes; time++) {
            for (int sb = 0; sb < nrSubbands; sb++) {
                for (int ch = 0; ch < nrChannels; ch++) {
                    flagged[time][sb][ch] = initialFlagged[time][sb][ch];
                }
            }
        }

        if (getFlaggerType().equals("none")) {
            return;
        }

        if (nrChannels > 1) {
            final IntermediateFlagger[] flaggers = new IntermediateFlagger[nrSubbands];
            for (int i = 0; i < nrSubbands; i++) {
                flaggers[i] = new IntermediateFlagger(getFlaggerSensitivity(), getFlaggerSIRValue());
            }

            for (int time = 0; time < nrTimes; time++) {
                for (int sb = 0; sb < nrSubbands; sb++) {
                    flaggers[sb].flag(data[time][sb], flagged[time][sb]);
                }
            }
        } else {
            final IntermediateFlagger flagger = new IntermediateFlagger(getFlaggerSensitivity(), getFlaggerSIRValue());
            for (int time = 0; time < nrTimes; time++) {
                final boolean[] tmpFlags = new boolean[nrSubbands];
                final float[][] tmp = new float[nrPolarizations][nrSubbands];

                for (int pol = 0; pol < nrPolarizations; pol++) {
                    for (int sb = 0; sb < nrSubbands; sb++) {
                        tmp[pol][sb] = data[time][sb][pol][0];
                    }
                }
                flagger.flag(tmp, tmpFlags);
                for (int sb = 0; sb < nrSubbands; sb++) {
                    flagged[time][sb][0] = tmpFlags[sb];
                }
            }
        }
    }

    public final int getTotalTime() {
        return nrTimes;
    }

    public final int getTotalFreq() {
        return nrSubbands * nrChannels;
    }

    @Override
    public final int getNrChannels() {
        return nrChannels;
    }

    @Override
    public final int getNrSubbands() {
        return nrSubbands;
    }

    @Override
    public final int getSizeX() {
        return nrTimes;
    }

    @Override
    public final int getSizeY() {
        return nrSubbands * nrChannels;
    }

    @Override
    public final float getRawValue(final int x, final int y) {
        final int subband = y / nrChannels;
        final int channel = y % nrChannels;

        return data[x][subband][pol][channel];
    }

    @Override
    public final float getValue(final int x, final int y) {
        final int subband = y / nrChannels;
        final int channel = y % nrChannels;
        final float sample = data[x][subband][pol][channel];

        if (SCALE_PER_SUBBAND) {
            return (sample - minMaxVals.getMin(subband)) / (minMaxVals.getMax(subband) - minMaxVals.getMin(subband));
        } else {
            return (sample - min) / scaleValue;
        }
    }

    @Override
    public final boolean isFlagged(final int x, final int y) {
        final int subband = y / nrChannels;
        final int channel = y % nrChannels;
        return flagged[x][subband][channel];
    }

    public int getNrStations() {
        return nrStations;
    }

    @Override
    public int getStation1() {
        return station1;
    }

    @Override
    public int setStation1(int station1) {
        if (station1 < 0 || station1 >= nrStations || station1 == this.station1) {
            logger.warn("could not set station to " + station1);
            return this.station1;
        }

        this.station1 = station1;
        try {
            read();
        } catch (IOException e) {
            logger.error("" + e);
            throw new RuntimeException(e);
        }
        return this.station1;
    }

    @Override
    public int getStation2() {
        return -1;
    }

    @Override
    public int setStation2(int station2) {
        return -1;
    }

    @Override
    public int getPolarization() {
        return pol;
    }

    @Override
    public int setPolarization(int newValue) {
        if (newValue < 0 || newValue >= getPolList().length) {
            return pol;
        }

        pol = newValue;

        // No need to re-read the data, we already loaded both polarizations.
        return pol;
    }

    public int getNrPolarizations() {
        return nrPolarizations;
    }

    public int getIntegrationFactor() {
        return integrationFactor;
    }
}

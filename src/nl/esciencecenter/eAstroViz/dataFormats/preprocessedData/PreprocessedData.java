package nl.esciencecenter.eAstroViz.dataFormats.preprocessedData;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;

import nl.esciencecenter.eAstroViz.dataFormats.DataProvider;
import nl.esciencecenter.eAstroViz.dataFormats.MinMaxVals;
import nl.esciencecenter.eAstroViz.flaggers.Flagger;
import nl.esciencecenter.eAstroViz.flaggers.IntermediateFlagger;

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

    protected float[][][][] data; // [time][nrSubbands][nrPolarizations][nrChannels]
    protected boolean[][][] initialFlagged; // [time][nrSubbands][nrChannels]
    protected boolean[][][] flagged; // [time][nrSubbands][nrChannels]
    protected int nrStations;
    protected int nrSubbands;
    protected int nrChannels;
    protected int nrTimes;
    protected int nrPolarizations;
    protected int integrationFactor;
    protected MinMaxVals minMaxVals;
    private static final boolean SCALE_PER_SUBBAND = false;
    private float min;
    private float scaleValue;
    private final int station;

    public PreprocessedData(final String fileName, final int integrationFactor, final int maxSequenceNr, final int maxSubbands, final String[] polList,
            final int station) {
        super(fileName, maxSequenceNr, maxSubbands, polList, new String[] { "none", "Intermediate" });
        this.integrationFactor = integrationFactor;
        this.station = station;
    }

    @SuppressWarnings("unused")
    public void read() throws IOException {
        final FileInputStream fin = new FileInputStream(fileName);
        final DataInputStream din = new DataInputStream(fin);

        nrStations = din.readInt();
        nrTimes = din.readInt() / integrationFactor;
        final int nrSubbandsInFile = din.readInt();
        nrChannels = din.readInt();
        nrPolarizations = din.readInt();

        nrSubbands = nrSubbandsInFile;
        if (maxSubbands < nrSubbandsInFile) {
            nrSubbands = maxSubbands;
        }

        logger.info("nrTimes = " + (nrTimes * integrationFactor) + ", with integration, time = " + nrTimes + ", nrSubbands = " + nrSubbandsInFile
                + ", nrChannels = " + nrChannels);

        if (maxSequenceNr < nrTimes) {
            nrTimes = maxSequenceNr;
        }

        data = new float[nrTimes][nrSubbands][nrPolarizations][nrChannels];
        flagged = new boolean[nrTimes][nrSubbands][nrChannels];
        initialFlagged = new boolean[nrTimes][nrSubbands][nrChannels];

        int stationBlockSize = integrationFactor * nrSubbandsInFile * nrChannels * nrPolarizations * 4;

        fin.skip(stationBlockSize * station);

        final ByteBuffer bb = ByteBuffer.allocateDirect(stationBlockSize);
        bb.order(ByteOrder.BIG_ENDIAN);
        final FloatBuffer fb = bb.asFloatBuffer();
        final FileChannel channel = fin.getChannel();

        final long start = System.currentTimeMillis();

        for (int second = 0; second < nrTimes; second++) {
            if (second > maxSequenceNr) {
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
                            if (sb < maxSubbands) {
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

        logger.info("sampled already flagged in data set: " + initialFlaggedCount);
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

        if (flaggerType.equals("none")) {
            return;
        }

        if (nrChannels > 1) {
            final IntermediateFlagger[] flaggers = new IntermediateFlagger[nrSubbands];
            for (int i = 0; i < nrSubbands; i++) {
                flaggers[i] = new IntermediateFlagger(flaggerSensitivity, flaggerSIRValue);
            }

            for (int time = 0; time < nrTimes; time++) {
                for (int sb = 0; sb < nrSubbands; sb++) {
                    flaggers[sb].flag(data[time][sb], flagged[time][sb]);
                }
            }
        } else {
            final IntermediateFlagger flagger = new IntermediateFlagger(flaggerSensitivity, flaggerSIRValue);
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

    public final int getNrChannels() {
        return nrChannels;
    }

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
    public final float getRawValue(final int x, final int y, int pol) {
        final int subband = y / nrChannels;
        final int channel = y % nrChannels;

        return data[x][subband][pol][channel];
    }

    @Override
    public final float getValue(final int x, final int y, int pol) {
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
}

package nl.esciencecenter.eastroviz.dataformats.preprocessed.compressedbeamformed;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;

import nl.esciencecenter.eastroviz.dataformats.DataProvider;
import nl.esciencecenter.eastroviz.dataformats.MinMaxVals;
import nl.esciencecenter.eastroviz.dataformats.beamformed.BeamFormedData;
import nl.esciencecenter.eastroviz.flaggers.BeamFormedFlagger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO make subclass of preprocessed data? seems almost identical 

public final class CompressedBeamFormedData extends DataProvider {
    private static final Logger logger = LoggerFactory.getLogger(CompressedBeamFormedData.class);

    private float[][][] data; // [time][nrSubbands][nrChannels]
    private boolean[][][] initialFlagged; // [time][nrSubbands][nrChannels]
    private boolean[][][] flagged; // [time][nrSubbands][nrChannels]
    private int nrSubbands;
    private int nrChannels;
    private int nrTimes;
    private int integrationFactor;
    private int nrSamplesPerSecond;
    private MinMaxVals minMaxVals;
    private static final boolean SCALE_PER_SUBBAND = false;
    private float min;
    private float scaleValue;
    private int stoke = 0;

    public CompressedBeamFormedData(final String fileName, final int integrationFactor, final int maxSequenceNr,
            final int maxSubbands) {
        // for now, we only have stokes I
        super();
        init(fileName, maxSequenceNr, maxSubbands, new String[] { "I" }, new String[] { "none", "Intermediate" });
        this.integrationFactor = integrationFactor;
    }

    public void read() throws IOException {
        final FileInputStream fin = new FileInputStream(getFileName());
        final DataInputStream din = new DataInputStream(fin);

        nrTimes = din.readInt() / integrationFactor;
        nrSubbands = din.readInt();
        nrChannels = din.readInt();
        nrSamplesPerSecond = din.readInt() * integrationFactor;

        logger.info("nrTimes = " + (nrTimes * integrationFactor) + ", with integration, time = " + nrTimes + ", nrSubbands = "
                + nrSubbands + ", nrChannels = " + nrChannels + ", nrSamplesPerSecond = " + nrSamplesPerSecond);

        if (getMaxSequenceNr() < nrTimes) {
            nrTimes = getMaxSequenceNr();
        }

        data = new float[nrTimes][nrSubbands][nrChannels];
        flagged = new boolean[nrTimes][nrSubbands][nrChannels];
        initialFlagged = new boolean[nrTimes][nrSubbands][nrChannels];

        final long start = System.currentTimeMillis();

        final ByteBuffer bb = ByteBuffer.allocateDirect(integrationFactor * nrSubbands * nrChannels * 4);
        bb.order(ByteOrder.BIG_ENDIAN);
        final FloatBuffer fb = bb.asFloatBuffer();
        final FileChannel channel = fin.getChannel();

        for (int second = 0; second < nrTimes; second++) {
            if (second > getMaxSequenceNr()) {
                break;
            }

            bb.rewind();
            fb.rewind();
            final int res = channel.read(bb); // TODO can return less bytes!
            if (res != bb.capacity()) {
                logger.warn("read less bytes! Expected " + bb.capacity() + ", got " + res);
            }
            if (res < 0) {
                break;
            }

            for (int time = 0; time < integrationFactor; time++) {
                for (int sb = 0; sb < nrSubbands; sb++) {
                    for (int ch = 0; ch < nrChannels; ch++) {
                        float sample = fb.get();
                        if (sample < 0.0f) {
                            initialFlagged[second][sb][ch] = true;
                            flagged[second][sb][ch] = true;
                        } else {
                            data[second][sb][ch] += sample;
                        }
                    }
                }
            }
        }

        final long end = System.currentTimeMillis();
        final double iotime = (end - start) / 1000.0;
        final double mbs = (integrationFactor * nrTimes * nrSubbands * nrChannels * 4.0) / (1024.0 * 1024.0);
        final double speed = mbs / iotime;
        logger.info("read " + mbs + "MB in " + iotime + " s, speed = " + speed + " MB/s.");

        fin.close();
        din.close();

        calculateStatistics();
    }

    private void calculateStatistics() {
        // calc min and max for scaling
        // set flagged samples to 0.
        minMaxVals = new MinMaxVals(nrSubbands);
        for (int time = 0; time < nrTimes; time++) {
            for (int sb = 0; sb < nrSubbands; sb++) {
                for (int ch = 0; ch < nrChannels; ch++) {
                    if (initialFlagged[time][sb][ch]) {
                        data[time][sb][ch] = 0.0f;
                    } else {
                        final float sample = data[time][sb][ch];
                        minMaxVals.processValue(sample, sb);
                    }
                }
            }
        }
        min = minMaxVals.getMin();
        scaleValue = minMaxVals.getMax() - min;
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
            final BeamFormedFlagger[] flaggers = new BeamFormedFlagger[nrSubbands];
            for (int i = 0; i < nrSubbands; i++) {
                flaggers[i] = new BeamFormedFlagger(getFlaggerSensitivity(), getFlaggerSIRValue());
            }

            for (int time = 0; time < nrTimes; time++) {
                for (int sb = 0; sb < nrSubbands; sb++) {
                    flaggers[sb].flag(data[time][sb], flagged[time][sb]);
                }
            }
        } else {
            final BeamFormedFlagger flagger = new BeamFormedFlagger(getFlaggerSensitivity(), getFlaggerSIRValue());
            for (int time = 0; time < nrTimes; time++) {
                final float[] tmp = new float[nrSubbands];
                final boolean[] tmpFlags = new boolean[nrSubbands];
                for (int sb = 0; sb < nrSubbands; sb++) {
                    tmp[sb] = data[time][sb][0];
                }

                flagger.flag(tmp, tmpFlags);
                for (int sb = 0; sb < nrSubbands; sb++) {
                    flagged[time][sb][0] = tmpFlags[sb];
                }
            }
        }
    }

    public final float[][][] getData() {
        return data;
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
    public final float getRawValue(final int x, final int y) {
        final int subband = y / nrChannels;
        final int channel = y % nrChannels;

        return data[x][subband][channel];
    }

    @Override
    public final float getValue(final int x, final int y) {
        final int subband = y / nrChannels;
        final int channel = y % nrChannels;
        final float sample = data[x][subband][channel];

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

    public void dedisperse(float nrSamplesPerSecond, float lowFreq, float freqStep, float dm) {
        BeamFormedData.dedisperse(getData(), flagged, nrSamplesPerSecond, lowFreq, freqStep, dm);
        calculateStatistics();
    }

    public float[] fold(float nrSamplesPerSecond, float period) {
        return BeamFormedData.fold(getData(), flagged, nrSamplesPerSecond, period);
    }

    @Override
    public int getStation1() {
        return -1;
    }

    @Override
    public int setStation1(int station1) {
        return -1;
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
        return stoke;
    }

    @Override
    public int setPolarization(int newValue) {
        // TODO Auto-generated method stub
        return stoke;
    }

    @Override
    public String polarizationToString(int pol) {
        // TODO Auto-generated method stub
        return "I";
    }

    @Override
    public int StringToPolarization(String polString) {
        // TODO Auto-generated method stub
        return 0;
    }
}

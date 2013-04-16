package lofar.dataFormats.preprocessedData.CompressedBeamFormedData;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;

import lofar.dataFormats.DataProvider;
import lofar.dataFormats.MinMaxVals;
import lofar.dataFormats.beamFormedData.BeamFormedData;
import lofar.flaggers.IntermediateFlagger;

public final class CompressedBeamFormedData extends DataProvider {

    protected float[][][] data; // [time][nrSubbands][nrChannels]
    protected boolean[][][] initialFlagged; // [time][nrSubbands][nrChannels]
    protected boolean[][][] flagged; // [time][nrSubbands][nrChannels]
    protected int nrSubbands;
    protected int nrChannels;
    protected int nrTimes;
    protected int integrationFactor;
    protected int nrSamplesPerSecond;
    protected MinMaxVals minMaxVals;
    private static final boolean SCALE_PER_SUBBAND = false;
    private float min;
    private float scaleValue;

    public CompressedBeamFormedData(final String fileName, final int integrationFactor, final int maxSequenceNr) {
        super(fileName, maxSequenceNr, new String[] { "none", "Intermediate" });
        this.integrationFactor = integrationFactor;
    }

    public void read() throws IOException {
        final FileInputStream fin = new FileInputStream(fileName);
        final DataInputStream din = new DataInputStream(fin);

        nrTimes = din.readInt() / integrationFactor;
        nrSubbands = din.readInt();
        nrChannels = din.readInt();
        nrSamplesPerSecond = din.readInt() * integrationFactor;
        
        System.err.println("nrTimes = " + (nrTimes * integrationFactor) + ", with integration, time = " + nrTimes + ", nrSubbands = " + nrSubbands
                + ", nrChannels = " + nrChannels + ", nrSamplesPerSecond = " + nrSamplesPerSecond);

        if (maxSequenceNr < nrTimes) {
            nrTimes = maxSequenceNr;
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
            if (second > maxSequenceNr) {
                break;
            }

            bb.rewind();
            fb.rewind();
            final int res = channel.read(bb); // TODO can return less bytes!
            if (res != bb.capacity()) {
                System.err.println("read less bytes! Expected " + bb.capacity() + ", got " + res);
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
        System.err.println("read " + mbs + "MB in " + iotime + " s, speed = " + speed + " MB/s.");

        fin.close();
        din.close();

        calculateStatistics();
    }

    private void calculateStatistics()  {
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

        if (flaggerType.equals("none")) {
            return;
        }

        if (nrChannels > 1) {
            final IntermediateFlagger[] flaggers = new IntermediateFlagger[nrSubbands];
            for (int i = 0; i < nrSubbands; i++) {
                flaggers[i] = new IntermediateFlagger(flaggerSensitivity);
            }

            for (int time = 0; time < nrTimes; time++) {
                for (int sb = 0; sb < nrSubbands; sb++) {
                        flaggers[sb].flag(data[time][sb], flagged[time][sb]);
                }
            }
        } else {
            final IntermediateFlagger flagger = new IntermediateFlagger(flaggerSensitivity);
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
}

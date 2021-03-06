package nl.esciencecenter.eastroviz;

import java.io.File;
import java.io.IOException;

import nl.esciencecenter.eastroviz.dataformats.DataProvider;
import nl.esciencecenter.eastroviz.dataformats.beamformed.BeamFormedDataReader;
import nl.esciencecenter.eastroviz.dataformats.beamformed.BeamFormedMetaData;
import nl.esciencecenter.eastroviz.dataformats.beamformed.BeamFormedSampleHandler;

/**
 * compute the pulse profile in full resolution from a data set.
 * 
 * @author rob
 * 
 */
public final class PulseProfile implements BeamFormedSampleHandler {
    private BeamFormedMetaData m;

    public static final int NR_BINS = 512;
    String fileName;
    int maxSequenceNr;
    int maxSubbands;

    // pulsar parameters.
    private final double dm = 12.455;
    private final double period = 1.3373021601895;
    private double[] shifts;

    private double[] bins = new double[NR_BINS];
    private long[] counts = new long[NR_BINS];

    private long totalSamples;
    private long flaggedSamples;

    public PulseProfile(String fileName, int maxSequenceNr, int maxSubbands) {
        this.fileName = fileName;
        this.maxSequenceNr = maxSequenceNr;
        this.maxSubbands = maxSubbands;
    }

    void start() {
        try {
			read();
		} catch (IOException e) {
			e.printStackTrace();
		}

        double totalPower = 0.0;

        for (int i = 0; i < NR_BINS; i++) {
            totalPower += bins[i];
        }

        for (int i = 0; i < NR_BINS; i++) {
            bins[i] /= counts[i];
        }

        for (int i = 0; i < NR_BINS; i++) {
            System.out.println(bins[i]);
        }

        DataProvider.scale(bins);
        double snr = Dedispersion.computeSNR(bins);
        double percent = ((double)flaggedSamples / totalSamples) * 100.0;

        System.err.println("total samples: " + totalSamples + ", flagged samples: " + flaggedSamples + " (" + percent + " %)" +
        ", total power: " + totalPower + ", SNR: " + snr);
    }

    void read() throws IOException {
        BeamFormedDataReader reader = new BeamFormedDataReader(fileName, maxSequenceNr, maxSubbands, 1);
        m = reader.readMetaData();
        double sampleRate = m.totalNrSamples / m.totalIntegrationTime;

        shifts = Dedispersion.computeShiftsInSeconds(m.nrSubbands, m.nrChannels, sampleRate, m.minFrequency, m.channelWidth, dm);
        reader.read(m, this);
    }

    public static void main(String[] args) {
        String fileName = null;
        int maxSeqNo = Integer.MAX_VALUE;
        int maxSubbands = Integer.MAX_VALUE;

        if (args.length < 1) {
            System.err.println("Usage: PulseProfile [-maxSeqNo] [-maxSubbands] <dataset directory or raw file>");
            System.exit(1);
        }

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-maxSeqNo")) {
                i++;
                maxSeqNo = Integer.parseInt(args[i]);
            } else if (args[i].equals("-maxSubbands")) {
                i++;
                maxSubbands = Integer.parseInt(args[i]);
            } else {
                // it must be the filename
                if (fileName != null) {
                    System.err.println("You cannot specify the file name twice. The first one was: " + fileName + ", the second one was: " + args[i]);
                    System.exit(1);
                }

                fileName = args[i];
            }
        }

        File f = new File(fileName);
        fileName = f.getPath();

        if (!fileName.endsWith("beamFormed")) {
            System.err.println("illegal directory name, it should end with \"beamFormed\"");
        }

        new PulseProfile(fileName, maxSeqNo, maxSubbands).start();
    }

    @Override
    public void handleSample(int second, int minorTime, int subband, int channel, float sample) {
        totalSamples++;

        if (sample <= 0.0f) {
            // flagged sample
            flaggedSamples++;
            return;
        }

        int freq = subband * m.nrChannels + channel;
        double sampleRate = m.totalNrSamples / m.totalIntegrationTime;

        long samplePos = second * m.nrSamplesPerTimeStep + minorTime;
        double time = samplePos / sampleRate;
        double shiftedTime = time - shifts[freq];

        if (time < 0.0) {
            //            return;
        }

        double phase = shiftedTime / period;
        phase -= Math.floor(phase);

        int bin = (int) (phase * NR_BINS);

        if (bin < 0) {
            bin = 0;
        } else if (bin >= NR_BINS) {
            bin = NR_BINS - 1;
        }

        //       System.out.println("handle, second = " + second + ", minor = " + minorTime + ", subband = " + subband +  ", channel = " + channel + ", freq = "+ freq + ", time = " + time + ", shifted = " + shiftedTime);

        bins[bin] += sample;
        counts[bin]++;
    }
}

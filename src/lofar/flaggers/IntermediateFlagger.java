package lofar.flaggers;

public final class IntermediateFlagger extends Flagger {

    public IntermediateFlagger(final float sensitivity, final float SIREtaValue) {
        super(sensitivity, SIREtaValue);
    }

    public void flag(final float[][] samples, final boolean[] flagged) {
        int nrPols = samples.length;

        // initalize flags of both polarizations with initial flags
        boolean[][] flags = new boolean[nrPols][flagged.length];
        for (int pol = 0; pol < nrPols; pol++) {
            flags[pol] = flagged.clone();
        }

        for (int pol = 0; pol < nrPols; pol++) {
            calculateWinsorizedStatistics(samples[pol], flags[pol]); // sets mean, median, stdDev
            sumThreshold1D(samples[pol], flags[pol]);

            calculateWinsorizedStatistics(samples[pol], flags[pol]); // sets mean, median, stdDev
            sumThreshold1D(samples[pol], flags[pol]);
        }

        // take union of flags of both polarizations

        for (int pol = 0; pol < nrPols; pol++) {
            for (int i = 0; i < flagged.length; i++) {
                flagged[i] |= flags[pol][i];
            }
        }

        SIROperator(flagged);

        //      printNrFlagged(flagged);
    }

    public void flagSmooth(final float[] samples, final boolean[] flagged) {

        //      float[] tmp = new float[samples.length];

        calculateWinsorizedStatistics(samples, flagged); // sets mean, median, stdDev
        sumThreshold1D(samples, flagged);

        System.err.println("samples flagged after 1st iter: " + getNrFlaggedSamples(flagged));

        calculateWinsorizedStatistics(samples, flagged); // sets mean, median, stdDev
        sumThreshold1D(samples, flagged);

        System.err.println("samples flagged after 2nd iter: " + getNrFlaggedSamples(flagged));

        float[] tmp = samples.clone();
        for (int i = 0; i < samples.length; i++) {
            if (flagged[i]) {
                tmp[i] = 0.0f;
            }
        }
        float[] smoothed = oneDimensionalGausConvolution(tmp, 2.0f);
        float[] diff = new float[samples.length];
        for (int i = 0; i < samples.length; i++) {
            diff[i] = samples[i] - smoothed[i];
        }

        calculateWinsorizedStatistics(diff, flagged); // sets mean, median, stdDev
        sumThreshold1D(diff, flagged);

        SIROperator(flagged);

        //      printNrFlagged(flagged);
    }
}

// on datasets/pulsar-experiment/station_0-non_flagged-10_mins-16_samples_per_second-bandpass_corrected.intermediate
// maxSeqNo 1000
// non-SIR 43805 0.53%
// SIR 0.2 58267 0.71%
// SIR 0.4 94308 1.15%

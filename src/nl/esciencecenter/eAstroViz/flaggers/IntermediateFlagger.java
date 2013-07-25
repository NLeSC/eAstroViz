package nl.esciencecenter.eAstroViz.flaggers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class IntermediateFlagger extends Flagger {
    private static final Logger logger = LoggerFactory.getLogger(IntermediateFlagger.class);

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

        logger.debug("samples flagged after 1st iter: " + getNrFlaggedSamples(flagged));

        calculateWinsorizedStatistics(samples, flagged); // sets mean, median, stdDev
        sumThreshold1D(samples, flagged);

        logger.debug("samples flagged after 2nd iter: " + getNrFlaggedSamples(flagged));

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

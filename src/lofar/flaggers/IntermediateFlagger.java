package lofar.flaggers;

public final class IntermediateFlagger extends Flagger {

    public IntermediateFlagger(final float sensitivity) {
        this.baseSensitivity = sensitivity;
    }

    public void flag(final float[] samples, final boolean[] flagged) {
        calculateWinsorizedStatistics(samples, flagged); // sets mean, median, stdDev
        sumThreshold1D(samples, flagged);

        calculateWinsorizedStatistics(samples, flagged); // sets mean, median, stdDev
        sumThreshold1D(samples, flagged);
        
        SIROperator(flagged, 0.4f);
    }
}

// on datasets/pulsar-experiment/station_0-non_flagged-10_mins-16_samples_per_second-bandpass_corrected.intermediate
// maxSeqNo 1000
// non-SIR 43805 0.53%
// SIR 0.2 58267 0.71%
// SIR 0.4 94308 1.15%

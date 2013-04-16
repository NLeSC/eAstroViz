package lofar.flaggers;

public class PostCorrelationThresholdFlagger extends PostCorrelationFlagger {

    final float cutoffThreshold = 7.0f;
    float sensitivity;

    public PostCorrelationThresholdFlagger(final int nrChannels, final float sensitivity) {
        super(nrChannels);
        this.sensitivity = sensitivity;
    }

    // we have the data for one second, all frequencies in a subband.
    @Override
    protected void flag(final float[] powers, final boolean[] flagged, final int pol) {
        //                calculateStatistics();
        calculateWinsorizedStatistics(powers, flagged);

        final float threshold = median + cutoffThreshold * sensitivity * stdDev;

        // if one of the polarizations exceeds the threshold, flag them all.
        for (int channel = 0; channel < nrChannels; channel++) {
            //                    System.err.println("median = " + median + ", stddev = " + stdDev + ", threshold = " + threshold + ", sample = " + power[channel]);
            if (powers[channel] >= threshold) {
                flagged[channel] = true;
            }
        }
    }

    //        printNrFlagged(flagged);
}

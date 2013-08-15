package nl.esciencecenter.eastroviz.flaggers;

public class PostCorrelationThresholdFlagger extends PostCorrelationFlagger {

    private final float cutoffThreshold = 7.0f;

    public PostCorrelationThresholdFlagger(final int nrChannels, final float sensitivity, final float SIREtaValue) {
        super(nrChannels, sensitivity, SIREtaValue);
    }

    // we have the data for one second, all frequencies in a subband.
    @Override
    protected void flag(final float[] powers, final boolean[] flagged, final int pol) {
        //                calculateStatistics();
        calculateWinsorizedStatistics(powers, flagged);

        final float threshold = getMedian() + cutoffThreshold * getBaseSensitivity() * getStdDev();

        // if one of the polarizations exceeds the threshold, flag them all.
        for (int channel = 0; channel < nrChannels; channel++) {
            if (powers[channel] >= threshold) {
                flagged[channel] = true;
            }
        }
    }

    //        printNrFlagged(flagged);
}

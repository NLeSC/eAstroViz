package lofar.flaggers;

public class PostCorrelationSumThresholdFlagger extends PostCorrelationFlagger {

    public PostCorrelationSumThresholdFlagger(final int nrChannels, final float baseSensitivity) { // higher baseSensitivity means less sensitive
        super(nrChannels);
        this.baseSensitivity = baseSensitivity;
    }

    // we have the data for one second, all frequencies in a subband.
    // if one of the polarizations exceeds the threshold, flag them all.
    @Override
    protected void flag(final float[] powers, final boolean[] flagged, final int pol) {
        calculateWinsorizedStatistics(powers, flagged); // sets mean, median, stdDev
        sumThreshold1D(powers, flagged);

        calculateWinsorizedStatistics(powers, flagged); // sets mean, median, stdDev
        sumThreshold1D(powers, flagged);

        SIROperator(flagged, 0.2f);
        
        //        printNrFlagged(flagged);
    }
}

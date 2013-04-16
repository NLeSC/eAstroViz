package lofar.flaggers;

public final class BeamFormedFlagger extends Flagger {
    public BeamFormedFlagger(final float sensitivity) {
        this.baseSensitivity = sensitivity;
    }

    public void flag(final float[] samples, final boolean[] flagged) {
        calculateWinsorizedStatistics(samples, flagged); // sets mean, median, stdDev
        sumThreshold1D(samples, flagged);

        calculateWinsorizedStatistics(samples, flagged); // sets mean, median, stdDev
        sumThreshold1D(samples, flagged);
    }
}

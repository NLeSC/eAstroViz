package nl.esciencecenter.eastroviz.flaggers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostCorrelationHistorySmoothedSumThresholdFlagger extends PostCorrelationSumThresholdFlagger {
    private static final Logger logger = LoggerFactory.getLogger(PostCorrelationSumThresholdFlagger.class);

    int second;
    final PostCorrelationFlaggerHistory history;
    final float historyFlaggingThreshold = 7.0f;

    public PostCorrelationHistorySmoothedSumThresholdFlagger(final int nrChannels, final float sensitivity, final float SIREtaValue) {
        super(nrChannels, sensitivity, SIREtaValue);
        history = new PostCorrelationFlaggerHistory(nrChannels);
    }

    // we have the data for one second, all frequencies in a subband.
    // if one of the polarizations exceeds the threshold, flag them all.
    @Override
    protected void flag(final float[] powers, final boolean[] flagged, final int pol) {
        final float originalSensitivity = getBaseSensitivity();
        calculateWinsorizedStatistics(powers, flagged); // sets mean, median, stdDev

        logger.trace("mean = " + mean + ", median = " + median + ", stdDev = " + stdDev);

        // first do an insensitive sumthreshold
        setBaseSensitivity(originalSensitivity * 1.0f); // higher number is less sensitive!
        sumThreshold1D(powers, flagged); // sets flags, and replaces flagged samples with threshold

        // smooth
        final float[] smoothedPower = oneDimensionalGausConvolution(powers, 0.5f); // 2nd param is sigma, height of the gaussian curve

        // calculate difference
        final float[] diff = new float[nrChannels];
        for (int i = 0; i < nrChannels; i++) {
            diff[i] = powers[i] - smoothedPower[i];
        }

        // flag based on difference
        calculateWinsorizedStatistics(diff, flagged); // sets mean, median, stdDev                
        setBaseSensitivity(originalSensitivity * 1.0f); // higher number is less sensitive!
        sumThreshold1D(diff, flagged);

        // and one final pass on the flagged power
        calculateWinsorizedStatistics(powers, flagged); // sets mean, median, stdDev
        setBaseSensitivity(originalSensitivity * 0.90f); // higher number is less sensitive!
        sumThreshold1D(powers, flagged);
        setBaseSensitivity(originalSensitivity);

        calculateWinsorizedStatistics(powers, flagged); // sets mean, median, stdDev

        if (history.getSize(pol) >= PostCorrelationFlaggerHistory.MIN_HISTORY_SIZE) {
            final float meanMedian = history.getMeanMedian(pol);
            final float stdDevOfMedians = history.getStdDevOfMedians(pol);
            final boolean flagSecond = median > (meanMedian + historyFlaggingThreshold * stdDevOfMedians);

            logger.trace("median = " + median + ", meanMedian = " + meanMedian + ", factor = " + (median / meanMedian) + ", stddev = " + stdDevOfMedians
                    + (flagSecond ? " FLAGGED" : ""));
            if (flagSecond) {
                for (int i = 0; i < nrChannels; i++) {
                    flagged[i] = true;
                }
                // add the mean to the history
                history.add(pol, second, mean, meanMedian, stdDev, powers);

                return;
            } else {
                // add the corrected power statistics to the history
                history.add(pol, second, mean, median, stdDev, powers);
            }
        } else { // we don't have enough history yet, let's just add it
            // add the corrected power statistics to the history
            history.add(pol, second, mean, median, stdDev, powers);
        }

        if (pol == 0) {
            second++;
        }
    }
}

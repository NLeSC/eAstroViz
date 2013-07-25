package nl.esciencecenter.eAstroViz.flaggers;

public class PostCorrelationHistorySumThresholdFlagger extends PostCorrelationSumThresholdFlagger {
    int second;
    final PostCorrelationFlaggerHistory history;
    final float historyFlaggingThreshold = 7.0f;

    public PostCorrelationHistorySumThresholdFlagger(final int nrChannels, final float sensitivity, final float SIREtaValue) {
        super(nrChannels, sensitivity, SIREtaValue);
        history = new PostCorrelationFlaggerHistory(nrChannels);
    }

    // we have the data for one second, all frequencies in a subband.
    // if one of the polarizations exceeds the threshold, flag them all.
    @Override
    protected void flag(final float[] powers, final boolean[] flagged, final int pol) {

        calculateWinsorizedStatistics(powers, flagged); // sets mean, median, stdDev

        //System.err.println("mean = " + mean + ", median = " + median + ", stdDev = " + stdDev);

        sumThreshold1D(powers, flagged); // sets flags, and replaces flagged samples with threshold

        calculateWinsorizedStatistics(powers, flagged); // sets mean, median, stdDev

        if (history.getSize(pol) >= PostCorrelationFlaggerHistory.MIN_HISTORY_SIZE) {
            final float[] integratedPowers = history.getIntegratedPowers(pol);
            //                for (int i = 0; i < integratedPowers.length; i++) {
            //                    System.out.println("integrated [" + i + "] = " + integratedPowers[i]);
            //                }
            integratedHistoryFlagger(integratedPowers, flagged);
            // we screwed up the stats, just recalculate :-)
            calculateWinsorizedStatistics(powers, flagged);
        }

        if (history.getSize(pol) >= PostCorrelationFlaggerHistory.MIN_HISTORY_SIZE) {
            final float meanMedian = history.getMeanMedian(pol);
            final float stdDevOfMedians = history.getStdDevOfMedians(pol);
            final boolean flagSecond = median > (meanMedian + historyFlaggingThreshold * stdDevOfMedians);

            // System.err.println("median = " + median + ", meanMedian = " + meanMedian + ", factor = " + (median / meanMedian) + ", stddev = " + stdDevOfMedians + (flagSecond ? " FLAGGED" : ""));
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

    void integratedHistoryFlagger(final float[] integratedPowers, final boolean[] flagged) {
        calculateWinsorizedStatistics(integratedPowers, flagged);
        sumThreshold1D(integratedPowers, flagged); // sets flags, and replaces flagged samples with threshold
    }
}

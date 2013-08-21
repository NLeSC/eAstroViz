/*
 * Copyright 2013 Netherlands eScience Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.esciencecenter.eastroviz.flaggers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostCorrelationHistorySumThresholdFlagger extends PostCorrelationSumThresholdFlagger {
    private static final Logger logger = LoggerFactory.getLogger(PostCorrelationSumThresholdFlagger.class);
    private int second;
    private final PostCorrelationFlaggerHistory history;
    private final float historyFlaggingThreshold = 7.0f;

    public PostCorrelationHistorySumThresholdFlagger(final int nrChannels, final float sensitivity, final float SIREtaValue) {
        super(nrChannels, sensitivity, SIREtaValue);
        history = new PostCorrelationFlaggerHistory(nrChannels);
    }

    // we have the data for one second, all frequencies in a subband.
    // if one of the polarizations exceeds the threshold, flag them all.
    @Override
    protected void flag(final float[] powers, final boolean[] flagged, final int pol) {

        calculateWinsorizedStatistics(powers, flagged); // sets mean, median, stdDev

        logger.trace("mean = " + getMean() + ", median = " + getMedian() + ", stdDev = " + getStdDev());

        sumThreshold1D(powers, flagged); // sets flags, and replaces flagged samples with threshold

        calculateWinsorizedStatistics(powers, flagged); // sets mean, median, stdDev

        if (history.getSize(pol) >= PostCorrelationFlaggerHistory.MIN_HISTORY_SIZE) {
            final float[] integratedPowers = history.getIntegratedPowers(pol);
            integratedHistoryFlagger(integratedPowers, flagged);
            // we screwed up the stats, just recalculate :-)
            calculateWinsorizedStatistics(powers, flagged);
        }

        if (history.getSize(pol) >= PostCorrelationFlaggerHistory.MIN_HISTORY_SIZE) {
            final float meanMedian = history.getMeanMedian(pol);
            final float stdDevOfMedians = history.getStdDevOfMedians(pol);
            final boolean flagSecond = getMedian() > (meanMedian + historyFlaggingThreshold * stdDevOfMedians);

            logger.trace("median = " + getMedian() + ", meanMedian = " + meanMedian + ", factor = " + (getMedian() / meanMedian)
                    + ", stddev = " + stdDevOfMedians + (flagSecond ? " FLAGGED" : ""));
            if (flagSecond) {
                for (int i = 0; i < getNrChannels(); i++) {
                    flagged[i] = true;
                }
                // add the mean to the history
                history.add(pol, second, getMean(), meanMedian, getStdDev(), powers);

                return;
            } else {
                // add the corrected power statistics to the history
                history.add(pol, second, getMean(), getMedian(), getStdDev(), powers);
            }
        } else { // we don't have enough history yet, let's just add it
            // add the corrected power statistics to the history
            history.add(pol, second, getMean(), getMedian(), getStdDev(), powers);
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

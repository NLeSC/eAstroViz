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

/*
Wanneer je informatie hebt in frequentierichting zou je ook kunnen
overwegen om (iteratief?) te "smoothen" in die richting: dus eerst een
eerste ongevoelige (sum)threshold om de grootste pieken eruit te halen,
dan een lowpass filter (e.g. 1d Gaussian convolution) op de
frequentierichting uitvoeren, thresholden+smoothen nogmaals herhalen op
het verschil en dan een laatste keer sumthresholden. Daardoor
"vergelijk" je kanalen onderling en wordt je ook gevoeliger voor
veranderingen niet-broadband RFI. Wanneer je een "absolute" sumthreshold
"broadband" detector + de gesmoothde spectrale detector combineert is
dit is erg effectief, denk ik. Dit is natuurlijk een geavanceerdere
thresholder en vast niet het eerste algoritme wat je wilt implementeren
-- ik weet uberhaubt niet of het technisch mogelijk is op de blue gene,
maar het is een idee. Het is niet zoo zwaar om dit te doen.
 */
public class PostCorrelationSmoothedSumThresholdFlagger extends PostCorrelationSumThresholdFlagger {
    private static final Logger logger = LoggerFactory.getLogger(PostCorrelationSumThresholdFlagger.class);

    public PostCorrelationSmoothedSumThresholdFlagger(final int nrChannels, final float baseSensitivity, final float SIREtaValue) {
        super(nrChannels, baseSensitivity, SIREtaValue);
    }

    // we have the data for one second, all frequencies in a subband.
    // if one of the polarizations exceeds the threshold, flag them all.
    @Override
    protected void flag(final float[] powers, final boolean[] flagged, final int pol) {

        calculateWinsorizedStatistics(powers, flagged); // sets mean, median, stdDev

        logger.trace("mean = " + getMean() + ", median = " + getMedian() + ", stdDev = " + getStdDev());

        // first do an insensitive sumthreshold
        final float originalSensitivity = getBaseSensitivity();
        setBaseSensitivity(originalSensitivity * 1.0f); // higher number is less sensitive!
        sumThreshold1D(powers, flagged); // sets flags, and replaces flagged samples with threshold

        // smooth
        final float[] smoothedPower = oneDimensionalGausConvolution(powers, 0.5f); // 2nd param is sigma, heigth of the gauss curve

        // calculate difference
        final float[] diff = new float[getNrChannels()];
        for (int i = 0; i < getNrChannels(); i++) {
            diff[i] = powers[i] - smoothedPower[i];
        }

        // flag based on difference
        calculateWinsorizedStatistics(diff, flagged); // sets mean, median, stdDev                
        setBaseSensitivity(originalSensitivity * 1.0f); // higher number is less sensitive!
        sumThreshold1D(diff, flagged);

        // and one final pass on the flagged power
        calculateWinsorizedStatistics(powers, flagged); // sets mean, median, stdDev
        setBaseSensitivity(originalSensitivity * 0.80f); // higher number is less sensitive!
        sumThreshold1D(powers, flagged);

        setBaseSensitivity(originalSensitivity);
    }
}

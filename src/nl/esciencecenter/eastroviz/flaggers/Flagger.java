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

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * There are three options for statistics. 1. Use medians 2. more robust: use winsorized medians. In this case, we are 10% robust
 * against polluted data. 3. Much better (learned at RFI2016 workshop, talk by Kaushal D. Buch) is to use the STDDEV_MAD. This gives 50%
 * robustness against polluted data.
 */
public abstract class Flagger {
    private static StatisticsType statisticsType = StatisticsType.STDDEV_MAD;
    private static final int MAX_ITERS = 5;
    private static final float FIRST_THRESHOLD = 6.0f; // from Andre's code: 6.0f
    private static final float GAUSSIAN_SCALE_FACTOR = 1.4826f; // Scale value to estimate stddev from the MAD. See https://en.wikipedia.org/wiki/Median_absolute_deviation

    private static final Logger logger = LoggerFactory.getLogger(Flagger.class);

    private float mean;
    private float stdDev;
    private float median;
    private float baseSensitivity;
    private float SIREtaValue;

    public Flagger(float baseSensitivity, float SIRValue) {
        this.baseSensitivity = baseSensitivity;
        this.SIREtaValue = SIRValue;
    }

    protected final void calculateStatistics(final float[] samples, final boolean[] flags) {
        int unflaggedCount = getNrUnflaggedSamples(flags);
        if (unflaggedCount == 0) {
            median = 0.0f;
            mean = 0.0f;
            stdDev = 0.0f;
            return;
        }

        float[] cleanSamples = getCleanSamples(samples, flags, unflaggedCount);
        Arrays.sort(cleanSamples);

        switch (statisticsType) {
        case STDDEV_MEDIAN:
            calculateStatisticsNormal(cleanSamples, flags);
            break;
        case STDDEV_WINSORIZED_MEDIAN:
            calculateWinsorizedStatistics(cleanSamples, flags);
        case STDDEV_MAD:
            calculateMADStatistics(cleanSamples, flags);
            break;
        }
    }

    private final void calculateStatisticsNormal(final float[] cleanSamples, final boolean[] flags) {
        mean = 0.0f;
        for (float cleanSample : cleanSamples) {
            mean += cleanSample;
        }
        mean /= cleanSamples.length;

        median = cleanSamples[cleanSamples.length / 2];

        stdDev = 0.0f;
        for (float cleanSample : cleanSamples) {
            final float diff = cleanSample - mean;
            stdDev += diff * diff;
        }
        stdDev /= cleanSamples.length;
        stdDev = (float) Math.sqrt(stdDev);
    }

    private final void calculateWinsorizedStatistics(final float[] cleanSamples, final boolean[] flags) {
        int lowIndex = (int) Math.floor(0.1 * cleanSamples.length);
        int highIndex = (int) Math.ceil(0.9 * cleanSamples.length);
        if (highIndex > 0) {
            highIndex--;
        }
        float lowValue = cleanSamples[lowIndex];
        float highValue = cleanSamples[highIndex];

        median = cleanSamples[cleanSamples.length / 2];

        // Assume an array of 0 .. 9; low idx = 3; high idx = 7
        // low vals = 0, 1, 2, 3 -> #= 4
        // normal:    4, 5, 6    -> #= 3
        // high vals: 7, 8, 9    -> #= 3
        
        // Calculate mean
        mean = (lowIndex+1) * lowValue;
        for (int i = lowIndex+1; i < highIndex; i++) {
            mean += cleanSamples[i];
        }
        mean += (cleanSamples.length - highIndex) * highValue;
        mean /= cleanSamples.length;
        
        stdDev = (lowIndex+1) * ((lowValue - mean) * (lowValue - mean)) ;
        for (int i = lowIndex+1; i < highIndex; i++) {
            stdDev += (cleanSamples[i] - mean) * (cleanSamples[i] - mean);
        }
        stdDev += (cleanSamples.length - highIndex) * ((highValue - mean) * (highValue - mean));
        stdDev /= cleanSamples.length;
        stdDev = (float) Math.sqrt(1.54 * stdDev / cleanSamples.length);

        if (logger.isTraceEnabled()) {
            logger.trace("winsorized stats: unFlaggedCount = " + cleanSamples.length + ", mean = " + mean + ", median = " + median
                    + ", stddev = " + stdDev);
        }
    }

    private final void calculateMADStatistics(final float[] cleanSamples, final boolean[] flags) {
        mean = 0.0f;
        for (float cleanSample : cleanSamples) {
            mean += cleanSample;
        }
        mean /= cleanSamples.length;

        median = cleanSamples[cleanSamples.length / 2];

        // Calculate STDDEV_MAD
        float mad = 0.0f;
        for (int i = 0; i < cleanSamples.length; i++) {
            mad += Math.abs(cleanSamples[i] - median);
        }
        stdDev = GAUSSIAN_SCALE_FACTOR * (mad / cleanSamples.length);

        if (logger.isTraceEnabled()) {
            logger.trace("winsorized stats: unFlaggedCount = " + cleanSamples.length + ", mean = " + mean + ", median = " + median
                    + ", stddev = " + stdDev);
        }
    }

    protected final void sumThreshold1D(final float[] samples, final boolean[] flags) {
        float factor;

        if (stdDev == 0.0f) {
            factor = baseSensitivity;
        } else {
            factor = stdDev * baseSensitivity;
        }

        int window = 1;
        for (int iter = 1; iter <= MAX_ITERS; iter++) {
            final float thresholdI = median + calcThresholdI(FIRST_THRESHOLD, window, 1.5f) * factor;

            sumThreshold(samples, flags, window, thresholdI);
            window *= 2;
        }
    }

    private final void sumThreshold(final float[] samples, final boolean[] flags, final int window, final float threshold) {
        logger.trace("sumthreshold window = " + window + ", threshold = " + threshold);

        for (int base = 1; base + window < samples.length; base++) {
            float sum = 0.0f;
            int count = 0;

            for (int pos = base; pos < base + window; pos++) {
                if (!flags[pos]) {
                    sum += samples[pos];
                    count++;
                }
            }

            if (sum >= count * threshold) {
                // flag all samples in the sequence!
                for (int pos = base; pos < base + window; pos++) {
                    flags[pos] = true;
                }
            }
        }
    }

    private static final float[] oneDimensionalConvolution(final float[] data, final float[] kernel) {
        final float[] result = new float[data.length];
        for (int i = 0; i < data.length; ++i) {
            final int offset = i - kernel.length / 2;
            int start, end;

            if (offset < 0) {
                start = -offset;
            } else {
                start = 0;
            }
            if (offset + kernel.length > data.length) {
                end = data.length - offset;
            } else {
                end = kernel.length;
            }

            float sum = 0.0f;
            float weight = 0.0f;
            for (int k = start; k < end; k++) {
                sum += data[k + offset] * kernel[k];
                weight += kernel[k];
            }

            if (weight != 0.0f) {
                result[i] = sum / weight;
            }
        }
        return result;
    }

    public static final float[] oneDimensionalGausConvolution(final float[] data, final float sigma) {
        int kernelSize = (int) Math.round(sigma * 3.0);
        if (kernelSize < 1) {
            kernelSize = 1;
        } else if (kernelSize > data.length) {
            kernelSize = data.length;
        }

        final float[] kernel = new float[kernelSize];
        for (int i = 0; i < kernel.length; ++i) {
            final float x = i - kernel.length / 2.0f;
            kernel[i] = evaluateGaussian(x, sigma);
        }
        return oneDimensionalConvolution(data, kernel);
    }

    private static final float evaluateGaussian(final float x, final float sigma) {
        return (float) (1.0 / (sigma * Math.sqrt(2.0 * Math.PI)) * Math.exp(-0.5 * x * x / sigma));
    }

    private static float[] getCleanSamples(float[] samples, boolean[] flags, int destSize) {
        if (destSize == samples.length) {
            return samples.clone();
        }

        float[] cleanSamples = new float[destSize];
        int destIndex = 0;
        for (int i = 0; i < samples.length; i++) {
            if (!flags[i]) {
                cleanSamples[destIndex] = samples[i];
                destIndex++;
                if (destIndex >= destSize) {
                    break;
                }
            }
        }
        return cleanSamples;
    }

    protected static final int getNrFlaggedSamples(final boolean[] flags) {
        int count = 0;
        for (final boolean b : flags) {
            if (b) {
                count++;
            }
        }

        return count;
    }

    protected static final int getNrUnflaggedSamples(final boolean[] flags) {
        return flags.length - getNrFlaggedSamples(flags);
    }

    protected static final void printNrFlagged(final boolean[] flags) {
        final int flagCount = getNrFlaggedSamples(flags);
        logger.info("Flagger: flagged samples for this second: " + flagCount);
    }

    private final float calcThresholdI(final float threshold1, final int window, float p) {
        if (p <= 0.0f) {
            p = 1.5f; // according to Andre Offringa's RFI paper, this is a good default value
        }

        return (float) (threshold1 * Math.pow(p, logBase2(window)) / window);
    }

    private static final float logBase2(final float x) {
        // log(base 2) x=log(base e) x/log(base e) 2
        return (float) (Math.log(x) / Math.log(2.0));
    }

    @SuppressWarnings("unused")
    private final float median(final float arr[]) {
        return quickSelect(arr, arr.length / 2);
    }

    private final void swap(final float[] r, final int a, final int b) {
        final float tmp = r[a];
        r[a] = r[b];
        r[b] = tmp;
    }

    /*
     *  This Quickselect routine is based on the algorithm described in
     *  "Numerical recipes in C", Second Edition,
     *  Cambridge University Press, 1992, Section 8.5, ISBN 0-521-43108-5
     */
    private final float quickSelect(final float a[], final int n) {
        int low, high;
        int median;
        int middle, ll, hh;

        low = 0;
        high = n - 1;
        median = (low + high) / 2;
        for (;;) {
            if (high <= low) {
                return a[median];
            }

            if (high == low + 1) { /* Two elements only */
                if (a[low] > a[high]) {
                    swap(a, low, high);
                }
                return a[median];
            }

            /* Find median of low, middle and high items; swap into position low */
            middle = (low + high) >>> 1;
            if (a[middle] > a[high]) {
                swap(a, middle, high);
            }
            if (a[low] > a[high]) {
                swap(a, low, high);
            }
            if (a[middle] > a[low]) {
                swap(a, middle, low);
            }

            /* Swap low item (now in position middle) into position (low+1) */
            swap(a, middle, low + 1);

            /* Nibble from each end towards middle, swapping items when stuck */
            ll = low + 1;
            hh = high;
            for (;;) {
                do {
                    ll++;
                } while (a[low] > a[ll]);
                do {
                    hh--;
                } while (a[hh] > a[low]);

                if (hh < ll) {
                    break;
                }

                swap(a, ll, hh);
            }

            /* Swap middle item (in position low) back into correct position */
            swap(a, low, hh);

            /* Re-set active partition */
            if (hh <= median) {
                low = ll;
            }
            if (hh >= median) {
                high = hh - 1;
            }
        }
    }

    public final float getMean() {
        return mean;
    }

    public final float getStdDev() {
        return stdDev;
    }

    public final float getMedian() {
        return median;
    }

    /**
     * This is an experimental algorithm that might be slightly faster than the original algorithm by Andre Offringa. Jasper van
     * de Gronde is preparing an article about it.
     * 
     * @param flags
     *            The input array of flags to be dilated that will be overwritten by the dilatation of itself. SIREtaValue is the
     *            η parameter that specifies the minimum number of good data that any subsequence should have.
     */
    public void SIROperator(boolean[] flags) {
        boolean[] temp = new boolean[flags.length];
        float credit = 0.0f;
        for (int i = 0; i < flags.length; ++i) {
            // credit ← max(0, credit) + w(f [i])
            final float w = flags[i] ? SIREtaValue : SIREtaValue - 1.0f;
            final float maxcredit0 = credit > 0.0f ? credit : 0.0f;
            credit = maxcredit0 + w;
            temp[i] = (credit >= 0.0f);
        }

        // The same iteration, but now backwards
        credit = 0.0f;
        for (int i = flags.length - 1; i >= 0; i--) {
            final float w = flags[i] ? SIREtaValue : SIREtaValue - 1.0f;
            final float maxcredit0 = credit > 0.0f ? credit : 0.0f;
            credit = maxcredit0 + w;
            flags[i] = (credit >= 0.0f) || temp[i];
        }
    }

    public float getBaseSensitivity() {
        return baseSensitivity;
    }

    public void setBaseSensitivity(float baseSensitivity) {
        this.baseSensitivity = baseSensitivity;
    }
}

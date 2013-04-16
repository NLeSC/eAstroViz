package lofar.flaggers;

import java.util.Arrays;

public abstract class Flagger {
    private static final int MAX_ITERS = 5;

    protected float mean;
    protected float stdDev;
    protected float median;

    protected float baseSensitivity;
    private final float firstThreshold = 6.0f; // from Andre's code: 6.0f

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

        mean = 0.0f;
        for (int i = 0; i < cleanSamples.length; i++) {
            mean += cleanSamples[i];
        }
        mean /= unflaggedCount;

        median = cleanSamples[unflaggedCount / 2];

        stdDev = 0.0f;
        for (int i = 0; i < cleanSamples.length; i++) {
            final float diff = cleanSamples[i] - mean;
            stdDev += diff * diff;
        }
        stdDev /= unflaggedCount;
        stdDev = (float) Math.sqrt(stdDev);
    }

    protected final void calculateWinsorizedStatistics(final float[] samples, final boolean[] flags) {
        int unflaggedCount = getNrUnflaggedSamples(flags);

        if (unflaggedCount == 0) {
            median = 0.0f;
            mean = 0.0f;
            stdDev = 0.0f;
            return;
        }

        float[] cleanSamples = getCleanSamples(samples, flags, unflaggedCount);
        Arrays.sort(cleanSamples);

        int lowIndex = (int) Math.floor(0.1 * unflaggedCount);
        int highIndex = (int) Math.ceil(0.9 * unflaggedCount);
        if (highIndex > 0)
            highIndex--;
        float lowValue = cleanSamples[lowIndex];
        float highValue = cleanSamples[highIndex];

        median = cleanSamples[cleanSamples.length / 2];

        // Calculate mean
        mean = 0.0f;
        for (int i = 0; i < unflaggedCount; i++) {
            final float value = cleanSamples[i];
            if (value < lowValue) {
                mean += lowValue;
            } else if (value > highValue) {
                mean += highValue;
            } else {
                mean += value;
            }
        }
        mean /= unflaggedCount;

        // Calculate variance
        stdDev = 0.0f;
        for (int i = 0; i < unflaggedCount; i++) {
            final float value = cleanSamples[i];
            if (value < lowValue) {
                stdDev += (lowValue - mean) * (lowValue - mean);
            } else if (value > highValue) {
                stdDev += (highValue - mean) * (highValue - mean);
            } else {
                stdDev += (value - mean) * (value - mean);
            }
        }
        stdDev = (float) Math.sqrt(1.54 * stdDev / unflaggedCount);
        // System.err.println("winsorized stats: size = " + samples.length + ", count = " + count + ", mean = " + mean + ", median = " + median + ", stddev = " + stdDev);
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
            final float thresholdI = median + calcThresholdI(firstThreshold, iter, 1.5f) * factor;

            sumThreshold(samples, flags, window, thresholdI);
            window *= 2;
        }
    }

    private final void sumThreshold(final float[] samples, final boolean[] flags, final int window, final float threshold) {
        //         System.err.println("sumthreshold window = " + window + ", threshold = " + threshold);

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

    protected static final float[] oneDimensionalGausConvolution(final float[] data, final float sigma) {
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
        System.err.println("Flagger: flagged samples for this second: " + flagCount);
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
            middle = (low + high) / 2;
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
     * This is an experimental algorithm that might be slightly faster than
     * the original algorithm by Andre Offringa. Jasper van de Gronde is preparing an article about it.
     * @param [in,out] flags The input array of flags to be dilated that will be overwritten by the dilatation of itself.
     * @param [in] eta The η parameter that specifies the minimum number of good data
     * that any subsequence should have (see class description for the definition).
     */
    public void SIROperator(boolean[] flags, float eta) {
        boolean[] temp = new boolean[flags.length];
        float credit = 0.0f;
        for (int i = 0; i < flags.length; ++i) {
            // credit ← max(0, credit) + w(f [i])
            final float w = flags[i] ? eta : eta - 1.0f;
            final float maxcredit0 = credit > 0.0f ? credit : 0.0f;
            credit = maxcredit0 + w;
            temp[i] = (credit >= 0.0f);
        }

        // The same iteration, but now backwards
        credit = 0.0f;
        for (int i = flags.length - 1; i >= 0; i--) {
            final float w = flags[i] ? eta : eta - 1.0f;
            final float maxcredit0 = credit > 0.0f ? credit : 0.0f;
            credit = maxcredit0 + w;
            flags[i] = (credit >= 0.0f) || temp[i];
        }
    }
}

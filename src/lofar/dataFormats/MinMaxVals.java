package lofar.dataFormats;

public final class MinMaxVals {
    private float minVal = 1.0E20f;
    private float maxVal = -10000000.0f;

    private float[] minVals;
    private float[] maxVals;

    public MinMaxVals(final int size) {
        minVals = new float[size];
        maxVals = new float[size];
        for (int sb = 0; sb < size; sb++) {
            minVals[sb] = 1.0E20f;
            maxVals[sb] = -10000000.0f;
        }
    }

    public void processValue(final float sample, final int sb) {
        if (sample < minVal) {
            minVal = sample;
        }
        if (sample > maxVal) {
            maxVal = sample;
        }

        if (sample < minVals[sb]) {
            minVals[sb] = sample;
        }
        if (sample > maxVals[sb]) {
            maxVals[sb] = sample;
        }
    }

    public float getMin() {
        return minVal;
    }

    public float getMin(final int sb) {
        return minVals[sb];
    }

    public float getMax() {
        return maxVal;
    }

    public float getMax(final int sb) {
        return maxVals[sb];
    }
}

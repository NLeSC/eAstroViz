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
package nl.esciencecenter.eastroviz.dataformats;

/**
 * @author rob
 * 
 */
public abstract class DataProvider {

    public static final int SIZE_OF_FLOAT = 4;

    private String flaggerType;
    private float flaggerSensitivity = 1.0f;
    private float flaggerSIRValue = 0.4f;
    private String[] polList; // polarizations or stokes
    private String[] flaggerList;
    private String fileName;
    private int maxSequenceNr;
    private int maxSubbands;

    protected DataProvider() {
    }

    protected void init(final String fileName, final int maxSequenceNr, final int maxSubbands, final String[] polList, final String[] flaggerList) {
        this.fileName = fileName;
        this.maxSequenceNr = maxSequenceNr;
        if (this.maxSequenceNr <= 0) {
            this.maxSequenceNr = Integer.MAX_VALUE;
        }
        this.maxSubbands = maxSubbands;
        if (this.maxSubbands <= 0) {
            this.maxSubbands = Integer.MAX_VALUE;
        }
        this.polList = polList.clone();
        this.flaggerList = flaggerList.clone();
        this.flaggerType = this.flaggerList[0];
    }

    /**
     * @return the total data size in the x direction (usually time).
     */
    public abstract int getSizeX();

    /**
     * @return the total data size in the y direction (usually frequency). If the data has both subbands and channels, this refers
     *         to them both, to the total number of frequency channels is returned (e.g., nrSubbands * nrChannels).
     */
    public abstract int getSizeY();

    /**
     * @return The number of frequenycy subbands in the data set.
     */
    public abstract int getNrSubbands();

    /**
     * @return the number of frequency channels per subband.
     */
    public abstract int getNrChannels();

    /**
     * @param x
     *            X-position in the data
     * @param y
     *            Y-position in the data
     * @param polarization
     *            the polarization, or, in case of beam formed data, the stoke
     * @return a value between 0 and 1
     */
    public abstract float getValue(int x, int y);

    /**
     * @param x
     *            X-position in the data
     * @param y
     *            Y-position in the data
     * @param polarization
     *            the polarization, or, in case of beam formed data, the stoke
     * @return The original unscaled data value
     */
    public abstract float getRawValue(int x, int y);

    /**
     * Flags are not kept per polarization, so we have one parameter less compared to the getValues for the data itself.
     * 
     * @param x
     *            X-position in the data
     * @param y
     *            Y-position in the data
     * @return
     */
    public abstract boolean isFlagged(int x, int y);

    /**
     * Flag the data with the currently selected built in flagger.
     */
    public abstract void flag();

    /**
     * Set the current flagger to a new flagger type.
     * 
     * @param name
     *            the name of the flagger. For a list of valid names, see getFlaggerNames().
     */
    public void setFlagger(final String name) {
        if (name.equals(flaggerType)) {
            return;
        }

        for (final String element : flaggerList) {
            if (name.equals(element)) {
                flaggerType = element;
                flag();
                return;
            }
        }

        throw new RuntimeException("illegal flagger type");
    }

    public String getFlagger() {
        return flaggerType;
    }

    public String[] getFlaggerNames() {
        return flaggerList;
    }

    public float getFlaggerSensitivity() {
        return flaggerSensitivity;
    }

    public void setFlaggerSensitivity(final float flaggerSensitivity) {
        if (this.flaggerSensitivity == flaggerSensitivity) {
            return;
        }
        this.flaggerSensitivity = flaggerSensitivity;
        flag();
    }

    public String[] getPolarizationNames() {
        return polList;
    }

    public final String getFileName() {
        return fileName;
    }

    public final int getMaxSequenceNr() {
        return maxSequenceNr;
    }

    public final int getMaxSubbands() {
        return maxSubbands;
    }

    public abstract int getStation1();

    public abstract int setStation1(int station1);

    public abstract int getStation2();

    public abstract int setStation2(int station2);

    public abstract int getPolarization();

    public abstract int setPolarization(int newValue);

    public abstract String polarizationToString(final int pol);

    public abstract int StringToPolarization(final String polString);

    /**
     * This is a utility function that takes an array of values (floats), and scales them, so all values are between 0 and 1. The
     * scaling is done in-place.
     * 
     * @param in
     *            the array that should be scaled.
     */
    public static final void scale(final float[] in) {
        float max = Float.MIN_VALUE;
        float min = Float.MAX_VALUE;
        for (final float element : in) {
            if (element < min) {
                min = element;
            }
            if (element > max) {
                max = element;
            }
        }

        for (int i = 0; i < in.length; i++) {
            in[i] = (in[i] - min) / (max - min);
        }
    }

    /**
     * This is a utility function that takes an array of values (doubles), and scales them, so all values are between 0 and 1. The
     * scaling is done in-place.
     * 
     * @param in
     *            the array that should be scaled.
     */
    public static final void scale(final double[] in) {
        double max = Double.MIN_VALUE;
        double min = Double.MAX_VALUE;
        for (final double element : in) {
            if (element < min) {
                min = element;
            }
            if (element > max) {
                max = element;
            }
        }

        for (int i = 0; i < in.length; i++) {
            in[i] = (in[i] - min) / (max - min);
        }
    }

    /**
     * This is a utility function that takes an array of values (floats), and scales them, so all values are between 0 and 1. The
     * scaling is done in-place.
     * 
     * @param in
     *            the array that should be scaled.
     */
    public static final void scale(final float[][] in) {
        float max = -10000000.0f;
        float min = 1.0E20f;

        for (float[] element2 : in) {
            for (float element : element2) {
                if (element < min) {
                    min = element;
                }
                if (element > max) {
                    max = element;
                }
            }
        }

        for (int y = 0; y < in.length; y++) {
            for (int x = 0; x < in[y].length; x++) {
                in[y][x] = (in[y][x] - min) / (max - min);
            }
        }
    }

    protected String getFlaggerType() {
        return flaggerType;
    }

    protected void setFlaggerType(String flaggerType) {
        this.flaggerType = flaggerType;
    }

    protected float getFlaggerSIRValue() {
        return flaggerSIRValue;
    }

    protected void setFlaggerSIRValue(float flaggerSIRValue) {
        this.flaggerSIRValue = flaggerSIRValue;
    }

    protected String[] getPolList() {
        return polList;
    }

    protected void setPolList(String[] polList) {
        this.polList = polList;
    }

    protected String[] getFlaggerList() {
        return flaggerList;
    }

    protected void setFlaggerList(String[] flaggerList) {
        this.flaggerList = flaggerList;
    }

    protected void setFileName(String fileName) {
        this.fileName = fileName;
    }

    protected void setMaxSequenceNr(int maxSequenceNr) {
        this.maxSequenceNr = maxSequenceNr;
    }

    protected void setMaxSubbands(int maxSubbands) {
        this.maxSubbands = maxSubbands;
    }
}

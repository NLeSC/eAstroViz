package nl.esciencecenter.eAstroViz.dataFormats;

public abstract class DataProvider {

    public static final int SIZE_OF_FLOAT = 4;

    protected String flaggerType;
    protected float flaggerSensitivity = 1.0f;
    protected float flaggerSIRValue = 0.4f;
    protected String[] polList; // polarizations or stokes
    protected String[] flaggerList;
    protected String fileName;
    protected int maxSequenceNr;
    protected int maxSubbands;

    protected DataProvider() {

    }

    protected void init(final String fileName, final int maxSequenceNr, final int maxSubbands, final String[] polList, final String[] flaggerList) {
        this.fileName = fileName;
        this.maxSequenceNr = maxSequenceNr;
        this.maxSubbands = maxSubbands;
        this.polList = polList.clone();
        this.flaggerList = flaggerList.clone();
        this.flaggerType = this.flaggerList[0];
    }

    public abstract int getSizeX();

    public abstract int getSizeY();

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
     * @param y
     * @return
     */
    public abstract boolean isFlagged(int x, int y);

    public abstract void flag();

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

    public String[] getPolarizationNames() {
        return polList;
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

    public final String getFileName() {
        return fileName;
    }

    public final int getMaxSequenceNr() {
        return maxSequenceNr;
    }

    public final int getMaxSubbands() {
        return maxSubbands;
    }

    public static final void scale(final float[] in) {
        float max = -10000000.0f;
        float min = 1.0E20f;
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

    public static final void scale(final float[][] in) {
        float max = -10000000.0f;
        float min = 1.0E20f;

        for (int y = 0; y < in.length; y++) {
            for (int x = 0; x < in[y].length; x++) {
                float element = in[y][x];
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

    public abstract int getStation1();

    public abstract int setStation1(int station1);

    public abstract int getStation2();

    public abstract int setStation2(int station2);

    public abstract int getPolarization();

    public abstract int setPolarization(int newValue);

    public abstract String polarizationToString(final int pol);

    public abstract int StringToPolarization(final String polString);

}

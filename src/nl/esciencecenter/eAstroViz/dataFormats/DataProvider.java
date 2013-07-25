package nl.esciencecenter.eAstroViz.dataFormats;

public abstract class DataProvider {
    protected String flaggerType;
    protected float flaggerSensitivity = 1.0f;
    protected float flaggerSIRValue = 0.4f;
    protected final String[] polList; // polarizations or stokes
    protected final String[] flaggerList;
    protected final String fileName;
    protected final int maxSequenceNr;
    protected final int maxSubbands;

    protected DataProvider(final String fileName, final int maxSequenceNr, final int maxSubbands, final String[] polList, final String[] flaggerList) {
        this.fileName = fileName;
        this.maxSequenceNr = maxSequenceNr;
        this.maxSubbands = maxSubbands;
        this.polList = polList;
        this.flaggerList = flaggerList;
        this.flaggerType = flaggerList[0];
    }

    public abstract int getSizeX();

    public abstract int getSizeY();

    /**
     * @param x X-position in the data
     * @param y Y-position in the data
     * @param polarization the polarization, or, in case of beam formed data, the stoke
     * @return a value between 0 and 1
     */
    public abstract float getValue(int x, int y, int polarization);

    /**
     * @param x X-position in the data
     * @param y Y-position in the data
     * @param polarization the polarization, or, in case of beam formed data, the stoke
     * @return The original unscaled data value
     */
    public abstract float getRawValue(int x, int y, int polarization);

    /**
     * Flags are not kept per polarization, so we have one parameter less compared to the getValues for the data itself.
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
}

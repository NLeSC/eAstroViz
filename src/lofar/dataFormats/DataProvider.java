package lofar.dataFormats;

public abstract class DataProvider {
    protected String flaggerType;
    protected float flaggerSensitivity = 1.0f;
    protected final String[] FLAGGER_LIST;
    protected final String fileName;
    protected final int maxSequenceNr;

    protected DataProvider(final String fileName, final int maxSequenceNr, final String[] flaggerList) {
        this.fileName = fileName;
        this.maxSequenceNr = maxSequenceNr;
        this.FLAGGER_LIST = flaggerList;
        this.flaggerType = flaggerList[0];
    }

    public abstract int getSizeX();

    public abstract int getSizeY();

    public abstract float getValue(int x, int y); // Should return a value between 0 and 1

    public abstract float getRawValue(int x, int y); // Returns the original data

    public abstract boolean isFlagged(int x, int y);

    public abstract void flag();

    // null turns off flagging
    public void setFlagger(final String name) {
        if (name.equals(flaggerType)) {
            return;
        }

        for (final String element : FLAGGER_LIST) {
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
        return FLAGGER_LIST;
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

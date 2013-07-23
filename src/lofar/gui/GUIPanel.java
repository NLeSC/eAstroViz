package lofar.gui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import lofar.Viz;
import lofar.dataFormats.DataProvider;

public abstract class GUIPanel extends JPanel implements MouseMotionListener {
    public static final int LOW_SCALE = 90;
    public static final int START_SCALE = 99;
    public static final int HIGH_SCALE = 100;

    private static final long serialVersionUID = 1L;
    protected Viz viz;
    protected GUIFrame parentFrame;
    protected BufferedImage image;
    protected DataProvider data;
    protected float scale = START_SCALE;
    protected float percentileValLow;
    protected float percentileValHigh;
    
    private float[] rawData;
    private float[][] scaledData;

    protected int zoomX = 1;
    protected int zoomY = 1;

    /**
     * The current polarization or stoke.
     */
    protected int currentPol = 0;
    
    int COLOR_WHITE = colorToRGB(1.0f, 1.0f, 1.0f);
    ColorMapInterpreter colorMaps;
    ColorMap colorMap;
    String colorMapName = "default";

    GUIPanel(final Viz viz, final GUIFrame parentFrame, final DataProvider data) {
        this.viz = viz;
        this.parentFrame = parentFrame;
        colorMaps = new ColorMapInterpreter();
        colorMap = colorMaps.getColorMap(colorMapName);

        image = new BufferedImage(data.getSizeX(), data.getSizeY(), BufferedImage.TYPE_INT_RGB);
        setData(data);
        parentFrame.setPositionText(String.format("%06d, %06d", 0, 0));
        parentFrame.setRawValueText(String.format("%06.4e", 0.0f));
        parentFrame.setScaledValueText(String.format("%06f", 0.0f));
        addMouseMotionListener(this);
    }

    public boolean isFlagged(int x, int y) {
        return data.isFlagged(x, y);
    }

    public static float getScaleValue(int sliderValue) {
        return LOW_SCALE + ((float) sliderValue / (HIGH_SCALE - LOW_SCALE));
    }

    public static int getScaleSliderValue(float scale) {
        int res = Math.round((scale - LOW_SCALE) * (HIGH_SCALE - LOW_SCALE));
        if (res < 0) {
            res = 0;
        } else if (res > 100) {
            res = 100;
        }
        return res;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(data.getSizeX() * zoomX, data.getSizeY() * zoomY);
    }

    protected static final int colorToRGB(float red, float green, float blue) {
        if (red > 1.0f) {
            red = 1.0f;
        } else if (red < 0.0f) {
            red = 0.0f;
        }
        if (green > 1.0f) {
            green = 1.0f;
        } else if (green < 0.0f) {
            green = 0.0f;
        }
        if (blue > 1.0f) {
            blue = 1.0f;
        } else if (blue < 0.0f) {
            blue = 0.0f;
        }

        final int myRed = (int) (red * 255);
        final int myGreen = (int) (green * 255);
        final int myBlue = (int) (blue * 255);

        final int rgb = myRed << 16 | myGreen << 8 | myBlue;
        return rgb;
    }

    protected void setData(final DataProvider d) {
        this.data = d;

        rawData = new float[data.getSizeX() * data.getSizeY()]; // just in a 1D array, so we can sort it
        scaledData = new float[data.getSizeX()][data.getSizeY()];

        for (int y = 0; y < data.getSizeY(); y++) {
            for (int x = 0; x < data.getSizeX(); x++) {
                final float sampleVal;
                if (data.isFlagged(x, y)) {
                    sampleVal = 0.0f;
                } else {
                    sampleVal = data.getRawValue(x, y, currentPol);
                }

                rawData[data.getSizeX() * y + x] = sampleVal;
            }
        }

        Arrays.sort(rawData);
        generateImage();
    }

    protected float[][] getScaledData() {
        return scaledData;
    }

    // generate a new image, assuming the underlying data itself has not changed. If it has, call setData.
    protected void generateImage() {
        System.err.print("generate image...");

        long start = System.currentTimeMillis();

        int samplesFlagged = 0;

        final int lowIndex = (int) (((100.0f - scale) / 100.0f) * rawData.length);
        if (lowIndex < 0) {
            percentileValLow = rawData[0];
        } else {
            percentileValLow = rawData[lowIndex];
        }
        final int highIndex = (int) (scale * rawData.length / 100.0f);
        if (highIndex < rawData.length) {
            percentileValHigh = rawData[highIndex];
        } else {
            percentileValHigh = rawData[rawData.length - 1];
        }
        //        System.err.println("index of " + scale + "th percentile low = " + lowIndex + ", high = " + highIndex + ", low val = " + percentileValLow + ", high val = "+ percentileValHigh);

        final float scaleFactor = percentileValHigh - percentileValLow;

        for (int y = 0; y < data.getSizeY(); y++) {
            for (int x = 0; x < data.getSizeX(); x++) {
                if (!data.isFlagged(x, y)) {
                    float sampleVal = data.getRawValue(x, y, currentPol);
                    sampleVal = (sampleVal - percentileValLow) / scaleFactor;
                    if (sampleVal < 0.0f) {
                        sampleVal = 0.0f;
                    }
                    if (sampleVal > 1.0f) {
                        sampleVal = 1.0f;
                    }

                    scaledData[x][y] = sampleVal;

                    image.setRGB(x, data.getSizeY() - y - 1, colorMap.getColor(0.0f, 1.0f, sampleVal));
                } else {
                    samplesFlagged++;
                    image.setRGB(x, data.getSizeY() - y - 1, COLOR_WHITE);
                }
            }
        }

        final float percentClipped = (100.0f * samplesFlagged) / (data.getSizeX() * data.getSizeY());
        final String flaggedString = String.format("%10d (%10.2f %%)", samplesFlagged, percentClipped);
        parentFrame.setFlaggerStatisticsText(flaggedString);

        long end = System.currentTimeMillis();

        System.err.println("DONE in " + (end - start) + " ms.");
    }

    @Override
    public void paintComponent(final Graphics g) {
        super.paintComponent(g);
        if (zoomX == 1 && zoomY == 1) {
            g.drawImage(image, 0, 0, null);
        } else {
            g.drawImage(image.getScaledInstance(data.getSizeX() * zoomX, data.getSizeY() * zoomY, Image.SCALE_DEFAULT), 0, 0,
                    null);
        }
    }

    @Override
    public void mouseDragged(MouseEvent arg0) {
    }

    @Override
    public void mouseMoved(final MouseEvent e) {
        int x = e.getX() / zoomX;
        int y = data.getSizeY() - (e.getY() / zoomY) - 1;
        float raw = 0;
        float val = 0;

        if (x < 0 || x >= data.getSizeX()) {
            x = 0;
            y = 0;
        } else if (y < 0 || y >= data.getSizeY()) {
            x = 0;
            y = 0;
        } else {
            raw = data.getRawValue(x, y, currentPol);
            val = data.getValue(x, y, currentPol);
        }

        parentFrame.setPositionText(String.format("%06d, %06d", x, y));
        parentFrame.setRawValueText(String.format("%06.4e", raw));
        parentFrame.setScaledValueText(String.format("%06f", val));
    }

    public BufferedImage getImage() {
        return image;
    }

    public void save(final String fileName) {
        try {
            ImageIO.write(image, "bmp", new File(fileName));
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    public void setScale(final float scale) {
        if (this.scale == scale) {
            return;
        }
        this.scale = scale;
        generateImage();
    }

    public float getScale() {
        return scale;
    }

    public void setZoomX(int zoomX) {
        this.zoomX = zoomX;
        repaint();
    }

    public void setZoomY(int zoomY) {
        this.zoomY = zoomY;
        repaint();
    }

    public void setSensitivity(final float sensitivity) {
        if (data.getFlaggerSensitivity() == sensitivity || data.getFlagger().equals("none")) {
            return;
        }
        final long start = System.currentTimeMillis();
        data.setFlaggerSensitivity(sensitivity);
        final long end = System.currentTimeMillis();

        parentFrame.setStatusBarText("Flagging with " + data.getFlagger() + ", sensitivity " + data.getFlaggerSensitivity()
                + ", took " + (end - start) + " ms.");

        generateImage();
    }

    public float getSensitivity() {
        return data.getFlaggerSensitivity();
    }

    public String getFlagger() {
        return data.getFlagger();
    }

    public void setFlagger(final String flagger) {
        final long start = System.currentTimeMillis();
        data.setFlaggerSensitivity(parentFrame.getSensitivity());
        data.setFlagger(flagger);
        final long end = System.currentTimeMillis();

        if (!flagger.equals("none")) {
            parentFrame.setStatusBarText("Flagging with " + flagger + ", sensitivity " + data.getFlaggerSensitivity() + ", took "
                    + (end - start) + " ms.");
        }

        generateImage();
    }

    public void setColorMap(String map) {
        colorMapName = map;
        colorMap = colorMaps.getColorMap(colorMapName);
        generateImage();
    }

    public String getColorMapName() {
        return colorMapName;
    }

    public ColorMap getColorMap() {
        return colorMap;
    }

    public String[] getColorMapNames() {
        return colorMaps.getColorMaps();
    }
}

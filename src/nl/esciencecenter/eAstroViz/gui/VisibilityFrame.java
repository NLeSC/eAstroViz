/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * VizFrame.java
 *
 * Created on Aug 12, 2010, 1:37:35 PM
 */

package nl.esciencecenter.eAstroViz.gui;

import java.io.IOException;

import nl.esciencecenter.eAstroViz.Viz;
import nl.esciencecenter.eAstroViz.dataFormats.DataProvider;
import nl.esciencecenter.eAstroViz.dataFormats.visibilityData.VisibilityData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VisibilityFrame extends GUIFrame {
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(VisibilityFrame.class);

    private int pol;
    private int station1;
    private int station2;
    private final int nrStations;
    VisibilityData visibilityData;

    public VisibilityFrame(final Viz viz, final DataProvider data, final int pol) {
        super(data);
        this.visibilityData = (VisibilityData) data;
        this.nrStations = visibilityData.getNrStations();
        this.station1 = visibilityData.getStation1();
        this.station2 = visibilityData.getStation2();
        this.pol = pol;
        configureComponents();
    }

    private void configureComponents() {
        setStation1Spinner(station1);
        setStation2Spinner(station2);
        setBaselineText(getBaselineText());
        pack();
    }

    // If we change the baseline, we have to re-read the input data.
    void changeBaseline(final int station1, final int station2) {
        try {
            visibilityData = new VisibilityData(getData().getFileName(), station1, station2, pol, getData().getMaxSequenceNr(), getData().getMaxSubbands());
            setData(visibilityData); // also set in super class
            visibilityData.read();
            samplePanel.setData(visibilityData);
        } catch (final IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        configureComponents();
    }

    @Override
    protected GUIPanel createPanel() {
        return new VisibilityPanel(getData(), this);
    }

    public String setPolarization(final String s) {
        pol = getData().StringToPolarization(s);
        samplePanel.setPolarization(pol);
        return getData().polarizationToString(pol);
    }

    public int setStation1(final int value) {
        if (value == station1 || value < 0 || value >= nrStations || VisibilityData.baseline(value, station2) < 0) {
            return station1;
        }

        station1 = value;
        changeBaseline(station1, station2);
        repaint();
        return station1;
    }

    public int setStation2(final int value) {
        if (value == station2 || value < 0 || value >= nrStations || value > station1 || VisibilityData.baseline(station1, value) < 0) {
            return station2;
        }

        station2 = value;
        changeBaseline(station1, station2);
        repaint();
        return station2;
    }

    public int getStation1() {
        return station1;
    }

    public int getStation2() {
        return station2;
    }

    protected String getBaselineText() {
        String res = "Baseline: " + VisibilityData.baseline(station1, station2);
        if (station1 == station2) {
            res += " (auto correlation)";
        }
        return res;
    }
}

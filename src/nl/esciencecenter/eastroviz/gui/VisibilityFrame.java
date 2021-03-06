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
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * VizFrame.java
 *
 * Created on Aug 12, 2010, 1:37:35 PM
 */

package nl.esciencecenter.eastroviz.gui;

import java.io.IOException;

import nl.esciencecenter.eastroviz.Viz;
import nl.esciencecenter.eastroviz.dataformats.DataProvider;
import nl.esciencecenter.eastroviz.dataformats.visibility.VisibilityData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VisibilityFrame extends GUIFrame {
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(VisibilityFrame.class);

    private int pol;
    private int station1;
    private int station2;
    private final int nrStations;
    private VisibilityData visibilityData;

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
        LOGGER.debug("trying to set baseline to " + VisibilityData.baseline(station1, station2));
        try {
            visibilityData =
                    new VisibilityData(getData().getFileName(), station1, station2, pol, getData().getMaxSequenceNr(), getData()
                            .getMaxSubbands());
            setData(visibilityData); // also set in super class
            visibilityData.read();
            getSamplePanel().setData(visibilityData);
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

    @Override
    public String setPolarization(final String newPol) {
        pol = getData().StringToPolarization(newPol);
        getData().setPolarization(pol);
        getSamplePanel().setPolarization(pol);
        return getData().polarizationToString(pol);
    }

    @Override
    public int setPolarization(final int newPol) {
        getData().setPolarization(newPol);
        getSamplePanel().setPolarization(newPol);
        return getData().getPolarization();
    }

    @Override
    public int setStation1(final int value) {
        if (value == station1 || value < 0 || value >= nrStations || VisibilityData.baseline(value, station2) < 0) {
            return station1;
        }

        station1 = value;
        changeBaseline(station1, station2);
        repaint();
        return station1;
    }

    @Override
    public int setStation2(final int value) {
        if (value == station2 || value < 0 || value >= nrStations || value > station1
                || VisibilityData.baseline(station1, value) < 0) {
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

    @Override
    public String getBaselineText() {
        String res = "Baseline: " + VisibilityData.baseline(station1, station2);
        if (station1 == station2) {
            res += " (auto correlation)";
        }
        return res;
    }
}

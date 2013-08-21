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
package nl.esciencecenter.eastroviz.gui;

import nl.esciencecenter.eastroviz.dataformats.DataProvider;

@SuppressWarnings("serial")
public final class PreProcessedFrame extends GUIFrame {
    public PreProcessedFrame(final DataProvider data) {
        super(data);

        disableStation2Spinner();
    }

    @Override
    protected GUIPanel createPanel() {
        return new PreProcessedPanel(getData(), this);
    }

    @Override
    public int setStation1(int newVal) {
        int s = getSamplePanel().setStation1(newVal);
        repaint();
        return s;
    }

    @Override
    public int setStation2(int newVal) {
        return -1;
    }

    @Override
    public String getBaselineText() {
        return "N.A.";
    }

    @Override
    public String setPolarization(String newString) {
        int newPol = getData().StringToPolarization(newString);
        int result = getSamplePanel().setPolarization(newPol);
        repaint();
        return getData().polarizationToString(result);
    }

    @Override
    public int setPolarization(int newPol) {
        int result = getSamplePanel().setPolarization(newPol);
        repaint();
        return result;
    }
}

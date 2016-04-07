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

import javax.swing.JFrame;

import nl.esciencecenter.eastroviz.Viz;
import nl.esciencecenter.eastroviz.dataformats.raw.RawData;

@SuppressWarnings("serial")
public final class RawFrame extends JFrame {
    @SuppressWarnings("unused")
    private Viz viz;
    private RawPanel panel;

    public RawFrame(final Viz viz, final RawData rawData) {
        this.viz = viz;
        panel = new RawPanel(this, rawData);
        add(panel);
    }
}

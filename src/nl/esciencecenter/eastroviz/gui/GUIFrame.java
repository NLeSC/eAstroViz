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

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JPanel;

import nl.esciencecenter.eastroviz.dataformats.DataProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author rob
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public abstract class GUIFrame extends javax.swing.JFrame {
    private static final Logger LOGGER = LoggerFactory.getLogger(GUIFrame.class);
    private static final long serialVersionUID = 1L;
    private DataProvider data;
    private GUIPanel samplePanel;
    private String flaggerType = "none";
    private int[] histogram;

    public GUIFrame(final DataProvider data) {
        this.data = data;
        setTitle("eAstronomy visualizer");
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        initComponents();

        samplePanel = createPanel();
        scrollPane.setViewportView(samplePanel);

        flaggerTypeComboBox.setModel(new javax.swing.DefaultComboBoxModel(data.getFlaggerNames()));
        flaggerTypeComboBox.setSelectedItem("none");

        polarizationComboBox.setModel(new javax.swing.DefaultComboBoxModel(data.getPolarizationNames()));
        flaggerTypeComboBox.setSelectedItem(data.getPolarizationNames()[0]);

        sensitivityLabel.setText(String.format("%2.2f", sensitivitySlider.getValue() / 100.0f));

        scaleSlider.setMinimum(0);
        scaleSlider.setValue(GUIPanel.getScaleSliderValue(GUIPanel.START_SCALE));
        scaleSlider.setMaximum(100);
        scaleLabel.setText(String.format("%6.1f", samplePanel.getScale()));

        colorMapComboBox.setModel(new javax.swing.DefaultComboBoxModel(samplePanel.getColorMapNames()));
        colorMapComboBox.setSelectedItem("default");
    }

    abstract protected GUIPanel createPanel();

    private void drawHistogram() {
        int height = histogramInnerPanel.getHeight();
        int width = histogramInnerPanel.getWidth();
        histogram = new int[width];

        float[][] data = samplePanel.getScaledData();
        for (int y = 0; y < data.length; y++) {
            for (int x = 0; x < data[0].length; x++) {
                if (!samplePanel.isFlagged(y, x)) {
                    int histogramPos = (int) (data[y][x] * (histogram.length - 1));
                    if (histogramPos > histogram.length - 1) {
                        histogramPos = histogram.length - 1;
                    }
                    histogram[histogramPos]++;
                }
            }
        }

        // calc scale
        int max = -1;
        for (int element : histogram) {
            if (element > max) {
                max = element;
            }
        }

        BufferedImage histogramImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int background = histogramInnerPanel.getBackground().getRGB();

        for (int i = 0; i < histogram.length; i++) {
            int lineHeight = (int) (((float) histogram[i] / max) * height);
            for (int y = 0; y < height - lineHeight; y++) {
                histogramImage.setRGB(i, y, background);
            }
            for (int y = height - lineHeight; y < height; y++) {
                histogramImage.setRGB(i, y, 0);
            }
        }

        histogramInnerPanel.getGraphics().drawImage(histogramImage, 0, 0, null);
    }

    private void drawLegend() {
        int width = colorMapLegendPanel.getWidth();
        int height = colorMapLegendPanel.getHeight();
        BufferedImage legendImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int x = 0; x < width; x++) {
            int color = samplePanel.getColorMap().getColor(0, 1, (float) x / width);

            for (int y = 0; y < height; y++) {
                legendImage.setRGB(x, y, color);
            }
        }

        colorMapLegendPanel.getGraphics().drawImage(legendImage, 0, 0, null);
    }

    protected float getSensitivity() {
        return sensitivitySlider.getValue() / 100.0f;
    }

    private void drawLogo() {
        BufferedImage img = null;
        try {
            img = ImageIO.read(new File("dist/NLeSC-logo-small.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (img == null) {
            LOGGER.warn("image is null!");
            return;
        }

        // Image scaled = img.getScaledInstance(logoPanel.getWidth()-20, -1, Image.SCALE_SMOOTH);

        logoPanel.getGraphics().drawImage(img, 10, 2, null);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        drawHistogram();
        drawLegend();
        drawLogo();
    }

    // override in subclass if needed.
    protected JPanel createAdditionalControlsPanel() {
        return new JPanel();
    }

    public int[] getHistogram() {
        return histogram;
    }

    public String getFlaggerType() {
        return flaggerType;
    }

    public JPanel getHistogramPanel() {
        return histogramInnerPanel;
    }

    public JPanel getLegendPanel() {
        return colorMapLegendPanel;
    }

    public void setFlaggerType(final String flaggerType) {
        if (flaggerType.equals(this.flaggerType)) {
            return;
        }
        this.flaggerType = flaggerType;
        samplePanel.setFlagger(flaggerType);
    }

    protected void setPanel(final GUIPanel panel) {
        this.samplePanel = panel;
    }

    public void setStatusBarText(final String s) {
        statusBarLabel.setText(s);
    }

    public void setPositionText(final String s) {
        positionLabel.setText(s);
    }

    public void setRawValueText(final String s) {
        rawValueLabel.setText(s);
    }

    public void setScaledValueText(final String s) {
        scaledValueLabel.setText(s);
    }

    public void setFlaggerStatisticsText(final String s) {
        flaggedLabel.setText(s);
    }

    public void save(final String fileName) {
        samplePanel.save(fileName);
    }

    public DataProvider getData() {
        return data;
    }

    public void setData(DataProvider data) {
        this.data = data;
    }

    public abstract int setStation1(int newVal);

    public abstract int setStation2(int newVal);

    public abstract String getBaselineText();

    public abstract String setPolarization(String s);

    public abstract int setPolarization(int pol);

    public void disableStation2Spinner() {
        station2Label.setEnabled(false);
        station2Spinner.setEnabled(false);
    }

    public int getStation1Spinner() {
        return ((Integer) station1Spinner.getValue()).intValue();
    }

    public void setStation1Spinner(int val) {
        station1Spinner.setValue(Integer.valueOf(val));
    }

    public int getStation2Spinner() {
        return ((Integer) station2Spinner.getValue()).intValue();
    }

    public void setStation2Spinner(int val) {
        station2Spinner.setValue(Integer.valueOf(val));
    }

    public void setBaselineText(String val) {
        baselineLabel.setText(val);
    }

    protected GUIPanel getSamplePanel() {
        return samplePanel;
    }

    protected void setSamplePanel(GUIPanel samplePanel) {
        this.samplePanel = samplePanel;
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of
     * this method is always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        leftPanel = new javax.swing.JPanel();
        standardControlsPanel = new javax.swing.JPanel();
        logoPanel = new javax.swing.JPanel();
        scalingPanel = new javax.swing.JPanel();
        scaleSlider = new javax.swing.JSlider();
        label0 = new javax.swing.JLabel();
        scaleLabel = new javax.swing.JLabel();
        colorMapPanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        colorMapComboBox = new javax.swing.JComboBox();
        colorMapLegendPanel = new javax.swing.JPanel();
        histogramPanel = new javax.swing.JPanel();
        histogramInnerPanel = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        infoPanel = new javax.swing.JPanel();
        label1 = new javax.swing.JLabel();
        label2 = new javax.swing.JLabel();
        label3 = new javax.swing.JLabel();
        label4 = new javax.swing.JLabel();
        positionLabel = new javax.swing.JLabel();
        rawValueLabel = new javax.swing.JLabel();
        scaledValueLabel = new javax.swing.JLabel();
        zoomPanel = new javax.swing.JPanel();
        zoomXSlider = new javax.swing.JSlider();
        zoomXLabel = new javax.swing.JLabel();
        zoomYLabel = new javax.swing.JLabel();
        zoomYSlider = new javax.swing.JSlider();
        flaggerPanel = new javax.swing.JPanel();
        flaggerTypeComboBox = new javax.swing.JComboBox();
        label5 = new javax.swing.JLabel();
        label6 = new javax.swing.JLabel();
        sensitivitySlider = new javax.swing.JSlider();
        label7 = new javax.swing.JLabel();
        flaggedLabel = new javax.swing.JLabel();
        sensitivityLabel = new javax.swing.JLabel();
        stationPanel = new javax.swing.JPanel();
        station1Spinner = new javax.swing.JSpinner();
        station1Label = new javax.swing.JLabel();
        station2Label = new javax.swing.JLabel();
        station2Spinner = new javax.swing.JSpinner();
        baselineLabel = new javax.swing.JLabel();
        polarizationLabel = new javax.swing.JLabel();
        polarizationComboBox = new javax.swing.JComboBox();
        StatusBar = new javax.swing.JPanel();
        statusBarLabel = new javax.swing.JLabel();
        scrollPane = new javax.swing.JScrollPane();
        jMenuBar1 = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        saveMenuItem = new javax.swing.JMenuItem();
        exitMenuItem = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        standardControlsPanel.setOpaque(false);

        logoPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        javax.swing.GroupLayout logoPanelLayout = new javax.swing.GroupLayout(logoPanel);
        logoPanel.setLayout(logoPanelLayout);
        logoPanelLayout.setHorizontalGroup(logoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(
                0, 0, Short.MAX_VALUE));
        logoPanelLayout.setVerticalGroup(logoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0,
                21, Short.MAX_VALUE));

        scalingPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        scaleSlider.setMinimum(1);
        scaleSlider.setValue(90);
        scaleSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            @Override
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                scaleSliderscaleStateChanged(evt);
            }
        });

        label0.setText("scale factor: ");

        scaleLabel.setText("jLabel1");

        javax.swing.GroupLayout scalingPanelLayout = new javax.swing.GroupLayout(scalingPanel);
        scalingPanel.setLayout(scalingPanelLayout);
        scalingPanelLayout.setHorizontalGroup(scalingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(
                        scalingPanelLayout
                                .createSequentialGroup()
                                .addContainerGap()
                                .addGroup(
                                        scalingPanelLayout
                                                .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                .addGroup(
                                                        scalingPanelLayout.createSequentialGroup().addComponent(label0)
                                                                .addGap(18, 18, 18).addComponent(scaleLabel))
                                                .addComponent(scaleSlider, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                        javax.swing.GroupLayout.DEFAULT_SIZE,
                                                        javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
        scalingPanelLayout.setVerticalGroup(scalingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(
                        scalingPanelLayout
                                .createSequentialGroup()
                                .addGap(14, 14, 14)
                                .addGroup(
                                        scalingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                .addComponent(label0).addComponent(scaleLabel))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(scaleSlider, javax.swing.GroupLayout.PREFERRED_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        colorMapPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        jLabel1.setText("Color map:");

        colorMapComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        colorMapComboBox.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comorMapSelected(evt);
            }
        });

        javax.swing.GroupLayout colorMapLegendPanelLayout = new javax.swing.GroupLayout(colorMapLegendPanel);
        colorMapLegendPanel.setLayout(colorMapLegendPanelLayout);
        colorMapLegendPanelLayout.setHorizontalGroup(colorMapLegendPanelLayout.createParallelGroup(
                javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 0, Short.MAX_VALUE));
        colorMapLegendPanelLayout.setVerticalGroup(colorMapLegendPanelLayout.createParallelGroup(
                javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 20, Short.MAX_VALUE));

        javax.swing.GroupLayout colorMapPanelLayout = new javax.swing.GroupLayout(colorMapPanel);
        colorMapPanel.setLayout(colorMapPanelLayout);
        colorMapPanelLayout
                .setHorizontalGroup(colorMapPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(
                                colorMapPanelLayout
                                        .createSequentialGroup()
                                        .addContainerGap()
                                        .addGroup(
                                                colorMapPanelLayout
                                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addGroup(
                                                                colorMapPanelLayout
                                                                        .createSequentialGroup()
                                                                        .addComponent(jLabel1)
                                                                        .addGap(4, 4, 4)
                                                                        .addComponent(colorMapComboBox,
                                                                                javax.swing.GroupLayout.PREFERRED_SIZE,
                                                                                javax.swing.GroupLayout.DEFAULT_SIZE,
                                                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                        .addGap(0, 0, Short.MAX_VALUE))
                                                        .addComponent(colorMapLegendPanel, javax.swing.GroupLayout.DEFAULT_SIZE,
                                                                javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                        .addContainerGap()));
        colorMapPanelLayout.setVerticalGroup(colorMapPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(
                        colorMapPanelLayout
                                .createSequentialGroup()
                                .addContainerGap()
                                .addGroup(
                                        colorMapPanelLayout
                                                .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                .addComponent(jLabel1)
                                                .addComponent(colorMapComboBox, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                        javax.swing.GroupLayout.DEFAULT_SIZE,
                                                        javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(colorMapLegendPanel, javax.swing.GroupLayout.PREFERRED_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        histogramPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        histogramPanel.setOpaque(false);

        javax.swing.GroupLayout histogramInnerPanelLayout = new javax.swing.GroupLayout(histogramInnerPanel);
        histogramInnerPanel.setLayout(histogramInnerPanelLayout);
        histogramInnerPanelLayout.setHorizontalGroup(histogramInnerPanelLayout.createParallelGroup(
                javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 0, Short.MAX_VALUE));
        histogramInnerPanelLayout.setVerticalGroup(histogramInnerPanelLayout.createParallelGroup(
                javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 110, Short.MAX_VALUE));

        jLabel2.setText("Histogram:");

        javax.swing.GroupLayout histogramPanelLayout = new javax.swing.GroupLayout(histogramPanel);
        histogramPanel.setLayout(histogramPanelLayout);
        histogramPanelLayout.setHorizontalGroup(histogramPanelLayout.createParallelGroup(
                javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                histogramPanelLayout
                        .createSequentialGroup()
                        .addContainerGap()
                        .addGroup(
                                histogramPanelLayout
                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(histogramInnerPanel, javax.swing.GroupLayout.DEFAULT_SIZE,
                                                javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addGroup(
                                                histogramPanelLayout.createSequentialGroup().addComponent(jLabel2)
                                                        .addGap(0, 0, Short.MAX_VALUE))).addContainerGap()));
        histogramPanelLayout.setVerticalGroup(histogramPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(
                        javax.swing.GroupLayout.Alignment.TRAILING,
                        histogramPanelLayout
                                .createSequentialGroup()
                                .addContainerGap()
                                .addComponent(jLabel2)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(histogramInnerPanel, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE).addContainerGap()));

        infoPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        label1.setText("current sample:");

        label2.setText("position: ");

        label3.setText("raw value:");

        label4.setText("scaled value:");

        positionLabel.setText("jLabel3");

        rawValueLabel.setText("jLabel4");

        scaledValueLabel.setText("jLabel5");

        javax.swing.GroupLayout infoPanelLayout = new javax.swing.GroupLayout(infoPanel);
        infoPanel.setLayout(infoPanelLayout);
        infoPanelLayout
                .setHorizontalGroup(infoPanelLayout
                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(
                                infoPanelLayout
                                        .createSequentialGroup()
                                        .addGroup(
                                                infoPanelLayout
                                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addGroup(
                                                                infoPanelLayout
                                                                        .createSequentialGroup()
                                                                        .addGroup(
                                                                                infoPanelLayout
                                                                                        .createParallelGroup(
                                                                                                javax.swing.GroupLayout.Alignment.TRAILING)
                                                                                        .addComponent(
                                                                                                label3,
                                                                                                javax.swing.GroupLayout.PREFERRED_SIZE,
                                                                                                100,
                                                                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                                        .addGroup(
                                                                                                infoPanelLayout
                                                                                                        .createParallelGroup(
                                                                                                                javax.swing.GroupLayout.Alignment.LEADING)
                                                                                                        .addGroup(
                                                                                                                infoPanelLayout
                                                                                                                        .createSequentialGroup()
                                                                                                                        .addGap(24,
                                                                                                                                24,
                                                                                                                                24)
                                                                                                                        .addComponent(
                                                                                                                                label4,
                                                                                                                                javax.swing.GroupLayout.PREFERRED_SIZE,
                                                                                                                                100,
                                                                                                                                javax.swing.GroupLayout.PREFERRED_SIZE))
                                                                                                        .addGroup(
                                                                                                                javax.swing.GroupLayout.Alignment.TRAILING,
                                                                                                                infoPanelLayout
                                                                                                                        .createSequentialGroup()
                                                                                                                        .addContainerGap()
                                                                                                                        .addComponent(
                                                                                                                                label2,
                                                                                                                                javax.swing.GroupLayout.PREFERRED_SIZE,
                                                                                                                                100,
                                                                                                                                javax.swing.GroupLayout.PREFERRED_SIZE))))
                                                                        .addPreferredGap(
                                                                                javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                                        .addGroup(
                                                                                infoPanelLayout
                                                                                        .createParallelGroup(
                                                                                                javax.swing.GroupLayout.Alignment.LEADING)
                                                                                        .addComponent(positionLabel)
                                                                                        .addComponent(rawValueLabel)
                                                                                        .addComponent(scaledValueLabel)))
                                                        .addGroup(
                                                                infoPanelLayout
                                                                        .createSequentialGroup()
                                                                        .addContainerGap()
                                                                        .addComponent(label1,
                                                                                javax.swing.GroupLayout.PREFERRED_SIZE, 166,
                                                                                javax.swing.GroupLayout.PREFERRED_SIZE)))
                                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
        infoPanelLayout.setVerticalGroup(infoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                infoPanelLayout
                        .createSequentialGroup()
                        .addContainerGap()
                        .addComponent(label1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(
                                infoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(label2).addComponent(positionLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(
                                infoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(label3).addComponent(rawValueLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(
                                infoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(label4).addComponent(scaledValueLabel))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        zoomPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        zoomXSlider.setMaximum(10);
        zoomXSlider.setMinimum(1);
        zoomXSlider.setValue(1);
        zoomXSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            @Override
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                zoomXSliderStateChanged(evt);
            }
        });

        zoomXLabel.setText("zoom x");

        zoomYLabel.setText("zoom y");

        zoomYSlider.setMaximum(10);
        zoomYSlider.setMinimum(1);
        zoomYSlider.setValue(1);
        zoomYSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            @Override
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                zoomYSliderStateChanged(evt);
            }
        });

        javax.swing.GroupLayout zoomPanelLayout = new javax.swing.GroupLayout(zoomPanel);
        zoomPanel.setLayout(zoomPanelLayout);
        zoomPanelLayout.setHorizontalGroup(zoomPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(
                        zoomPanelLayout
                                .createSequentialGroup()
                                .addContainerGap()
                                .addComponent(zoomXLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(zoomXSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 60,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(zoomYLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(zoomYSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 60,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
        zoomPanelLayout.setVerticalGroup(zoomPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                zoomPanelLayout
                        .createSequentialGroup()
                        .addGap(13, 13, 13)
                        .addGroup(
                                zoomPanelLayout
                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(zoomXLabel)
                                        .addComponent(zoomYSlider, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(zoomXSlider, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(zoomYLabel))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        flaggerPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        flaggerTypeComboBox
                .setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        flaggerTypeComboBox.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                flaggerTypeSelected(evt);
            }
        });

        label5.setText("flagger: ");

        label6.setText("sensitivity: ");

        sensitivitySlider.setMaximum(500);
        sensitivitySlider.setMinimum(1);
        sensitivitySlider.setToolTipText("");
        sensitivitySlider.setValue(100);
        sensitivitySlider.addChangeListener(new javax.swing.event.ChangeListener() {
            @Override
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                sensitivitySliderStateChanged(evt);
            }
        });

        label7.setText("flagged: ");

        flaggedLabel.setText("jLabel6");

        sensitivityLabel.setText("jLabel1");

        javax.swing.GroupLayout flaggerPanelLayout = new javax.swing.GroupLayout(flaggerPanel);
        flaggerPanel.setLayout(flaggerPanelLayout);
        flaggerPanelLayout.setHorizontalGroup(flaggerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(
                        flaggerPanelLayout
                                .createSequentialGroup()
                                .addContainerGap()
                                .addGroup(
                                        flaggerPanelLayout
                                                .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                .addComponent(label5)
                                                .addComponent(sensitivitySlider, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                        javax.swing.GroupLayout.DEFAULT_SIZE,
                                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addGroup(
                                                        flaggerPanelLayout
                                                                .createSequentialGroup()
                                                                .addComponent(label6)
                                                                .addPreferredGap(
                                                                        javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                                .addComponent(sensitivityLabel))
                                                .addComponent(flaggerTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                        javax.swing.GroupLayout.DEFAULT_SIZE,
                                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addGroup(
                                                        flaggerPanelLayout.createSequentialGroup().addComponent(label7)
                                                                .addGap(26, 26, 26).addComponent(flaggedLabel)))
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
        flaggerPanelLayout.setVerticalGroup(flaggerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(
                        flaggerPanelLayout
                                .createSequentialGroup()
                                .addContainerGap()
                                .addComponent(label5)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(flaggerTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addGroup(
                                        flaggerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                .addComponent(label6).addComponent(sensitivityLabel))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(sensitivitySlider, javax.swing.GroupLayout.PREFERRED_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addGroup(
                                        flaggerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                .addComponent(label7).addComponent(flaggedLabel))
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        stationPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        station1Spinner.addChangeListener(new javax.swing.event.ChangeListener() {
            @Override
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                station1SpinnerStateChanged(evt);
            }
        });

        station1Label.setText("station 1:");

        station2Label.setText("station 2:");

        station2Spinner.addChangeListener(new javax.swing.event.ChangeListener() {
            @Override
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                station2SpinnerStateChanged(evt);
            }
        });

        baselineLabel.setText("baseline: ");

        polarizationLabel.setText("Polarization:");

        polarizationComboBox.setModel(new javax.swing.DefaultComboBoxModel(
                new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        polarizationComboBox.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                polarizationComboBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout stationPanelLayout = new javax.swing.GroupLayout(stationPanel);
        stationPanel.setLayout(stationPanelLayout);
        stationPanelLayout
                .setHorizontalGroup(stationPanelLayout
                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(
                                stationPanelLayout
                                        .createSequentialGroup()
                                        .addContainerGap()
                                        .addGroup(
                                                stationPanelLayout
                                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addComponent(baselineLabel)
                                                        .addGroup(
                                                                stationPanelLayout
                                                                        .createSequentialGroup()
                                                                        .addGroup(
                                                                                stationPanelLayout
                                                                                        .createParallelGroup(
                                                                                                javax.swing.GroupLayout.Alignment.LEADING)
                                                                                        .addComponent(
                                                                                                station1Label,
                                                                                                javax.swing.GroupLayout.PREFERRED_SIZE,
                                                                                                80,
                                                                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                                        .addComponent(polarizationLabel))
                                                                        .addPreferredGap(
                                                                                javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                                        .addGroup(
                                                                                stationPanelLayout
                                                                                        .createParallelGroup(
                                                                                                javax.swing.GroupLayout.Alignment.LEADING)
                                                                                        .addComponent(
                                                                                                polarizationComboBox,
                                                                                                javax.swing.GroupLayout.PREFERRED_SIZE,
                                                                                                javax.swing.GroupLayout.DEFAULT_SIZE,
                                                                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                                        .addGroup(
                                                                                                stationPanelLayout
                                                                                                        .createSequentialGroup()
                                                                                                        .addComponent(
                                                                                                                station1Spinner,
                                                                                                                javax.swing.GroupLayout.PREFERRED_SIZE,
                                                                                                                42,
                                                                                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                                                        .addPreferredGap(
                                                                                                                javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                                                                        .addComponent(
                                                                                                                station2Label)
                                                                                                        .addPreferredGap(
                                                                                                                javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                                                                        .addComponent(
                                                                                                                station2Spinner,
                                                                                                                javax.swing.GroupLayout.PREFERRED_SIZE,
                                                                                                                42,
                                                                                                                javax.swing.GroupLayout.PREFERRED_SIZE)))))
                                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
        stationPanelLayout.setVerticalGroup(stationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(
                        stationPanelLayout
                                .createSequentialGroup()
                                .addContainerGap()
                                .addGroup(
                                        stationPanelLayout
                                                .createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                .addComponent(station1Label)
                                                .addComponent(station2Label)
                                                .addComponent(station2Spinner, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                        javax.swing.GroupLayout.DEFAULT_SIZE,
                                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addComponent(station1Spinner, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                        javax.swing.GroupLayout.DEFAULT_SIZE,
                                                        javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(baselineLabel)
                                .addGap(18, 18, 18)
                                .addGroup(
                                        stationPanelLayout
                                                .createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                .addComponent(polarizationLabel)
                                                .addComponent(polarizationComboBox, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                        javax.swing.GroupLayout.DEFAULT_SIZE,
                                                        javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        javax.swing.GroupLayout standardControlsPanelLayout = new javax.swing.GroupLayout(standardControlsPanel);
        standardControlsPanel.setLayout(standardControlsPanelLayout);
        standardControlsPanelLayout.setHorizontalGroup(standardControlsPanelLayout
                .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(histogramPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                        Short.MAX_VALUE)
                .addComponent(scalingPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                        Short.MAX_VALUE)
                .addComponent(colorMapPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                        Short.MAX_VALUE)
                .addComponent(infoPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                        Short.MAX_VALUE)
                .addComponent(logoPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                        Short.MAX_VALUE)
                .addComponent(flaggerPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                        Short.MAX_VALUE)
                .addComponent(zoomPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                        Short.MAX_VALUE)
                .addComponent(stationPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                        Short.MAX_VALUE));
        standardControlsPanelLayout.setVerticalGroup(standardControlsPanelLayout.createParallelGroup(
                javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                standardControlsPanelLayout
                        .createSequentialGroup()
                        .addComponent(logoPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(stationPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 105,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(zoomPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(scalingPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(colorMapPanel, javax.swing.GroupLayout.PREFERRED_SIZE,
                                javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(histogramPanel, javax.swing.GroupLayout.PREFERRED_SIZE,
                                javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(infoPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(flaggerPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        javax.swing.GroupLayout leftPanelLayout = new javax.swing.GroupLayout(leftPanel);
        leftPanel.setLayout(leftPanelLayout);
        leftPanelLayout.setHorizontalGroup(leftPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(standardControlsPanel, javax.swing.GroupLayout.PREFERRED_SIZE,
                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE));
        leftPanelLayout.setVerticalGroup(leftPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(standardControlsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                        Short.MAX_VALUE));

        StatusBar.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        statusBarLabel.setText("Dataset loaded");

        javax.swing.GroupLayout StatusBarLayout = new javax.swing.GroupLayout(StatusBar);
        StatusBar.setLayout(StatusBarLayout);
        StatusBarLayout.setHorizontalGroup(StatusBarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(
                        StatusBarLayout
                                .createSequentialGroup()
                                .addComponent(statusBarLabel, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE).addGap(0, 0, Short.MAX_VALUE)));
        StatusBarLayout.setVerticalGroup(StatusBarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                javax.swing.GroupLayout.Alignment.TRAILING,
                StatusBarLayout.createSequentialGroup().addGap(0, 0, Short.MAX_VALUE).addComponent(statusBarLabel)));

        scrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        scrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        fileMenu.setText("File");

        saveMenuItem.setText("save as image");
        saveMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(saveMenuItem);

        exitMenuItem.setText("exit");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(exitMenuItem);

        jMenuBar1.add(fileMenu);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(layout
                .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(StatusBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                        Short.MAX_VALUE)
                .addGroup(
                        layout.createSequentialGroup()
                                .addComponent(leftPanel, javax.swing.GroupLayout.PREFERRED_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(scrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 800, Short.MAX_VALUE)));
        layout.setVerticalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                javax.swing.GroupLayout.Alignment.TRAILING,
                layout.createSequentialGroup()
                        .addGroup(
                                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(leftPanel, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(scrollPane))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(StatusBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                                javax.swing.GroupLayout.PREFERRED_SIZE)));

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void saveMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveMenuItemActionPerformed
        JFileChooser chooser = new JFileChooser();
        int returnVal = chooser.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            String name = chooser.getSelectedFile().getPath();
            save(name);
        }
    }//GEN-LAST:event_saveMenuItemActionPerformed

    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        System.exit(0);
    }//GEN-LAST:event_exitMenuItemActionPerformed

    private void sensitivitySliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_sensitivitySliderStateChanged
        final float newSensitivity = sensitivitySlider.getValue() / 100.0f;
        if (Math.abs(newSensitivity - samplePanel.getSensitivity()) > .0000001) {
            samplePanel.setSensitivity(newSensitivity);
            sensitivityLabel.setText(String.format("%2.2f", newSensitivity));
            repaint();
        }
    }//GEN-LAST:event_sensitivitySliderStateChanged

    private void flaggerTypeSelected(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flaggerTypeSelected
        final String newFlaggerType = (String) flaggerTypeComboBox.getSelectedItem();
        if (!newFlaggerType.equals(samplePanel.getFlagger())) {
            final float newSensitivity = sensitivitySlider.getValue() / 100.0f;
            if (Math.abs(newSensitivity - samplePanel.getSensitivity()) > .0000001) {
                samplePanel.setSensitivity(newSensitivity);
            }

            samplePanel.setFlagger(newFlaggerType);
            repaint();
        }
    }//GEN-LAST:event_flaggerTypeSelected

    private void zoomYSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_zoomYSliderStateChanged
        final int newZoomY = zoomYSlider.getValue();
        samplePanel.setZoomY(newZoomY);
    }//GEN-LAST:event_zoomYSliderStateChanged

    private void zoomXSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_zoomXSliderStateChanged
        final int newZoomX = zoomXSlider.getValue();
        samplePanel.setZoomX(newZoomX);
    }//GEN-LAST:event_zoomXSliderStateChanged

    private void comorMapSelected(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comorMapSelected
        final String newColorMap = (String) colorMapComboBox.getSelectedItem();
        if (!newColorMap.equals(samplePanel.getColorMapName())) {
            samplePanel.setColorMap(newColorMap);
            repaint();
        }
    }//GEN-LAST:event_comorMapSelected

    private void scaleSliderscaleStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_scaleSliderscaleStateChanged
        final float newScale = GUIPanel.getScaleValue(scaleSlider.getValue());
        if (newScale != samplePanel.getScale()) {
            samplePanel.setScale(newScale);
            scaleLabel.setText(String.format("%6.1f", samplePanel.getScale()));
            repaint();
        }
    }//GEN-LAST:event_scaleSliderscaleStateChanged

    private void station1SpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_station1SpinnerStateChanged
        final int val = ((Integer) station1Spinner.getValue()).intValue();
        final int newVal = setStation1(val);
        baselineLabel.setText(getBaselineText());
        station1Spinner.setValue(newVal);
        repaint();
    }//GEN-LAST:event_station1SpinnerStateChanged

    private void station2SpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_station2SpinnerStateChanged
        final int val = ((Integer) station2Spinner.getValue()).intValue();
        final int newVal = setStation2(val);
        baselineLabel.setText(getBaselineText());
        station2Spinner.setValue(newVal);
        repaint();
    }//GEN-LAST:event_station2SpinnerStateChanged

    private void polarizationComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_polarizationComboBoxActionPerformed
        setPolarization((String) polarizationComboBox.getSelectedItem());
    }//GEN-LAST:event_polarizationComboBoxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel StatusBar;
    private javax.swing.JLabel baselineLabel;
    private javax.swing.JComboBox colorMapComboBox;
    private javax.swing.JPanel colorMapLegendPanel;
    private javax.swing.JPanel colorMapPanel;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JLabel flaggedLabel;
    private javax.swing.JPanel flaggerPanel;
    private javax.swing.JComboBox flaggerTypeComboBox;
    private javax.swing.JPanel histogramInnerPanel;
    private javax.swing.JPanel histogramPanel;
    private javax.swing.JPanel infoPanel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JLabel label0;
    private javax.swing.JLabel label1;
    private javax.swing.JLabel label2;
    private javax.swing.JLabel label3;
    private javax.swing.JLabel label4;
    private javax.swing.JLabel label5;
    private javax.swing.JLabel label6;
    private javax.swing.JLabel label7;
    private javax.swing.JPanel leftPanel;
    private javax.swing.JPanel logoPanel;
    private javax.swing.JComboBox polarizationComboBox;
    private javax.swing.JLabel polarizationLabel;
    private javax.swing.JLabel positionLabel;
    private javax.swing.JLabel rawValueLabel;
    private javax.swing.JMenuItem saveMenuItem;
    private javax.swing.JLabel scaleLabel;
    private javax.swing.JSlider scaleSlider;
    private javax.swing.JLabel scaledValueLabel;
    private javax.swing.JPanel scalingPanel;
    private javax.swing.JScrollPane scrollPane;
    private javax.swing.JLabel sensitivityLabel;
    private javax.swing.JSlider sensitivitySlider;
    private javax.swing.JPanel standardControlsPanel;
    private javax.swing.JLabel station1Label;
    private javax.swing.JSpinner station1Spinner;
    private javax.swing.JLabel station2Label;
    private javax.swing.JSpinner station2Spinner;
    private javax.swing.JPanel stationPanel;
    private javax.swing.JLabel statusBarLabel;
    private javax.swing.JPanel zoomPanel;
    private javax.swing.JLabel zoomXLabel;
    private javax.swing.JSlider zoomXSlider;
    private javax.swing.JLabel zoomYLabel;
    private javax.swing.JSlider zoomYSlider;

    // End of variables declaration//GEN-END:variables

}

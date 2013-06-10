/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * VizFrame.java
 *
 * Created on Aug 12, 2010, 1:37:35 PM
 */

package lofar.gui;

import java.io.IOException;

import lofar.Viz;
import lofar.dataFormats.DataProvider;
import lofar.dataFormats.visabilityData.VisibilityData;

public final class VisibilityFrame extends GUIFrame {
    private static final long serialVersionUID = 1L;

    private int pol;
    private int station1;
    private int station2;
    private final int nrStations;
    VisibilityData visibilityData;

    public VisibilityFrame(final Viz viz, final DataProvider data, final int pol) {
        super(viz, data);
        this.visibilityData = (VisibilityData) data;
        this.nrStations = visibilityData.getNrStations();
        this.station1 = visibilityData.getStation1();
        this.station2 = visibilityData.getStation2();
        this.pol = pol;
        initComponents();
        configureComponents();
    }

    private void configureComponents() {
        station1Spinner.setValue(station1);
        station2Spinner.setValue(station2);
        baselineLabel.setText(getBaselineText());

        switch (pol) {
        case 0:
            xxButton.setSelected(true);
            break;
        case 1:
            xyButton.setSelected(true);
            break;
        case 2:
            yxButton.setSelected(true);
            break;
        case 3:
            yyButton.setSelected(true);
            break;
        default:
            System.err.println("error, illegal pol: " + pol);
            new Exception().printStackTrace();
        }

        /* for now, we can only draw the power
                switch(inputType) {
                realButton.setSelected(true);
                complexButton.setSelected(true);
                amplitudeButton.setSelected(true);
                phaseButton.setSelected(true);
                uvplotButton.setSelected(true);
        */

        pack();
    }

    // If we change the baseline, we have to re-read the input data.
    void changeBaseline(final int station1, final int station2) {
        try {
            visibilityData = new VisibilityData(data.getFileName(), station1, station2, data.getMaxSequenceNr(), data.getMaxSubbands());
            data = visibilityData; // also set in super class
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
        return new VisibilityPanel(viz, data, this);
    }

    public static String polarizationToString(final int pol) {
        switch (pol) {
        case 0:
            return "xx";
        case 1:
            return "xy";
        case 2:
            return "yx";
        case 3:
            return "yy";
        default:
            return "error";
        }
    }

    public static int StringToPolarization(final String polString) {
        if (polString.equals("XX")) {
            return 0;
        } else if (polString.equals("XY")) {
            return 1;
        } else if (polString.equals("YX")) {
            return 2;
        } else if (polString.equals("YY")) {
            return 3;
        } else {
            System.err.println("illegal polarization: " + polString);
            return 0;
        }
    }

    public void setPolarization(final String s) {
        pol = StringToPolarization(s);
        repaint();
    }

    public int setStation1(final int value) {
        if (value == station1 || value < 0 || value >= nrStations || Viz.baseline(value, station2) < 0) {
            return station1;
        }

        station1 = value;
        changeBaseline(station1, station2);
        return station1;
    }

    public int setStation2(final int value) {
        if (value == station2 || value < 0 || value >= nrStations || value > station1 || Viz.baseline(station1, value) < 0) {
            return station2;
        }

        station2 = value;
        changeBaseline(station1, station2);
        return station2;
    }

    public int getStation1() {
        return station1;
    }

    public int getStation2() {
        return station2;
    }

    public int getCurrentPol() {
        return pol;
    }

    String getBaselineText() {
        String res = "Baseline: " + Viz.baseline(station1, station2);
        if (station1 == station2) {
            res += " (auto correlation)";
        }
        return res;
    }

    private void initComponents() {
        polarizationButtonGroup = new javax.swing.ButtonGroup();
        inputTypeButtonGroup = new javax.swing.ButtonGroup();
        inputPanel = new javax.swing.JPanel();
        polarizationPanel = new javax.swing.JPanel();
        polarizationLable = new javax.swing.JLabel();
        xxButton = new javax.swing.JRadioButton();
        xyButton = new javax.swing.JRadioButton();
        yxButton = new javax.swing.JRadioButton();
        yyButton = new javax.swing.JRadioButton();
        inputTypePanel = new javax.swing.JPanel();
        inputTypeLabel = new javax.swing.JLabel();
        realButton = new javax.swing.JRadioButton();
        complexButton = new javax.swing.JRadioButton();
        amplitudeButton = new javax.swing.JRadioButton();
        phaseButton = new javax.swing.JRadioButton();
        uvplotButton = new javax.swing.JRadioButton();
        stationPanel = new javax.swing.JPanel();
        station1Spinner = new javax.swing.JSpinner();
        station1Label = new javax.swing.JLabel();
        station2Label = new javax.swing.JLabel();
        station2Spinner = new javax.swing.JSpinner();
        baselineLabel = new javax.swing.JLabel();
        polarizationPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        polarizationPanel.setMaximumSize(new java.awt.Dimension(256, 145));

        polarizationLable.setText("Please select a polarization");

        polarizationButtonGroup.add(xxButton);
        xxButton.setText("XX");
        xxButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                polarizationActionPerformed(evt);
            }
        });

        polarizationButtonGroup.add(xyButton);
        xyButton.setText("XY");
        xyButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                polarizationActionPerformed(evt);
            }
        });

        polarizationButtonGroup.add(yxButton);
        yxButton.setText("YX");
        yxButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                polarizationActionPerformed(evt);
            }
        });

        polarizationButtonGroup.add(yyButton);
        yyButton.setText("YY");
        yyButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                polarizationActionPerformed(evt);
            }
        });

        final javax.swing.GroupLayout polarizationPanelLayout = new javax.swing.GroupLayout(polarizationPanel);
        polarizationPanel.setLayout(polarizationPanelLayout);
        polarizationPanelLayout.setHorizontalGroup(polarizationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                polarizationPanelLayout
                        .createSequentialGroup()
                        .addContainerGap()
                        .addGroup(
                                polarizationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addComponent(polarizationLable)
                                        .addComponent(xxButton).addComponent(xyButton).addComponent(yxButton).addComponent(yyButton))
                        .addContainerGap(86, Short.MAX_VALUE)));
        polarizationPanelLayout.setVerticalGroup(polarizationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                polarizationPanelLayout.createSequentialGroup().addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(polarizationLable).addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(xxButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(xyButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(yxButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(yyButton)));

        inputTypePanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        inputTypeLabel.setText("Please select an input type");

        inputTypeButtonGroup.add(realButton);
        realButton.setText("real part");
        realButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                inputTypeActionPerformed(evt);
            }
        });

        inputTypeButtonGroup.add(complexButton);
        complexButton.setText("imaginary part");
        complexButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                inputTypeActionPerformed(evt);
            }
        });

        inputTypeButtonGroup.add(amplitudeButton);
        amplitudeButton.setText("amplitude");
        amplitudeButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                inputTypeActionPerformed(evt);
            }
        });

        inputTypeButtonGroup.add(phaseButton);
        phaseButton.setText("phase");
        phaseButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                inputTypeActionPerformed(evt);
            }
        });

        inputTypeButtonGroup.add(uvplotButton);
        uvplotButton.setText("uv plot");
        uvplotButton.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(final java.awt.event.ActionEvent evt) {
                inputTypeActionPerformed(evt);
            }
        });

        final javax.swing.GroupLayout inputTypePanelLayout = new javax.swing.GroupLayout(inputTypePanel);
        inputTypePanel.setLayout(inputTypePanelLayout);
        inputTypePanelLayout.setHorizontalGroup(inputTypePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                inputTypePanelLayout
                        .createSequentialGroup()
                        .addContainerGap()
                        .addGroup(
                                inputTypePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addComponent(inputTypeLabel)
                                        .addComponent(realButton).addComponent(complexButton).addComponent(amplitudeButton).addComponent(phaseButton)
                                        .addComponent(uvplotButton)).addContainerGap(88, Short.MAX_VALUE)));
        inputTypePanelLayout.setVerticalGroup(inputTypePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                inputTypePanelLayout.createSequentialGroup().addContainerGap().addComponent(inputTypeLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(realButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(complexButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(amplitudeButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(phaseButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(uvplotButton).addContainerGap(17, Short.MAX_VALUE)));

        stationPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        station1Spinner.addChangeListener(new javax.swing.event.ChangeListener() {
            @Override
            public void stateChanged(final javax.swing.event.ChangeEvent evt) {
                station1SpinnerStateChanged(evt);
            }
        });

        station1Label.setText("Station 1:");

        station2Label.setText("Station 2:");

        station2Spinner.addChangeListener(new javax.swing.event.ChangeListener() {
            @Override
            public void stateChanged(final javax.swing.event.ChangeEvent evt) {
                station2SpinnerStateChanged(evt);
            }
        });

        baselineLabel.setText("Baseline: ");

        final javax.swing.GroupLayout stationPanelLayout = new javax.swing.GroupLayout(stationPanel);
        stationPanel.setLayout(stationPanelLayout);
        stationPanelLayout.setHorizontalGroup(stationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                stationPanelLayout
                        .createSequentialGroup()
                        .addContainerGap()
                        .addGroup(
                                stationPanelLayout
                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(
                                                stationPanelLayout
                                                        .createSequentialGroup()
                                                        .addGroup(
                                                                stationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                                                        .addComponent(station2Label).addComponent(station1Label))
                                                        .addGap(18, 18, 18)
                                                        .addGroup(
                                                                stationPanelLayout
                                                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                                        .addComponent(station1Spinner, javax.swing.GroupLayout.PREFERRED_SIZE, 54,
                                                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                        .addComponent(station2Spinner, javax.swing.GroupLayout.PREFERRED_SIZE, 54,
                                                                                javax.swing.GroupLayout.PREFERRED_SIZE))).addComponent(baselineLabel))
                        .addContainerGap(126, Short.MAX_VALUE)));
        stationPanelLayout.setVerticalGroup(stationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                stationPanelLayout
                        .createSequentialGroup()
                        .addContainerGap()
                        .addGroup(
                                stationPanelLayout
                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(station1Spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                                                javax.swing.GroupLayout.PREFERRED_SIZE).addComponent(station1Label))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(
                                stationPanelLayout
                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(station2Spinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                                                javax.swing.GroupLayout.PREFERRED_SIZE).addComponent(station2Label))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED).addComponent(baselineLabel)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        final javax.swing.GroupLayout inputPanelLayout = new javax.swing.GroupLayout(inputPanel);
        inputPanel.setLayout(inputPanelLayout);
        inputPanelLayout.setHorizontalGroup(inputPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                javax.swing.GroupLayout.Alignment.TRAILING,
                inputPanelLayout
                        .createSequentialGroup()
                        .addContainerGap()
                        .addGroup(
                                inputPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                                        inputPanelLayout
                                                .createSequentialGroup()
                                                .addGroup(
                                                        inputPanelLayout
                                                                .createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                                                .addComponent(stationPanel, javax.swing.GroupLayout.Alignment.LEADING,
                                                                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                                                                        Short.MAX_VALUE)
                                                                .addComponent(polarizationPanel, javax.swing.GroupLayout.Alignment.LEADING,
                                                                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                                                                        Short.MAX_VALUE)
                                                                .addComponent(inputTypePanel, javax.swing.GroupLayout.Alignment.LEADING,
                                                                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                                                                        Short.MAX_VALUE)).addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)))
                        .addContainerGap()));
        inputPanelLayout.setVerticalGroup(inputPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                inputPanelLayout
                        .createSequentialGroup()
                        .addGroup(
                                inputPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING).addGroup(
                                        inputPanelLayout
                                                .createSequentialGroup()
                                                .addComponent(polarizationPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(inputTypePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                                                        Short.MAX_VALUE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(stationPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)));

        final javax.swing.GroupLayout layout = new javax.swing.GroupLayout(additionalControlsPanel);
        additionalControlsPanel.setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(inputPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                                javax.swing.GroupLayout.PREFERRED_SIZE).addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
        layout.setVerticalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(inputPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                                javax.swing.GroupLayout.PREFERRED_SIZE).addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

    }

    private void polarizationActionPerformed(final java.awt.event.ActionEvent evt) {
        setPolarization(evt.getActionCommand());
        repaint();
    }

    private void inputTypeActionPerformed(final java.awt.event.ActionEvent evt) {
        //        viz.setInputType(evt.getActionCommand());
        repaint();
    }

    private void station1SpinnerStateChanged(final javax.swing.event.ChangeEvent evt) {
        final int val = ((Integer) station1Spinner.getValue()).intValue();
        final int newVal = setStation1(val);
        baselineLabel.setText(getBaselineText());
        station1Spinner.setValue(newVal);
        repaint();
    }

    private void station2SpinnerStateChanged(final javax.swing.event.ChangeEvent evt) {
        final int val = ((Integer) station2Spinner.getValue()).intValue();
        final int newVal = setStation2(val);
        baselineLabel.setText(getBaselineText());
        station2Spinner.setValue(newVal);
        repaint();
    }

    private javax.swing.JRadioButton amplitudeButton;
    private javax.swing.JLabel baselineLabel;
    private javax.swing.JRadioButton complexButton;
    private javax.swing.JPanel inputPanel;
    private javax.swing.ButtonGroup inputTypeButtonGroup;
    private javax.swing.JLabel inputTypeLabel;
    private javax.swing.JPanel inputTypePanel;
    private javax.swing.JRadioButton phaseButton;
    private javax.swing.ButtonGroup polarizationButtonGroup;
    private javax.swing.JLabel polarizationLable;
    private javax.swing.JPanel polarizationPanel;
    private javax.swing.JRadioButton realButton;
    private javax.swing.JLabel station1Label;
    private javax.swing.JSpinner station1Spinner;
    private javax.swing.JLabel station2Label;
    private javax.swing.JSpinner station2Spinner;
    private javax.swing.JPanel stationPanel;
    private javax.swing.JRadioButton uvplotButton;
    private javax.swing.JRadioButton xxButton;
    private javax.swing.JRadioButton xyButton;
    private javax.swing.JRadioButton yxButton;
    private javax.swing.JRadioButton yyButton;
}

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package com.projects.gmm.assabil;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.chart.ChartUtils;
import weka.clusterers.EM;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.*;
import java.util.List;

public class GMMAssabil {

    private static final int MAX_CLUSTERS = 25;
    private static final double TRUCK_CAPACITY_5_TONNES = 5000.0;

    public static void main(String[] args) {
        try {
            // Read data from Excel file
            String excelFilePath = "C:\\Users\\Chaffaa Anass\\Documents\\Projects\\Optimisation des Livrasions\\data.xlsx";
            FileInputStream fileInputStream = new FileInputStream(excelFilePath);
            Workbook workbook = new XSSFWorkbook(fileInputStream);
            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = sheet.getLastRowNum();

            // Prepare data
            ArrayList<Attribute> attributes = new ArrayList<>(3);
            attributes.add(new Attribute("Latitude"));
            attributes.add(new Attribute("Longitude"));
            attributes.add(new Attribute("Weight"));

            Instances data = new Instances("ClientData", attributes, rowCount);
            data.setClassIndex(-1);

            List<String> partnerNames = new ArrayList<>();
            List<Double> weights = new ArrayList<>();

            // Add data instances
            for (int i = 1; i <= rowCount; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue; // Skip empty rows
                }
                Cell partnerNameCell = row.getCell(1);
                Cell latitudeCell = row.getCell(2);
                Cell longitudeCell = row.getCell(3);
                Cell weightCell = row.getCell(5);

                if (partnerNameCell == null || latitudeCell == null || longitudeCell == null || weightCell == null) {
                    continue; // Skip rows with missing cells
                }

                String partnerName = partnerNameCell.getStringCellValue();
                double latitude = latitudeCell.getNumericCellValue();
                double longitude = longitudeCell.getNumericCellValue();
                double weight = weightCell.getNumericCellValue();

                if (latitude >= 31.0 && latitude <= 34.0) {
                    Instance inst = new weka.core.DenseInstance(1.0, new double[]{latitude, longitude, weight});
                    data.add(inst);
                    partnerNames.add(partnerName);
                    weights.add(weight);
                }
            }

            workbook.close();
            fileInputStream.close();

            // Create and configure GMM
            EM gmm = new EM();
            gmm.setNumClusters(Math.min(MAX_CLUSTERS, data.numInstances())); // Ensure clusters do not exceed number of rows

            // Train the model
            gmm.buildClusterer(data);

            // Prepare cluster information
            XYSeriesCollection dataset = new XYSeriesCollection();
            XYSeries[] series = new XYSeries[Math.min(MAX_CLUSTERS, data.numInstances())];
            Map<Integer, List<String>> clusterToDataMap = new HashMap<>();
            double[] clusterWeights = new double[Math.min(MAX_CLUSTERS, data.numInstances())];
            List<UnassignedInstance> unassignedInstancesList = new ArrayList<>();

            // Initialize clusters
            for (int i = 0; i < series.length; i++) {
                series[i] = new XYSeries("Cluster " + (i + 1));
                clusterToDataMap.put(i, new ArrayList<>());
                clusterWeights[i] = 0.0;
            }

            // Assign instances to clusters
            for (int i = 0; i < data.numInstances(); i++) {
                Instance inst = data.instance(i);
                double weight = inst.value(2);
                double latitude = inst.value(0);
                double longitude = inst.value(1);

                int cluster = gmm.clusterInstance(inst);
                double newClusterWeight = clusterWeights[cluster] + weight;

                if (newClusterWeight <= TRUCK_CAPACITY_5_TONNES) {
                    series[cluster].add(latitude, longitude);
                    clusterToDataMap.get(cluster).add(String.format("Partner: %s, Latitude: %.6f, Longitude: %.6f, Weight: %.2f Kg", partnerNames.get(i), latitude, longitude, weight));
                    clusterWeights[cluster] = newClusterWeight;
                } else {
                    unassignedInstancesList.add(new UnassignedInstance(partnerNames.get(i), latitude, longitude, weight));
                }
            }

            // Process unassigned instances
            for (UnassignedInstance ui : unassignedInstancesList) {
                double weight = ui.getWeight();
                double latitude = ui.getLatitude();
                double longitude = ui.getLongitude();
                String partnerName = ui.getPartnerName();

                double minDistance = Double.MAX_VALUE;
                int bestCluster = -1;

                // Find the nearest cluster that can accommodate the weight
                for (int i = 0; i < clusterWeights.length; i++) {
                    if (clusterWeights[i] + weight <= TRUCK_CAPACITY_5_TONNES) {
                        double clusterLatitudeSum = 0.0;
                        double clusterLongitudeSum = 0.0;
                        int clusterSize = series[i].getItemCount();

                        for (int j = 0; j < clusterSize; j++) {
                            clusterLatitudeSum += series[i].getX(j).doubleValue();
                            clusterLongitudeSum += series[i].getY(j).doubleValue();
                        }

                        double centroidLatitude = clusterLatitudeSum / clusterSize;
                        double centroidLongitude = clusterLongitudeSum / clusterSize;
                        double distance = Math.sqrt(Math.pow(latitude - centroidLatitude, 2) + Math.pow(longitude - centroidLongitude, 2));

                        if (distance < minDistance) {
                            minDistance = distance;
                            bestCluster = i;
                        }
                    }
                }

                // Assign to the best cluster found
                if (bestCluster != -1) {
                    series[bestCluster].add(latitude, longitude);
                    clusterToDataMap.get(bestCluster).add(String.format("Partner: %s, Latitude: %.6f, Longitude: %.6f, Weight: %.2f Kg", partnerName, latitude, longitude, weight));
                    clusterWeights[bestCluster] += weight;
                } else {
                    System.out.println("Instance with weight " + weight + " could not be assigned to any cluster.");
                }
            }

            for (XYSeries s : series) {
                dataset.addSeries(s);
            }

            JFreeChart scatterPlot = ChartFactory.createScatterPlot(
                    "GMM Clustering",
                    "Latitude",
                    "Longitude",
                    dataset,
                    PlotOrientation.VERTICAL,
                    true,
                    true,
                    false
            );

            // Customize tooltips
            XYPlot plot = scatterPlot.getXYPlot();
            XYItemRenderer renderer = plot.getRenderer();
            renderer.setDefaultToolTipGenerator((dataset1, series1, item) -> {
                String partnerData = clusterToDataMap.get(series1).get(item);
                return partnerData;
            });

            // Create buttons (download Excel file, download graph)
            JButton downloadExcelButton = new JButton("Download Excel");
            JButton saveImageButton = new JButton("Save Graph");

            downloadExcelButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        FileOutputStream fileOut = new FileOutputStream("cluster_data.xlsx");
                        Workbook workbookOut = new XSSFWorkbook();
                        Sheet sheetOut = workbookOut.createSheet("Clusters");

                        int rowIndex = 0;
                        Row row;
                        Cell cell;

                        // Add headers
                        row = sheetOut.createRow(rowIndex++);
                        cell = row.createCell(0);
                        cell.setCellValue("Partner");
                        cell = row.createCell(1);
                        cell.setCellValue("Latitude");
                        cell = row.createCell(2);
                        cell.setCellValue("Longitude");
                        cell = row.createCell(3);
                        cell.setCellValue("Weight");

                        // Iterate through clusters
                        for (int i = 0; i < clusterToDataMap.size(); i++) {
                            row = sheetOut.createRow(rowIndex++);
                            cell = row.createCell(0);
                            cell.setCellValue("Cluster " + (i + 1));

                            row = sheetOut.createRow(rowIndex++);
                            cell = row.createCell(0);
                            cell.setCellValue("Total Weight:");
                            cell = row.createCell(1);
                            cell.setCellValue(clusterWeights[i]);

                            for (String info : clusterToDataMap.get(i)) {
                                row = sheetOut.createRow(rowIndex++);
                                String[] parts = info.split(", ");

                                row.createCell(0).setCellValue(parts[0].replace("Partner: ", "")); // Partner
                                row.createCell(1).setCellValue(parts[1].replace("Latitude: ", "")); // Latitude
                                row.createCell(2).setCellValue(parts[2].replace("Longitude: ", "")); // Longitude
                                row.createCell(3).setCellValue(parts[3].replace("Weight: ", "").replace(" Kg", "")); // Weight
                            }

                            rowIndex++;
                        }

                        // Adjust column widths
                        for (int i = 0; i < 4; i++) {
                            sheetOut.autoSizeColumn(i);
                        }

                        workbookOut.write(fileOut);
                        fileOut.close();
                        workbookOut.close();

                        JOptionPane.showMessageDialog(null, "Excel file created successfully!");

                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
            });

            saveImageButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        File imageFile = new File("cluster_graph.png");
                        ChartUtils.saveChartAsPNG(imageFile, scatterPlot, 800, 600);
                        JOptionPane.showMessageDialog(null, "Graph saved successfully!");
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            });

            // Setup the main frame
            JFrame frame = new JFrame("Cluster Visualization");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            // Add the chart to the center
            frame.add(new ChartPanel(scatterPlot), BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel();
            buttonPanel.add(downloadExcelButton);
            buttonPanel.add(saveImageButton);
            frame.add(buttonPanel, BorderLayout.SOUTH);

            frame.pack();
            frame.setVisible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


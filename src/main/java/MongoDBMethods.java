import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * MongoDB methods to be integrated into FREDChartDisplayApp
 * This file contains the MongoDB-related methods that will be added to the main application
 */
public class MongoDBMethods {
    
    // These methods should be copied into FREDChartDisplayApp as private methods
    
    /*
    private void initializeMongoDB() {
        try {
            mongoService = new FREDMongoService();
            appendOutput("\n" + mongoService.getServiceStatus() + "\n");
        } catch (Exception e) {
            appendOutput("\n✗ Failed to initialize MongoDB: " + e.getMessage() + "\n");
            mongoService = null;
        }
    }

    private void showMongoStatus() {
        if (mongoService == null) {
            JOptionPane.showMessageDialog(this,
                    "MongoDB service not initialized.",
                    "MongoDB Status", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String status = mongoService.getServiceStatus();
        String[] seriesIds = mongoService.getAllSeriesIds().toArray(new String[0]);
        
        StringBuilder message = new StringBuilder();
        message.append(status).append("\n\n");
        if (seriesIds.length > 0) {
            message.append("Available series in database:\n");
            for (String id : seriesIds) {
                message.append("• ").append(id).append("\n");
            }
        } else {
            message.append("No series found in database.");
        }

        JOptionPane.showMessageDialog(this,
                message.toString(),
                "MongoDB Status", JOptionPane.INFORMATION_MESSAGE);
    }

    private void loadFromMongoDB() {
        if (mongoService == null || !mongoService.isServiceAvailable()) {
            JOptionPane.showMessageDialog(this,
                    "MongoDB service not available.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<String> seriesIds = mongoService.getAllSeriesIds();
        if (seriesIds.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No data found in MongoDB database.",
                    "Load Data", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(this, "Load from MongoDB", true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);

        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBackground(CARD_COLOR);
        main.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        main.add(new JLabel("Select series to load:") {{
            setFont(new Font("Segoe UI", Font.BOLD, 14));
            setForeground(TEXT_PRIMARY);
        }}, BorderLayout.NORTH);

        JList<String> list = new JList<>(seriesIds.toArray(new String[0]));
        list.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane scroll = new JScrollPane(list);

        JPanel btnPanel = new JPanel(new FlowLayout());
        JButton loadBtn = createStyledButton("Load", PRIMARY_COLOR, PRIMARY_DARK);
        JButton cancelBtn = createStyledButton("Cancel", TEXT_SECONDARY, TEXT_PRIMARY);
        cancelBtn.setBackground(TEXT_SECONDARY);

        loadBtn.addActionListener(e -> {
            List<String> selected = list.getSelectedValuesList();
            if (selected.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        "Please select at least one series.",
                        "Selection Required", JOptionPane.WARNING_MESSAGE);
                return;
            }

            for (String seriesId : selected) {
                FREDDataDocument doc = mongoService.getFREDData(seriesId);
                if (doc != null) {
                    appendOutput("\n✓ Loaded from MongoDB: " + seriesId + "\n");
                    // Convert to FREDDataSet and cache it
                    // Note: You'll need to implement conversion logic
                }
            }
            dialog.dispose();
        });

        cancelBtn.addActionListener(e -> dialog.dispose());

        btnPanel.add(cancelBtn);
        btnPanel.add(loadBtn);

        main.add(scroll, BorderLayout.CENTER);
        main.add(btnPanel, BorderLayout.SOUTH);
        dialog.add(main);
        dialog.setVisible(true);
    }

    private void clearMongoDB() {
        if (mongoService == null || !mongoService.isServiceAvailable()) {
            JOptionPane.showMessageDialog(this,
                    "MongoDB service not available.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to clear all FRED data from MongoDB?\nThis action cannot be undone.",
                "Clear Database",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            if (mongoService.clearAllData()) {
                appendOutput("\n✓ MongoDB database cleared successfully\n");
                JOptionPane.showMessageDialog(this,
                        "Database cleared successfully.",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
            } else {
                appendOutput("\n✗ Failed to clear MongoDB database\n");
                JOptionPane.showMessageDialog(this,
                        "Failed to clear database.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    */
}

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Java Swing Application for fetching, saving, displaying, and managing
 * time series economic data from the FRED API.
 * NO EXTERNAL DEPENDENCIES - Pure Java implementation with built-in JSON parsing and charting.
 */
public class FREDChartDisplayApp extends JFrame {

    /* ----------  L O O K   &   F E E L  ---------- */
    private static final Color PRIMARY_COLOR       = new Color(33, 150, 243);
    private static final Color PRIMARY_DARK        = new Color(25, 118, 210);
    private static final Color BACKGROUND_COLOR    = new Color(250, 250, 250);
    private static final Color CARD_COLOR          = Color.WHITE;
    private static final Color TEXT_PRIMARY        = new Color(33, 33, 33);
    private static final Color TEXT_SECONDARY      = new Color(117, 117, 117);
    private static final Color CHART_LINE_COLOR    = new Color(33, 150, 243);
    private static Color CHART_GRID_COLOR    = new Color(224, 224, 224);

    private static final Color[] CHART_COLORS = {
            new Color(33, 150, 243),   // Blue
            new Color(244, 67, 54),    // Red
            new Color(76, 175, 80),    // Green
            new Color(255, 152, 0),    // Orange
            new Color(156, 39, 176)    // Purple
    };

    /* ====== LIGHT (default) ====== */
    private static Color  BG_LIGHT   = new Color(250,250,250);
    private static Color  CARD_LIGHT = Color.WHITE;
    private static Color  TXT_LIGHT  = new Color(33,33,33);
    private static Color  GRID_LIGHT = new Color(224,224,224);

    /* ====== DARK ====== */
    private static Color  BG_DARK    = new Color(30,30,30);
    private static Color  CARD_DARK  = new Color(45,45,45);
    private static Color  TXT_DARK   = new Color(220,220,220);
    private static Color  GRID_DARK  = new Color(70,70,70);

    /* current palette */
    private Color bgColor   = BG_LIGHT;
    private Color cardColor = CARD_LIGHT;
    private Color txtColor  = TXT_LIGHT;
    private Color gridColor = GRID_LIGHT;

    /* ----------  S T A T E  ---------- */
    private boolean hideConsole = false;                // default: console visible
    private boolean hideChart   = false;                // default: chart visible
    private boolean darkMode = false;   // light by default
    private String  apiKey      = null;
    private JTextArea  outputArea;
    private JLabel     statusLabel;
    private JPanel chartWrapper;
    private ChartPanel chartPanel;
    private Map<String, FREDDataSet> cachedDataSets = new HashMap<>();
    private List<String> activeSeriesIds = new ArrayList<>();
    private FREDMongoService mongoService;

    private static final File THEME_FILE =
            new File(System.getProperty("user.home"), ".fred_theme");

    private void loadTheme() {
        if (!THEME_FILE.exists()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(THEME_FILE))) {
            darkMode = "dark".equals(r.readLine());
        } catch (IOException ignore) {}
    }

    private void saveTheme() {
        try (PrintWriter w = new PrintWriter(THEME_FILE)) {
            w.println(darkMode ? "dark" : "light");
        } catch (IOException ignore) {}
    }

    /* ===================================================================================
                                          C O N S T R U C T O R
       =================================================================================== */
    public FREDChartDisplayApp() {
        super("FRED Chart Display Application");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 700);
        setLocationRelativeTo(null);

        /* look-and-feel */
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignore) { }

        if (!promptForApiKey()) System.exit(0);

        /* Initialize MongoDB */
        initializeMongoDB();

        setIconImage(blankIcon());   // <-- no Java logo
        setVisible(true);

        initComponents();
        loadTheme();          // read saved preference
        if (darkMode) toggleDarkMode();     // apply once (will flip colours if darkMode==true)
        setVisible(true);
    }

    /* ===================================================================================
                                        G U I   B U I L D
       =================================================================================== */
    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        mainPanel.setBackground(BACKGROUND_COLOR);

        setJMenuBar(createStyledMenuBar());

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.6);
        splitPane.setBorder(null);
        splitPane.setDividerSize(5);

        /* left – chart */
        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBackground(BACKGROUND_COLOR);
        leftPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 10));

        JLabel chartLabel = new JLabel("Chart View");
        chartLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        chartLabel.setForeground(TEXT_PRIMARY);
        chartLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 10, 0));

        chartPanel = new ChartPanel();
        chartPanel.setBackground(CARD_COLOR);
        chartPanel.setBorder(new LineBorder(new Color(224, 224, 224), 1));

        leftPanel.add(chartLabel, BorderLayout.NORTH);
        leftPanel.add(chartPanel, BorderLayout.CENTER);
        chartWrapper = leftPanel;   // store the wrapper that already holds label + chart

        /* right – console */
        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setBackground(BACKGROUND_COLOR);
        rightPanel.setBorder(BorderFactory.createEmptyBorder(15, 10, 15, 15));

        JLabel outputLabel = new JLabel("Output Console");
        outputLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        outputLabel.setForeground(TEXT_PRIMARY);
        outputLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 10, 0));

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFocusable(false);   // <-- add this
        outputArea.setWrapStyleWord(true);
        outputArea.setLineWrap(true);
        outputArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        outputArea.setBackground(CARD_COLOR);
        outputArea.setForeground(TEXT_PRIMARY);
        outputArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        outputArea.setText("Welcome to FRED Chart Display Application\n" +
                "Use the menu to fetch FRED data.\n\n" +
                "API Key Status: " + (apiKey != null ? "✓ Configured" : "✗ Not configured"));

        JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setBorder(new LineBorder(new Color(224, 224, 224), 1));

        rightPanel.add(outputLabel, BorderLayout.NORTH);
        rightPanel.add(scrollPane, BorderLayout.CENTER);

        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(rightPanel);

        /* status bar */
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(new Color(245, 245, 245));
        statusPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(224, 224, 224)),
                BorderFactory.createEmptyBorder(8, 15, 8, 15)));

        statusLabel = new JLabel("Data Structures and Algorithms: 210");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(TEXT_SECONDARY);
        statusPanel.add(statusLabel, BorderLayout.WEST);

        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(statusPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    /* ===================================================================================
                                        M E N U   B A R
       =================================================================================== */
    private JMenuBar createStyledMenuBar() {
        JMenuBar bar = new JMenuBar();

        /* -------- File -------- */
        JMenu file = createStyledMenu("File");

        /* NEW IMPORT ITEMS */
        JMenuItem importCsv = createStyledMenuItem("Import CSV");
        importCsv.addActionListener(e -> importCsv());

        JMenuItem importJson = createStyledMenuItem("Import JSON");
        importJson.addActionListener(e -> importJson());

        JMenuItem exit = createStyledMenuItem("Exit");
        exit.addActionListener(e -> System.exit(0));

        file.add(importCsv);
        file.add(importJson);
        file.add(exit);

        bar.add(file);

        /* -------- Data -------- */
        JMenu data = createStyledMenu("Data");
        JMenuItem fetch = createStyledMenuItem("Fetch FRED Data");
        fetch.addActionListener(e -> fetchFREDData());
        data.add(fetch);

        JMenuItem manage = createStyledMenuItem("Manage Comparison Series");
        manage.addActionListener(e -> showManageSeriesDialog());
        data.add(manage);

        JMenuItem export = createStyledMenuItem("Export Graph");
        export.addActionListener(e -> exportGraph());
        data.add(export);
        bar.add(data);

        /* -------- Settings -------- */
        JMenu settings = createStyledMenu("Settings");
        JMenuItem apiKeyItem = createStyledMenuItem("Change API Key");
        apiKeyItem.addActionListener(e -> promptForApiKey());
        settings.add(apiKeyItem);

        JCheckBoxMenuItem toggleConsole = new JCheckBoxMenuItem("Hide Console", hideConsole);
        toggleConsole.addActionListener(ev -> {
            hideConsole = !hideConsole;
            toggleConsole.setSelected(hideConsole);
            toggleConsoleVisibility();
        });

        JCheckBoxMenuItem toggleChart = new JCheckBoxMenuItem("Hide Chart", hideChart);
        toggleChart.addActionListener(ev -> {
            hideChart = !hideChart;
            toggleChart.setSelected(hideChart);
            toggleChartVisibility();   // <-- new method
        });

        settings.add(toggleChart);
        settings.add(toggleConsole);

        JCheckBoxMenuItem darkItem = new JCheckBoxMenuItem("Dark Mode", darkMode);
        darkItem.addActionListener(ev -> toggleDarkMode());
        settings.add(darkItem);

        bar.add(settings);

        /* -------- MongoDB -------- */
        JMenu mongo = createStyledMenu("MongoDB");
        JMenuItem mongoStatus = createStyledMenuItem("Connection Status");
        mongoStatus.addActionListener(e -> showMongoStatus());
        mongo.add(mongoStatus);
        
        JMenuItem mongoLoad = createStyledMenuItem("Load from Database");
        mongoLoad.addActionListener(e -> loadFromMongoDB());
        mongo.add(mongoLoad);
        
        JMenuItem mongoClear = createStyledMenuItem("Clear Database");
        mongoClear.addActionListener(e -> clearMongoDB());
        mongo.add(mongoClear);
        
        bar.add(mongo);

        return bar;
    }

    private JMenu createStyledMenu(String text) {
        JMenu m = new JMenu(text);
        m.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        m.setForeground(TEXT_PRIMARY);
        return m;
    }

    private JMenuItem createStyledMenuItem(String text) {
        JMenuItem i = new JMenuItem(text);
        i.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        return i;
    }

    /* ===================================================================================
                                        A P I   K E Y   D I A L O G
       =================================================================================== */
    private boolean promptForApiKey() {
        /* ---- content ---- */
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBackground(CARD_COLOR);
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("Enter your FRED API Key");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(TEXT_PRIMARY);

        JTextArea info = new JTextArea(
                "You can obtain a free API key from:\n" +
                        "https://fred.stlouisfed.org/docs/api/api_key.html ");
        info.setEditable(false);
        info.setFocusable(false);
        info.setBackground(CARD_COLOR);
        info.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        info.setForeground(TEXT_SECONDARY);
        info.setLineWrap(true);
        info.setWrapStyleWord(true);

        /* ---- password field ONLY (no button here) ---- */
        JLabel keyLabel = new JLabel("API Key:");
        keyLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        keyLabel.setForeground(TEXT_PRIMARY);

        JPasswordField keyField = new JPasswordField(30);
        keyField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        if (apiKey != null) keyField.setText(apiKey);

        JPanel keyRow = new JPanel(new BorderLayout(5, 0));
        keyRow.setBackground(CARD_COLOR);
        keyRow.add(keyLabel, BorderLayout.WEST);
        keyRow.add(keyField, BorderLayout.CENTER);   // <-- only field, no button

        p.add(title, BorderLayout.NORTH);
        p.add(info, BorderLayout.CENTER);
        p.add(keyRow, BorderLayout.SOUTH);

        /* ---- bottom button row: left = Get Key, right = Cancel + OK ---- */
        JButton okBtn     = createStyledButton("OK", PRIMARY_COLOR, PRIMARY_DARK);
        JButton cancelBtn = createStyledButton("Cancel", TEXT_SECONDARY, TEXT_PRIMARY);
        cancelBtn.setBackground(TEXT_SECONDARY);

        JButton getKeyBtn = createStyledButton("Get API Key", TEXT_SECONDARY, TEXT_PRIMARY);
        getKeyBtn.setBackground(TEXT_SECONDARY);
        getKeyBtn.addActionListener(e -> {
            try { Desktop.getDesktop().browse(new URI("https://fred.stlouisfed.org/docs/api/api_key.html")); }
            catch (Exception ex) { ex.printStackTrace(); }
        });

        /* one row: left = Get Key, right = Cancel + OK */
        JPanel btnPanel = new JPanel(new BorderLayout());
        btnPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        btnPanel.setBackground(CARD_COLOR);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setBackground(CARD_COLOR);
        left.add(getKeyBtn);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setBackground(CARD_COLOR);
        right.add(cancelBtn);
        right.add(okBtn);

        btnPanel.add(left, BorderLayout.WEST);
        btnPanel.add(right, BorderLayout.EAST);

        /* ---- build dialog ---- */
        JDialog dlg = new JDialog(this, "FRED API Key", true);
        dlg.setIconImage(null);
        dlg.setLayout(new BorderLayout());
        dlg.add(p, BorderLayout.CENTER);
        dlg.add(btnPanel, BorderLayout.SOUTH);

        dlg.setSize(450, 210);
        dlg.setLocationRelativeTo(this);

        /* ---- behaviour ---- */
        okBtn.addActionListener(e -> {
            String k = new String(keyField.getPassword()).trim();
            if (k.isEmpty()) {
                JOptionPane.showMessageDialog(dlg,
                        "API key cannot be empty.", "Invalid Key", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (k.length() != 32) {
                JOptionPane.showMessageDialog(dlg,
                        "API key must be exactly 32 characters.", "Invalid Key", JOptionPane.ERROR_MESSAGE);
                return;
            }
            apiKey = k;
            appendOutput("\n✓ API Key updated successfully\n");
            dlg.dispose();
        });
        cancelBtn.addActionListener(e -> dlg.dispose());

        dlg.setVisible(true);
        return apiKey != null;
    }

    /* ===================================================================================
                                      E X P O R T   G R A P H
       =================================================================================== */
    private void exportGraph() {
        if (chartPanel == null || chartPanel.getWidth() == 0) {
            JOptionPane.showMessageDialog(this,
                    "No chart to export.", "Export", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("FRED_chart.png"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            /* ensure .png */
            if (!f.getName().toLowerCase().endsWith(".png"))
                f = new File(f.getAbsolutePath() + ".png");

            BufferedImage img = new BufferedImage(chartPanel.getWidth(),
                    chartPanel.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            chartPanel.paintAll(g2);
            g2.dispose();
            try {
                javax.imageio.ImageIO.write(img, "png", f);
                appendOutput("\n✓ Chart exported to " + f.getAbsolutePath() + "\n");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Export failed: " + ex.getMessage(),
                        "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /* ===================================================================================
                                      D A T A   F E T C H
       =================================================================================== */
    private void fetchFREDData() {
        if (apiKey == null || apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "API key not configured. Please set your API key first.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            promptForApiKey();
            return;
        }

        JDialog dlg = new JDialog(this, "Fetch FRED Data", true);
        dlg.setLayout(new BorderLayout());
        dlg.setSize(500, 350);
        dlg.setLocationRelativeTo(this);

        JPanel main = new JPanel(new GridBagLayout());
        main.setBackground(CARD_COLOR);
        main.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridwidth = 2;
        main.add(new JLabel("Enter FRED Series Parameters") {{
            setFont(new Font("Segoe UI", Font.BOLD, 15));
            setForeground(TEXT_PRIMARY);
        }}, gbc);

        gbc.gridwidth = 1;

        /* series id */
        gbc.gridx = 0; gbc.gridy = 1;
        main.add(new JLabel("Series ID:") {{ setFont(new Font("Segoe UI", Font.PLAIN, 13)); }}, gbc);
        JTextField seriesIdF = new JTextField(20);
        seriesIdF.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        gbc.gridx = 1;
        main.add(seriesIdF, gbc);
        gbc.gridy = 2;
        main.add(new JLabel("(e.g., GDP, UNRATE, CPIAUCSL)") {{
            setFont(new Font("Segoe UI", Font.ITALIC, 11));
            setForeground(TEXT_SECONDARY);
        }}, gbc);

        /* start date */
        gbc.gridx = 0; gbc.gridy = 3;
        main.add(new JLabel("Start Date (optional):") {{ setFont(new Font("Segoe UI", Font.PLAIN, 13)); }}, gbc);
        JTextField startF = new JTextField(20);
        startF.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        gbc.gridx = 1;
        main.add(startF, gbc);
        gbc.gridy = 4;
        main.add(new JLabel("(Format: YYYY-MM-DD)") {{
            setFont(new Font("Segoe UI", Font.ITALIC, 11));
            setForeground(TEXT_SECONDARY);
        }}, gbc);

        /* end date */
        gbc.gridx = 0; gbc.gridy = 5;
        main.add(new JLabel("End Date (optional):") {{ setFont(new Font("Segoe UI", Font.PLAIN, 13)); }}, gbc);
        JTextField endF = new JTextField(20);
        endF.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        gbc.gridx = 1;
        main.add(endF, gbc);
        gbc.gridy = 6;
        main.add(new JLabel("(Format: YYYY-MM-DD)") {{
            setFont(new Font("Segoe UI", Font.ITALIC, 11));
            setForeground(TEXT_SECONDARY);
        }}, gbc);

        /* ---- buttons ---- */
        JPanel btnP = new JPanel(new BorderLayout());
        btnP.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10)); // top, left, bottom, right
        btnP.setBackground(CARD_COLOR);

        /* left – Browse button */
        JButton browseBtn = createStyledButton("Browse", TEXT_SECONDARY, TEXT_PRIMARY);
        browseBtn.setBackground(TEXT_SECONDARY);
        browseBtn.addActionListener(e -> {
            try { Desktop.getDesktop().browse(new URI("https://fred.stlouisfed.org/tags/series")); }
            catch (Exception ex) { ex.printStackTrace(); }
        });
        JPanel leftBtnP = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftBtnP.setBackground(CARD_COLOR);
        leftBtnP.add(browseBtn);

        /* right – Fetch / Cancel */
        JButton fetchBtn = createStyledButton("Fetch", PRIMARY_COLOR, PRIMARY_DARK);
        JButton cancBtn  = createStyledButton("Cancel", TEXT_SECONDARY, TEXT_PRIMARY);
        cancBtn.setBackground(TEXT_SECONDARY);

        fetchBtn.addActionListener(e -> {
            String sid = seriesIdF.getText().trim();
            if (sid.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Series ID is required!",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            LocalDate s = parseDate(startF.getText().trim());
            LocalDate en = parseDate(endF.getText().trim());
            dlg.dispose();
            performFetch(new FetchParams(sid, s, en));
        });
        cancBtn.addActionListener(e -> dlg.dispose());

        JPanel rightBtnP = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightBtnP.setBackground(CARD_COLOR);
        rightBtnP.add(cancBtn);
        rightBtnP.add(fetchBtn);

        btnP.add(leftBtnP, BorderLayout.WEST);
        btnP.add(rightBtnP, BorderLayout.EAST);

        dlg.add(main, BorderLayout.CENTER);
        dlg.add(btnP, BorderLayout.SOUTH);
        dlg.setVisible(true);

    }

    /* ===================================================================================
                                      U T I L I T Y
       =================================================================================== */
    private JButton createStyledButton(String text, Color base, Color hover) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setBackground(base);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setPreferredSize(new Dimension(100, 35));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(hover); }
            public void mouseExited(MouseEvent e)  { b.setBackground(base);  }
        });
        return b;
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd")); }
        catch (Exception ignore) { return null; }
    }

    /* ===================================================================================
                                      N E T W O R K
       =================================================================================== */
    private void performFetch(FetchParams p) {
        appendOutput("\n" + "=".repeat(60) + "\n");
        appendOutput("Fetching data for series: " + p.seriesId + "\n");
        appendOutput("=".repeat(60) + "\n");
        updateStatus("Fetching data...");

        new Thread(() -> {
            try {
                appendOutput("\n[1/2] Fetching series metadata...\n");
                String metaJson = fetchSeriesMetadata(p.seriesId);
                SimpleJSON meta = SimpleJSON.parse(metaJson);

                appendOutput("[2/2] Fetching series observations...\n");
                String obsJson = fetchSeriesObservations(p.seriesId, p.startDate, p.endDate);
                SimpleJSON obs = SimpleJSON.parse(obsJson);

                FREDDataSet ds = new FREDDataSet(meta, obs);
                cachedDataSets.put(p.seriesId, ds);
                if (activeSeriesIds.isEmpty()) activeSeriesIds.add(p.seriesId);

                SwingUtilities.invokeLater(() -> {
                    displayResults(ds);
                    updateChartWithActiveSeries();
                    updateStatus("Data Structures and Algorithms: 210");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    appendOutput("\n✗ Error: " + ex.getMessage() + "\n");
                    JOptionPane.showMessageDialog(FREDChartDisplayApp.this,
                            "Failed to fetch data: " + ex.getMessage(),
                            "Fetch Error", JOptionPane.ERROR_MESSAGE);
                    updateStatus("Error occurred");
                });
            }
        }).start();
    }

    private String fetchSeriesMetadata(String seriesId) throws Exception {
        String url = String.format(
                "https://api.stlouisfed.org/fred/series?series_id=%s&api_key=%s&file_type=json",
                URLEncoder.encode(seriesId, StandardCharsets.UTF_8),
                URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        return makeHttpRequest(url);
    }

    private String fetchSeriesObservations(String seriesId, LocalDate start, LocalDate end) throws Exception {
        StringBuilder url = new StringBuilder(String.format(
                "https://api.stlouisfed.org/fred/series/observations?series_id=%s&api_key=%s&file_type=json",
                URLEncoder.encode(seriesId, StandardCharsets.UTF_8),
                URLEncoder.encode(apiKey, StandardCharsets.UTF_8)));
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        if (start != null) url.append("&observation_start=").append(start.format(f));
        if (end   != null) url.append("&observation_end=").append(end.format(f));
        return makeHttpRequest(url.toString());
    }

    private String makeHttpRequest(String urlStr) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
        con.setRequestMethod("GET");
        con.setConnectTimeout(10000);
        con.setReadTimeout(10000);
        int code = con.getResponseCode();
        if (code != 200) throw new Exception("HTTP Error: " + code);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    /* ===================================================================================
                                      D I S P L A Y
       =================================================================================== */
    private void displayResults(FREDDataSet ds) {
        appendOutput("\n✓ Data fetched successfully!\n\n");
        appendOutput("SERIES METADATA:\n");
        appendOutput("-".repeat(60) + "\n");
        appendOutput(String.format("  ID: %s\n", ds.getId()));
        appendOutput(String.format("  Title: %s\n", ds.getTitle()));
        appendOutput(String.format("  Frequency: %s\n", ds.getFrequency()));
        appendOutput(String.format("  Units: %s\n", ds.getUnits()));
        appendOutput(String.format("  Last Updated: %s\n", ds.getLastUpdated()));
        appendOutput("\n");
        appendOutput("OBSERVATIONS:\n");
        appendOutput("-".repeat(60) + "\n");
        appendOutput(String.format("  Total observations: %d\n", ds.getObservationCount()));
        appendOutput(String.format("  Date range: %s to %s\n", ds.getFirstDate(), ds.getLastDate()));
        if (ds.getObservationCount() > 0) {
            appendOutput("\n  Latest 5 observations:\n");
            for (Observation o : ds.getLatestObservations(5))
                appendOutput(String.format("    %s: %s\n", o.date, o.value));
        }
        appendOutput("\n✓ Data cached for this session\n");
        appendOutput("✓ Chart displayed on the left panel\n");
    }

    private void appendOutput(String txt) {
        SwingUtilities.invokeLater(() -> outputArea.append(txt));
    }
    private void updateStatus(String txt) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(txt));
    }

    /* ===================================================================================
                                      M A N A G E   S E R I E S
       =================================================================================== */
    private void showManageSeriesDialog() {
        JDialog d = new JDialog(this, "Manage Comparison Series", true);
        d.setLayout(new BorderLayout(10, 10));
        d.setSize(600, 450);
        d.setLocationRelativeTo(this);

        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBackground(CARD_COLOR);
        main.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel head = new JLabel("Select Series to Display on Chart (Max 5)");
        head.setFont(new Font("Segoe UI", Font.BOLD, 15));
        head.setForeground(TEXT_PRIMARY);

        JLabel info = new JLabel("Check series to display them on the chart for comparison");
        info.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        info.setForeground(TEXT_SECONDARY);

        JPanel north = new JPanel(new BorderLayout(5, 5));
        north.setBackground(CARD_COLOR);
        north.add(head, BorderLayout.NORTH);
        north.add(info, BorderLayout.SOUTH);

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(CARD_COLOR);

        Map<String, JCheckBox> cbMap = new HashMap<>();

        if (cachedDataSets.isEmpty()) {
            JLabel empty = new JLabel("No data fetched yet. Fetch FRED data first.");
            empty.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            empty.setForeground(TEXT_SECONDARY);
            empty.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            listPanel.add(empty);
        } else {
            int colorIdx = 0;
            for (Map.Entry<String, FREDDataSet> e : cachedDataSets.entrySet()) {
                String id = e.getKey();
                FREDDataSet ds = e.getValue();

                JPanel row = new JPanel(new BorderLayout(10, 5));
                row.setBackground(CARD_COLOR);
                row.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(240, 240, 240)),
                        BorderFactory.createEmptyBorder(10, 10, 10, 10)));

                JCheckBox cb = new JCheckBox();
                cb.setBackground(CARD_COLOR);
                cb.setSelected(activeSeriesIds.contains(id));
                cbMap.put(id, cb);

                JPanel colorBar = new JPanel();
                colorBar.setBackground(CHART_COLORS[colorIdx % CHART_COLORS.length]);
                colorBar.setPreferredSize(new Dimension(4, 40));

                JPanel infoPanel = new JPanel();
                infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
                infoPanel.setBackground(CARD_COLOR);

                JLabel titleL = new JLabel(ds.getTitle());
                titleL.setFont(new Font("Segoe UI", Font.BOLD, 13));
                titleL.setForeground(TEXT_PRIMARY);

                JLabel detL = new JLabel(String.format("%s | %d observations | %s",
                        id, ds.getObservationCount(), ds.getUnits()));
                detL.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                detL.setForeground(TEXT_SECONDARY);

                infoPanel.add(titleL);
                infoPanel.add(Box.createVerticalStrut(3));
                infoPanel.add(detL);

                row.add(cb, BorderLayout.WEST);
                row.add(colorBar, BorderLayout.EAST);
                row.add(infoPanel, BorderLayout.CENTER);

                listPanel.add(row);
                colorIdx++;
            }
        }

        JScrollPane sp = new JScrollPane(listPanel);
        sp.setBorder(new LineBorder(new Color(224, 224, 224), 1));
        sp.getVerticalScrollBar().setUnitIncrement(16);

        JPanel btnP = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnP.setBackground(CARD_COLOR);

        JButton apply = createStyledButton("Apply", PRIMARY_COLOR, PRIMARY_DARK);
        JButton canc  = createStyledButton("Cancel", TEXT_SECONDARY, TEXT_PRIMARY);
        canc.setBackground(TEXT_SECONDARY);

        apply.addActionListener(e -> {
            List<String> selected = new ArrayList<>();
            for (Map.Entry<String, JCheckBox> en : cbMap.entrySet())
                if (en.getValue().isSelected()) selected.add(en.getKey());

            if (selected.size() > 5) {
                JOptionPane.showMessageDialog(d,
                        "Maximum 5 series can be displayed at once.",
                        "Too Many Series", JOptionPane.WARNING_MESSAGE);
                return;
            }
            activeSeriesIds = selected;
            updateChartWithActiveSeries();
            appendOutput("\n✓ Chart updated with " + activeSeriesIds.size() + " series\n");
            d.dispose();
        });
        canc.addActionListener(e -> d.dispose());

        btnP.add(canc);
        btnP.add(apply);

        main.add(north, BorderLayout.NORTH);
        main.add(sp, BorderLayout.CENTER);
        main.add(btnP, BorderLayout.SOUTH);

        d.add(main);
        d.setVisible(true);
    }

    private void updateChartWithActiveSeries() {
        if (activeSeriesIds.isEmpty()) {
            chartPanel.clearData();
        } else {
            List<FREDDataSet> toShow = new ArrayList<>();
            for (String id : activeSeriesIds) {
                FREDDataSet ds = cachedDataSets.get(id);
                if (ds != null) toShow.add(ds);
            }
            chartPanel.setMultipleData(toShow);
        }
    }

    /* ===================================================================================
                                      T O G G L E   C O N S O L E
       =================================================================================== */
    private void toggleConsoleVisibility() {
        JSplitPane sp = (JSplitPane) ((JPanel) getContentPane().getComponent(0)).getComponent(0);
        if (hideConsole) {
            sp.setRightComponent(null);
        } else {
            JPanel right = new JPanel(new BorderLayout(10, 10));
            right.setBackground(BACKGROUND_COLOR);
            right.setBorder(BorderFactory.createEmptyBorder(15, 10, 15, 15));

            JLabel lab = new JLabel("Output Console");
            lab.setFont(new Font("Segoe UI", Font.BOLD, 16));
            lab.setForeground(TEXT_PRIMARY);
            lab.setBorder(BorderFactory.createEmptyBorder(0, 5, 10, 0));

            JScrollPane scr = new JScrollPane(outputArea);
            scr.setBorder(new LineBorder(new Color(224, 224, 224), 1));

            right.add(lab, BorderLayout.NORTH);
            right.add(scr, BorderLayout.CENTER);

            sp.setRightComponent(right);
            sp.setDividerLocation(0.6);
        }
        sp.revalidate();
        sp.repaint();
    }

    /* ===================================================================================
                                      J S O N   H E L P E R
       =================================================================================== */
    static class SimpleJSON {
        private final String json;
        private SimpleJSON(String j) { json = j; }
        static SimpleJSON parse(String j) { return new SimpleJSON(j); }

        String getString(String key) {
            String k = "\"" + key + "\":";
            int i = json.indexOf(k);
            if (i == -1) return "";
            int s = json.indexOf("\"", i + k.length()) + 1;
            int e = json.indexOf("\"", s);
            return json.substring(s, e);
        }
        List<SimpleJSON> getArray(String key) {
            List<SimpleJSON> res = new ArrayList<>();
            String k = "\"" + key + "\":";
            int i = json.indexOf(k);
            if (i == -1) return res;
            int aStart = json.indexOf("[", i);
            int aEnd   = findMatching(json, aStart);
            String inside = json.substring(aStart + 1, aEnd);
            int depth = 0, objStart = -1;
            for (int x = 0; x < inside.length(); x++) {
                char c = inside.charAt(x);
                if (c == '{') { if (depth == 0) objStart = x; depth++; }
                else if (c == '}') {
                    depth--;
                    if (depth == 0 && objStart != -1) {
                        res.add(new SimpleJSON(inside.substring(objStart, x + 1)));
                        objStart = -1;
                    }
                }
            }
            return res;
        }
        SimpleJSON getObject(String key) {
            String k = "\"" + key + "\":";
            int i = json.indexOf(k);
            if (i == -1) return new SimpleJSON("{}");
            int oStart = json.indexOf("{", i);
            int oEnd   = findMatching(json, oStart);
            return new SimpleJSON(json.substring(oStart, oEnd + 1));
        }
        private int findMatching(String s, int start) {
            char open = s.charAt(start), close = (open == '{') ? '}' : ']';
            int depth = 1;
            for (int i = start + 1; i < s.length(); i++) {
                if (s.charAt(i) == open) depth++;
                else if (s.charAt(i) == close) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
            return s.length() - 1;
        }
    }

    /* ===================================================================================
                                      D A T A   M O D E L
       =================================================================================== */
    static class FetchParams {
        final String seriesId;
        final LocalDate startDate;
        final LocalDate endDate;
        FetchParams(String id, LocalDate s, LocalDate e) {
            seriesId = id; startDate = s; endDate = e;
        }
    }
    static class Observation {
        final String date, value;
        Observation(String d, String v) { date = d; value = v; }
    }
    static class FREDDataSet {
        private final SimpleJSON metadata;
        private final List<SimpleJSON> observations;

        FREDDataSet(SimpleJSON meta, SimpleJSON obs) {
            List<SimpleJSON> sa = meta.getArray("seriess");
            this.metadata = sa.isEmpty() ? new SimpleJSON("{}") : sa.get(0);
            this.observations = obs.getArray("observations");
        }

        /* ---- factory for user imports ---- */
        static FREDDataSet fromUserData(String id, String src, List<Observation> obs) {
            String meta = "{\"seriess\":[{\"id\":\"" + id +
                    "\",\"title\":\"" + src +
                    "\",\"frequency\":\"Imported\",\"units\":\"Units\",\"last_updated\":\"\"}]}";

            StringBuilder obsB = new StringBuilder("{\"observations\":[");
            for (int i = 0; i < obs.size(); i++) {
                if (i > 0) obsB.append(',');
                obsB.append("{\"date\":\"").append(obs.get(i).date)
                        .append("\",\"value\":\"").append(obs.get(i).value).append("\"}");
            }
            obsB.append("]}");

            return new FREDDataSet(SimpleJSON.parse(meta), SimpleJSON.parse(obsB.toString()));
        }

        String getId()           { return metadata.getString("id"); }
        String getTitle()        { return metadata.getString("title"); }
        String getFrequency()    { return metadata.getString("frequency"); }
        String getUnits()        { return metadata.getString("units"); }
        String getLastUpdated()  { return metadata.getString("last_updated"); }
        int getObservationCount(){ return observations.size(); }
        String getFirstDate()    { return observations.isEmpty() ? "N/A" : observations.get(0).getString("date"); }
        String getLastDate()     { return observations.isEmpty() ? "N/A" : observations.get(observations.size()-1).getString("date"); }
        List<Observation> getLatestObservations(int n) {
            List<Observation> res = new ArrayList<>();
            int start = Math.max(0, observations.size() - n);
            for (int i = observations.size() - 1; i >= start; i--) {
                SimpleJSON o = observations.get(i);
                res.add(new Observation(o.getString("date"), o.getString("value")));
            }
            return res;
        }
        List<Observation> getAllObservations() {
            List<Observation> res = new ArrayList<>();
            for (SimpleJSON o : observations)
                res.add(new Observation(o.getString("date"), o.getString("value")));
            return res;
        }
    }

    /* ===================================================================================
                                      C H A R T   P A N E L
       =================================================================================== */
    class ChartPanel extends JPanel {
        private List<FREDDataSet> dataSets = new ArrayList<>();
        private List<List<Point2D.Double>> allPoints = new ArrayList<>();
        private List<List<Point>> allScreenPoints = new ArrayList<>();
        private double minValue = Double.MAX_VALUE, maxValue = Double.MIN_VALUE;
        private Point mousePosition;
        private int hoveredSeriesIndex = -1, hoveredPointIndex = -1;

        ChartPanel() {
            setPreferredSize(new Dimension(600, 400));
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    mousePosition = e.getPoint();
                    findHoveredPoint();
                    repaint();
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) {
                    mousePosition = null;
                    hoveredSeriesIndex = -1;
                    hoveredPointIndex = -1;
                    repaint();
                }
            });
        }

        private void findHoveredPoint() {
            hoveredSeriesIndex = -1;
            hoveredPointIndex = -1;
            if (mousePosition == null || allScreenPoints.isEmpty()) return;
            int threshold = 15;
            double minDist = threshold;
            for (int s = 0; s < allScreenPoints.size(); s++) {
                List<Point> pts = allScreenPoints.get(s);
                for (int i = 0; i < pts.size(); i++) {
                    double d = mousePosition.distance(pts.get(i));
                    if (d < minDist) {
                        minDist = d;
                        hoveredSeriesIndex = s;
                        hoveredPointIndex = i;
                    }
                }
            }
        }

        void setMultipleData(List<FREDDataSet> list) {
            dataSets = list;
            allPoints.clear();
            allScreenPoints.clear();
            if (list.isEmpty()) { clearData(); return; }

            minValue = Double.MAX_VALUE;
            maxValue = Double.MIN_VALUE;

            for (FREDDataSet ds : dataSets) {
                List<Point2D.Double> pts = new ArrayList<>();
                List<Observation> obs = ds.getAllObservations();
                for (int i = 0; i < obs.size(); i++) {
                    try {
                        double v = Double.parseDouble(obs.get(i).value);
                        if (!Double.isNaN(v)) {
                            pts.add(new Point2D.Double(i, v));
                            minValue = Math.min(minValue, v);
                            maxValue = Math.max(maxValue, v);
                        }
                    } catch (NumberFormatException ignore) {}
                }
                allPoints.add(pts);
                allScreenPoints.add(new ArrayList<>());
            }
            hoveredSeriesIndex = -1;
            hoveredPointIndex = -1;
            repaint();
        }

        void clearData() {
            dataSets.clear();
            allPoints.clear();
            allScreenPoints.clear();
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int pad = 60;
            int cw = w - 2 * pad, ch = h - 2 * pad;

            if (dataSets.isEmpty() || allPoints.isEmpty()) {
                g2.setColor(TEXT_SECONDARY);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                String m = "No data to display. Fetch FRED data to see the chart.";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(m, (w - fm.stringWidth(m)) / 2, h / 2);
                return;
            }

            /* title */
            g2.setColor(txtColor);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
            String title = dataSets.size() == 1 ? dataSets.get(0).getTitle()
                    : "Comparing " + dataSets.size() + " Series";
            g2.drawString(title, pad, 30);

            /* axes */
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(2));
            g2.drawLine(pad, pad, pad, h - pad);
            g2.drawLine(pad, h - pad, w - pad, h - pad);

            /* grid */
            g2.setColor(CHART_GRID_COLOR);
            g2.setStroke(new BasicStroke(1));
            for (int i = 0; i <= 5; i++) {
                int y = pad + (ch * i / 5);
                g2.drawLine(pad, y, w - pad, y);
            }

            /* y-labels */
            g2.setColor(txtColor);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            double range = maxValue - minValue;
            for (int i = 0; i <= 5; i++) {
                double val = maxValue - (range * i / 5);
                int y = pad + (ch * i / 5);
                g2.drawString(String.format("%.2f", val), 10, y + 5);
            }

            /* plot lines & collect screen points */
            for (int s = 0; s < allPoints.size(); s++) {
                List<Point2D.Double> pts = allPoints.get(s);
                List<Point> screen = allScreenPoints.get(s);
                screen.clear();
                if (pts.size() > 1) {
                    Color c = CHART_COLORS[s % CHART_COLORS.length];
                    g2.setColor(c);
                    g2.setStroke(new BasicStroke(2));
                    for (int i = 0; i < pts.size() - 1; i++) {
                        Point2D.Double p1 = pts.get(i);
                        Point2D.Double p2 = pts.get(i + 1);
                        int x1 = pad + (int) (cw * p1.x / (pts.size() - 1));
                        int y1 = h - pad - (int) (ch * (p1.y - minValue) / range);
                        int x2 = pad + (int) (cw * p2.x / (pts.size() - 1));
                        int y2 = h - pad - (int) (ch * (p2.y - minValue) / range);
                        if (i == 0) screen.add(new Point(x1, y1));
                        screen.add(new Point(x2, y2));
                        g2.drawLine(x1, y1, x2, y2);
                    }
                    for (Point p : screen) g2.fillOval(p.x - 2, p.y - 2, 4, 4);
                }
            }

            /* legend */
            if (dataSets.size() > 1) {
                int lx = w - pad - 200, ly = pad + 20, lh = 25;
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                for (int s = 0; s < dataSets.size(); s++) {
                    Color c = CHART_COLORS[s % CHART_COLORS.length];
                    g2.setColor(c);
                    g2.fillRect(lx, ly + s * lh, 15, 15);
                    g2.setColor(Color.BLACK);
                    g2.drawRect(lx, ly + s * lh, 15, 15);
                    g2.setColor(txtColor);
                    String lab = dataSets.get(s).getId();
                    if (lab.length() > 20) lab = lab.substring(0, 17) + "...";
                    g2.drawString(lab, lx + 20, ly + s * lh + 12);
                }
            }

            /* highlight & tooltip */
            if (hoveredSeriesIndex >= 0 && hoveredPointIndex >= 0
                    && hoveredSeriesIndex < allScreenPoints.size()
                    && hoveredPointIndex < allScreenPoints.get(hoveredSeriesIndex).size()) {

                Point hp = allScreenPoints.get(hoveredSeriesIndex).get(hoveredPointIndex);
                FREDDataSet hds = dataSets.get(hoveredSeriesIndex);
                List<Observation> obs = hds.getAllObservations();
                Observation ho = obs.get(hoveredPointIndex);
                Color sc = CHART_COLORS[hoveredSeriesIndex % CHART_COLORS.length];

                g2.setColor(sc);
                g2.fillOval(hp.x - 6, hp.y - 6, 12, 12);
                g2.setColor(Color.WHITE);
                g2.fillOval(hp.x - 4, hp.y - 4, 8, 8);
                g2.setColor(sc);
                g2.fillOval(hp.x - 3, hp.y - 3, 6, 6);

                String tip = String.format("%s: %s (%s)", hds.getId(), ho.value, ho.date);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(tip) + 16, th = fm.getHeight() + 8;
                int tx = hp.x + 10, ty = hp.y - 30;
                if (tx + tw > w - 10) tx = hp.x - tw - 10;
                if (ty < 10) ty = hp.y + 30;

                g2.setColor(new Color(50, 50, 50, 230));
                g2.fillRoundRect(tx, ty, tw, th, 8, 8);
                g2.setColor(sc);
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(tx, ty, tw, th, 8, 8);
                g2.setColor(Color.WHITE);
                g2.drawString(tip, tx + 8, ty + th - 8);

                /* crosshair */
                Color cross = new Color(sc.getRed(), sc.getGreen(), sc.getBlue(), 100);
                g2.setColor(cross);
                g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                        0, new float[]{5}, 0));
                g2.drawLine(hp.x, pad, hp.x, h - pad);
                g2.drawLine(pad, hp.y, w - pad, hp.y);
            }

            /* x-labels */
            g2.setColor(txtColor);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            if (!dataSets.isEmpty() && !allPoints.isEmpty() && !allPoints.get(0).isEmpty()) {
                List<Observation> obs = dataSets.get(0).getAllObservations();
                int step = Math.max(1, obs.size() / 8);
                List<Point2D.Double> first = allPoints.get(0);
                for (int i = 0; i < obs.size(); i += step) {
                    if (i < first.size()) {
                        int x = pad + (int) (cw * i / (first.size() - 1));
                        g2.drawString(obs.get(i).date, x - 20, h - pad + 20);
                    }
                }
            }
        }
    }
    private Image blankIcon() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB); // fully transparent
    }

    private void toggleChartVisibility() {
        JSplitPane sp = (JSplitPane) ((JPanel) getContentPane().getComponent(0)).getComponent(0);
        if (hideChart) {
            sp.setLeftComponent(null);
        } else {
            sp.setLeftComponent(chartWrapper);   // <-- wrapper, not chartPanel
            sp.setDividerLocation(0.6);
        }
        sp.revalidate();
        sp.repaint();
    }

    private void importCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        List<Observation> obs = new ArrayList<>();
        String seriesId = fc.getSelectedFile().getName().replace(".csv", "");
        try (BufferedReader br = new BufferedReader(new FileReader(fc.getSelectedFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] tok = line.split(",", -1);
                if (tok.length >= 2) obs.add(new Observation(tok[0].trim(), tok[1].trim()));
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Read error: " + ex.getMessage(),
                    "Import failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (obs.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No data found", "Import", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        /* wrap as a fake FREDDataSet and cache it */
        FREDDataSet ds = FREDDataSet.fromUserData(seriesId, "Imported CSV", obs);
        cachedDataSets.put(seriesId, ds);
        if (activeSeriesIds.isEmpty()) activeSeriesIds.add(seriesId);
        updateChartWithActiveSeries();
        appendOutput("\n✓ Imported CSV: " + seriesId + " (" + obs.size() + " rows)\n");
    }

    private void importJson() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;


        try {
            String json = new String(Files.readAllBytes(fc.getSelectedFile().toPath()), StandardCharsets.UTF_8);
            SimpleJSON root = SimpleJSON.parse(json);

            /* expect:  { "seriesId":"XXX", "observations":[ {"date":"...", "value":"..."}, ... ] } */
            String seriesId = root.getString("seriesId");
            List<SimpleJSON> arr = root.getArray("observations");
            List<Observation> obs = new ArrayList<>();
            for (SimpleJSON o : arr) obs.add(new Observation(o.getString("date"), o.getString("value")));

            if (obs.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No data found", "Import", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            FREDDataSet ds = FREDDataSet.fromUserData(seriesId, "Imported CSV", obs);
            cachedDataSets.put(seriesId, ds);
            if (activeSeriesIds.isEmpty()) activeSeriesIds.add(seriesId);
            updateChartWithActiveSeries();
            appendOutput("\n✓ Imported JSON: " + seriesId + " (" + obs.size() + " rows)\n");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Import failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void toggleDarkMode() {
        darkMode = !darkMode;
        bgColor   = darkMode ? BG_DARK    : BG_LIGHT;
        cardColor = darkMode ? CARD_DARK  : CARD_LIGHT;
        txtColor  = darkMode ? TXT_DARK   : TXT_LIGHT;
        gridColor = darkMode ? GRID_DARK  : GRID_LIGHT;

        SwingUtilities.invokeLater(() -> {
            applyColors(this);
            saveTheme();
        });
    }

    private void applyColors(Container c) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JPanel) {
                ((JPanel) comp).setBackground(bgColor);
                applyColors((Container) comp);
            }
            if (comp instanceof JTextArea || comp instanceof JTextField || comp instanceof JPasswordField) {
                comp.setBackground(cardColor);
                comp.setForeground(txtColor);
            }
            if (comp instanceof JLabel) ((JLabel) comp).setForeground(txtColor);
            if (comp instanceof JMenuBar) comp.setBackground(cardColor);
            if (comp instanceof Container) applyColors((Container) comp);
        }
        /* chart text & grid */
        CHART_GRID_COLOR = gridColor;
        chartPanel.setForeground(txtColor);   // <-- ADD THIS LINE
        chartPanel.repaint();
    }


    /* ===================================================================================
                                  M O N G O D B   O P E R A T I O N S
   =================================================================================== */
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
                    convertAndCacheDocument(doc);
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

    private void convertAndCacheDocument(FREDDataDocument doc) {
        try {
            // Convert observations from MongoDB format to app format
            List<Observation> observations = new ArrayList<>();
            for (FREDDataDocument.ObservationDocument obs : doc.getObservations()) {
                observations.add(new Observation(obs.getDate(), obs.getValue()));
            }

            // Use the existing factory method to create FREDDataSet
            FREDDataSet dataSet = FREDDataSet.fromUserData(
                    doc.getSeriesId(),
                    doc.getTitle(),
                    observations
            );

            cachedDataSets.put(doc.getSeriesId(), dataSet);
            if (activeSeriesIds.isEmpty()) {
                activeSeriesIds.add(doc.getSeriesId());
            }

            SwingUtilities.invokeLater(() -> {
                updateChartWithActiveSeries();
                updateStatus("Data Structures and Algorithms: 210");
            });

        } catch (Exception e) {
            appendOutput("✗ Failed to convert document: " + e.getMessage() + "\n");
        }
    }

    private void saveToMongoDB(FREDDataSet ds) {
        if (mongoService != null && mongoService.isServiceAvailable()) {
            try {
                FREDDataDocument doc = new FREDDataDocument();
                doc.setSeriesId(ds.getId());
                doc.setTitle(ds.getTitle());
                doc.setFrequency(ds.getFrequency());
                doc.setUnits(ds.getUnits());
                doc.setLastUpdated(ds.getLastUpdated());

                // Convert observations
                //test
                for (Observation obs : ds.getAllObservations()) {
                    doc.getObservations().add(new FREDDataDocument.ObservationDocument(obs.date, obs.value));
                }

                if (mongoService.saveFREDData(doc)) {
                    appendOutput("✓ Data saved to MongoDB: " + ds.getId() + "\n");
                }
            } catch (Exception e) {
                appendOutput("✗ Failed to save to MongoDB: " + e.getMessage() + "\n");
            }
        }
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

    /* ===================================================================================
                                      M A I N
       =================================================================================== */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(FREDChartDisplayApp::new);
    }
}
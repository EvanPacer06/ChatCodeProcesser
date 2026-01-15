import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main { 
    // Credentials provided by user
    private static final String API_KEY = "x";
    private static final String CHANNEL_ID = "x";

    // Modern Colors
    private static final Color BG_COLOR = new Color(30, 30, 30);       // Dark Grey
    private static final Color PANEL_COLOR = new Color(45, 45, 45);    // Slightly Lighter Grey
    private static final Color TEXT_COLOR = new Color(0, 0, 0);  // White-ish (Readable)
    private static final Color ACCENT_COLOR = new Color(70, 130, 180); // Steel Blue
    private static final Font MAIN_FONT = new Font("Segoe UI", Font.PLAIN, 14);

    private JFrame frame;
    private JPanel listPanel;
    private JButton startBtn; 
    private HashSet<String> seenCodes = new HashSet<>();
    private Pattern gdPattern = Pattern.compile("\\b\\d{6,10}\\b");
    private YouTube youtube;

    public Main() throws Exception {
        setupUI();
        youtube = new YouTube.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), null)
                .setApplicationName("GD-Chat-Reader").build();
    }

    private void setupUI() {
        // 1. Main Frame Setup
        frame = new JFrame("GD Level Queue");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(380, 650);
        frame.getContentPane().setBackground(BG_COLOR);

        // 2. Top Control Panel
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        topPanel.setBackground(BG_COLOR);

        startBtn = createModernButton("Connect to Stream", ACCENT_COLOR);
        JButton testBtn = createModernButton("Test UI", new Color(100, 100, 100));

        startBtn.addActionListener(e -> {
            startBtn.setEnabled(false);
            startBtn.setText("Connecting...");
            new Thread(this::autoConnectAndListen).start();
        });

        testBtn.addActionListener(e -> {
            SwingUtilities.invokeLater(() -> addLevelButton("12345678 (Test)"));
        });

        topPanel.add(startBtn);
        topPanel.add(testBtn);

        // 3. Scrollable List Panel
        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(PANEL_COLOR);
        
        // Scroll Pane Styling
        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private JButton createModernButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(MAIN_FONT);
        btn.setBackground(bg);
        btn.setForeground(TEXT_COLOR); 
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(10, 20, 10, 20));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void autoConnectAndListen() {
        try {
            String videoId = null;

            // 1. First, try to find a PUBLIC live stream automatically
            SearchListResponse search = youtube.search().list(List.of("id"))
                    .setChannelId(CHANNEL_ID)
                    .setType(List.of("video"))
                    .setEventType("live")
                    .setKey(API_KEY)
                    .execute();

            if (!search.getItems().isEmpty()) {
                videoId = search.getItems().get(0).getId().getVideoId();
                System.out.println("Auto-detected Public Stream: " + videoId);
            } else {
                // 2. If no public stream found, ask the user manually (Handles Unlisted)
                String input = JOptionPane.showInputDialog(frame, 
                    "No Public Stream found.\n\nEnter Video ID or URL for Unlisted Stream:", 
                    "Connect Manually", 
                    JOptionPane.QUESTION_MESSAGE);
                
                if (input == null || input.trim().isEmpty()) {
                    resetStartButton("Connection Cancelled");
                    return;
                }
                videoId = extractVideoId(input);
            }

            // 3. Get Chat ID from the Video ID
            VideoListResponse vRes = youtube.videos().list(List.of("liveStreamingDetails"))
                    .setId(List.of(videoId))
                    .setKey(API_KEY)
                    .execute();
            
            if (vRes.getItems().isEmpty()) {
                resetStartButton("Invalid Video ID");
                return;
            }

            String chatId = vRes.getItems().get(0).getLiveStreamingDetails().getActiveLiveChatId();
            
            SwingUtilities.invokeLater(() -> {
                startBtn.setText("Connected (Listening)");
                startBtn.setBackground(new Color(46, 139, 87)); // Green color
            });

            // 4. Loop forever
            String nextToken = null;
            while (true) {
                LiveChatMessageListResponse res = youtube.liveChatMessages().list(chatId, List.of("snippet"))
                        .setKey(API_KEY)
                        .setPageToken(nextToken)
                        .execute();

                for (LiveChatMessage msg : res.getItems()) {
                    parse(msg.getSnippet().getDisplayMessage());
                }

                nextToken = res.getNextPageToken();
                Thread.sleep(20000); 
            }
        } catch (Exception e) { 
            e.printStackTrace();
            resetStartButton("Error: " + e.getMessage());
        }
    }

    // Helper to pull the ID out of a full URL if the user pastes one
    private String extractVideoId(String input) {
        // If user pastes "youtube.com/watch?v=AbCdEfG" -> returns "AbCdEfG"
        if (input.contains("v=")) {
            return input.split("v=")[1].split("&")[0];
        }
        // If user pastes "youtu.be/AbCdEfG" -> returns "AbCdEfG"
        if (input.contains("youtu.be/")) {
            return input.split("youtu.be/")[1].split("\\?")[0];
        }
        // Assume it's just the ID
        return input;
    }

    private void resetStartButton(String msg) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(frame, msg);
            startBtn.setText("Connect to Stream");
            startBtn.setEnabled(true);
            startBtn.setBackground(ACCENT_COLOR);
        });
    }

    private void parse(String text) {
        Matcher m = gdPattern.matcher(text);
        while (m.find()) {
            String code = m.group();
            if (seenCodes.add(code)) {
                SwingUtilities.invokeLater(() -> addLevelButton(code));
            }
        }
    }

    private void addLevelButton(String code) {
        JPanel itemPanel = new JPanel(new BorderLayout());
        itemPanel.setBackground(PANEL_COLOR);
        itemPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        itemPanel.setBorder(new EmptyBorder(5, 10, 5, 10)); 

        JButton b = new JButton(code);
        b.setFont(new Font("Segoe UI", Font.BOLD, 16));
        b.setBackground(new Color(60, 63, 65));
        b.setForeground(TEXT_COLOR);
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setToolTipText("Click to Copy & Remove");

        b.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
            listPanel.remove(itemPanel);
            listPanel.revalidate();
            listPanel.repaint();
        });

        itemPanel.add(b, BorderLayout.CENTER);
        listPanel.add(itemPanel);
        listPanel.revalidate();
        listPanel.repaint();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                new Main();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
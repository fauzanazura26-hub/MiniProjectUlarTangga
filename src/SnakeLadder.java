import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.*;
import java.util.List;
import javax.sound.sampled.*;
import java.net.URL;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import javax.imageio.ImageIO;

public class SnakeLadder extends JFrame {

    // Daftar nama Karakter yang tersedia (Tampilan)
    private static final String[] POKEMON_NAMES = {"Bulbasaur", "Charmander", "Squirtle", "Pikachu"};
    // Daftar Nama File Karakter (Harus sesuai dengan nama file di folder Char/)
    private static final String[] POKEMON_FILES = {"bulbasaur.png", "charmander.png", "squirtle.png", "pikachu.png"};
    // PATH IKON KARAKTER
    private static final String CHARACTER_BASE_PATH = "Char/";

    // ================== SOUND MANAGER (UPDATED) ==================
    private static class SoundManager {

        private static Clip startClip;
        private static Clip bgmClip;
        private static Clip diceClip;
        private static Clip winnerClip;

        static {
            startClip  = loadClip("Audio/start.wav");
            bgmClip    = loadClip("Audio/bgm.wav");
            diceClip   = loadClip("Audio/dice.wav");
            winnerClip = loadClip("Audio/winner.wav");
        }

        private static Clip loadClip(String resourceName) {
            try {
                URL url = SnakeLadder.class.getResource(resourceName);
                if (url == null) {
                    System.err.println("Sound not found: " + resourceName);
                    return null;
                }
                AudioInputStream ais = AudioSystem.getAudioInputStream(url);
                Clip clip = AudioSystem.getClip();
                clip.open(ais);
                return clip;
            } catch (Exception e) {
                return null;
            }
        }

        private static void playOnce(Clip clip) {
            if (clip == null) return;
            if (clip.isRunning()) clip.stop();
            clip.setFramePosition(0);
            clip.start();
        }

        public static void playGameStart() { playOnce(startClip); }
        public static void playDice()      { playOnce(diceClip); }
        public static void playWinner()    { playOnce(winnerClip); }

        public static void playBGM() {
            if (bgmClip == null) return;
            if (bgmClip.isRunning()) return;
            bgmClip.setFramePosition(0);
            bgmClip.loop(Clip.LOOP_CONTINUOUSLY);
        }

        public static void stopBGM() {
            if (bgmClip != null && bgmClip.isRunning()) bgmClip.stop();
        }

        // --- FITUR BARU: PENGATUR VOLUME ---
        public static void setBGMVolume(float decibels) {
            if (bgmClip != null && bgmClip.isOpen()) {
                try {
                    FloatControl gainControl = (FloatControl) bgmClip.getControl(FloatControl.Type.MASTER_GAIN);

                    // Mencegah error jika nilai melebihi batas hardware
                    float min = gainControl.getMinimum();
                    float max = gainControl.getMaximum();

                    if (decibels < min) decibels = min;
                    if (decibels > max) decibels = max;

                    gainControl.setValue(decibels);
                } catch (IllegalArgumentException e) {
                    System.err.println("Volume control not supported for BGM.");
                }
            }
        }
    }

    // ================== KONFIGURASI BOARD ==================
    private static final int BOARD_SIZE = 64;
    private static final Dimension BOARD_DIM = new Dimension(640, 680);
    private static final String POSITION_FILE = "node_positions.txt";


    // ================== DATA GAME ==================
    private List<BoardEditor.Player> players = new ArrayList<>();
    private Queue<BoardEditor.Player> turnQueue = new ArrayDeque<>();
    private BoardEditor.Player currentPlayer;

    private BoardEditor.BoardGraph board;
    private BoardEditor.Dice dice;

    private boolean gameOver = false;

    // --------- STATE ANIMASI GERAK -----------
    private javax.swing.Timer moveTimer;
    private int stepsLeft;
    private boolean movePositive;
    private int moveInitialPos;
    private int currentDiceNumber;
    private boolean currentDicePositive;
    private boolean useShortestThisRoll;

    // --------- SKOR NODE & PLAYER -----------
    private int[] nodeScores = new int[BOARD_SIZE + 1];
    private boolean[] nodeClaimed = new boolean[BOARD_SIZE + 1];
    private Map<BoardEditor.Player, Integer> playerScores = new HashMap<>();
    private Random scoreRandom = new Random();

    // --------- RANDOM ANIMASI DADU -----------
    private Random diceAnimRandom = new Random();

    // ================== KOMPONEN GUI ==================
    private BoardPanel boardPanel;
    private JLabel lblTurn;
    private JLabel lblDiceText;
    private JLabel lblStatus;
    private JButton btnRoll;
    private JButton btnReset;
    private JTextArea historyArea;
    private DicePanel dicePanel;
    private JTextArea leaderboardArea;
    // Komponen Volume Baru
    private JSlider volSlider;

    // ================== MAIN ==================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SnakeLadder().setVisible(true));
    }

    public SnakeLadder() {
        setTitle("Snake & Ladder - Pirate Map Edition");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(true);

        initGame();
        initUI();

        pack();
        setMinimumSize(new Dimension(BOARD_DIM.width + 380, BOARD_DIM.height + 80));
        setLocationRelativeTo(null);

        SoundManager.playGameStart();
        SoundManager.playBGM();

        // Set volume awal agak pelan agar nyaman (-10 dB)
        SoundManager.setBGMVolume(-10.0f);
    }

    // ================== UTIL: PRIMA & BINTANG (PUBLIC STATIC) ==================

    public static boolean isStarPosition(int n) { return n > 0 && n % 5 == 0; }

    public static boolean isPrime(int n) {
        if (n <= 1) return false;
        if (n <= 3) return true;
        if (n % 2 == 0 || n % 3 == 0) return false;
        for (int i = 5; i * i <= n; i += 6) {
            if (n % i == 0 || n % (i + 2) == 0) return false;
        }
        return true;
    }

    // ================== SKOR NODE & PLAYER ==================

    private void initScores() {
        for (int i = 1; i <= BOARD_SIZE; i++) {
            nodeScores[i] = 5 + scoreRandom.nextInt(16); // 5..20
            nodeClaimed[i] = false;
        }
        nodeScores[1] = 0;
    }

    private int getScore(BoardEditor.Player p) {
        return playerScores.getOrDefault(p, 0);
    }

    private String buildScoreBoardText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Skor Akhir:\n\n");
        for (BoardEditor.Player p : players) {
            sb.append(p.name)
                    .append(" : ")
                    .append(getScore(p))
                    .append("\n");
        }
        return sb.toString();
    }

    private void updateLeaderboard() {
        if (leaderboardArea == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("Leaderboard:\n\n");

        List<BoardEditor.Player> sorted = new ArrayList<>(players);
        sorted.sort((a, b) -> Integer.compare(getScore(b), getScore(a)));

        int rank = 1;
        for (BoardEditor.Player p : sorted) {
            sb.append(rank)
                    .append(". ")
                    .append(p.name)
                    .append("  | Skor: ")
                    .append(getScore(p))
                    .append("  | Pos: ")
                    .append(p.position)
                    .append("\n");
            rank++;
        }
        leaderboardArea.setText(sb.toString());
    }

    // ================== CUSTOM DIALOGS ==================

    private int showPlayerCountDialog() {
        JDialog dialog = new JDialog(this, "Pilih Pemain", true);
        dialog.setSize(380, 220);
        dialog.setLayout(new BorderLayout());

        Color bgColor = new Color(220, 20, 60);
        Color fgColor = Color.WHITE;
        Color panelColor = new Color(255, 255, 255, 200);

        dialog.getContentPane().setBackground(bgColor);
        dialog.setLocationRelativeTo(this);

        JLabel label = new JLabel("Pilih Jumlah Pemain (2 - 4):");
        label.setForeground(fgColor);
        label.setFont(new Font("Arial", Font.BOLD, 18));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setBorder(BorderFactory.createEmptyBorder(15, 10, 10, 10));

        JPanel inputPanel = new JPanel(new FlowLayout());
        inputPanel.setBackground(new Color(255, 255, 255, 0));

        JTextField field = new JTextField("2", 3);
        field.setFont(new Font("Arial", Font.PLAIN, 18));
        field.setHorizontalAlignment(SwingConstants.CENTER);
        field.setBackground(panelColor);
        field.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 50), 2));

        JButton ok = new JButton("MULAI");
        ok.setFont(new Font("Arial", Font.BOLD, 14));
        ok.setBackground(new Color(50, 50, 50));
        ok.setForeground(Color.WHITE);
        ok.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255), 2));
        ok.setPreferredSize(new Dimension(100, 40));

        final int[] result = {0};

        ok.addActionListener(e -> {
            try {
                int val = Integer.parseInt(field.getText().trim());
                if (val >= 2 && val <= 4) {
                    result[0] = val;
                    dialog.dispose();
                } else {
                    JOptionPane.showMessageDialog(dialog, "Masukkan angka antara 2 dan 4.", "Kesalahan Input", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ignored) {
                JOptionPane.showMessageDialog(dialog, "Input tidak valid.", "Kesalahan Input", JOptionPane.ERROR_MESSAGE);
            }
        });

        inputPanel.add(field);
        inputPanel.add(Box.createHorizontalStrut(20));
        inputPanel.add(ok);

        dialog.add(label, BorderLayout.NORTH);
        dialog.add(inputPanel, BorderLayout.CENTER);

        dialog.setVisible(true);
        return result[0];
    }

    private List<String> showPlayerNamesDialog(int count) {

        JDialog dialog = new JDialog(this, "Pilih Karakter Pokémon", true);
        dialog.setLayout(new BorderLayout());

        Color bgColor = new Color(40, 120, 255);
        Color fgColor = Color.YELLOW;

        dialog.getContentPane().setBackground(bgColor);

        JPanel selectionPanel = new JPanel();
        selectionPanel.setLayout(new GridLayout(count, 2, 15, 15));
        selectionPanel.setBackground(bgColor);
        selectionPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        JComboBox<String>[] selectors = new JComboBox[count];

        PokemonRenderer renderer = new PokemonRenderer(POKEMON_NAMES, POKEMON_FILES, CHARACTER_BASE_PATH);

        for (int i = 0; i < count; i++) {
            JLabel lbl = new JLabel("Pemain " + (i + 1) + " Pilih:");
            lbl.setForeground(fgColor);
            lbl.setFont(new Font("Arial", Font.BOLD, 14));

            JComboBox<String> cb = new JComboBox<>(POKEMON_NAMES);
            cb.setRenderer(renderer);
            cb.setFont(new Font("Arial", Font.PLAIN, 14));
            cb.setBackground(Color.WHITE);
            cb.setPreferredSize(new Dimension(150, 40));
            selectors[i] = cb;

            selectionPanel.add(lbl);
            selectionPanel.add(cb);
        }

        JButton ok = new JButton("MULAI PERMAINAN");
        ok.setFont(new Font("Arial", Font.BOLD, 14));
        ok.setBackground(Color.YELLOW);
        ok.setForeground(new Color(50, 50, 50));
        ok.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 50), 2));
        ok.setPreferredSize(new Dimension(200, 45));


        List<String> selectedNames = new ArrayList<>();

        ok.addActionListener(e -> {
            selectedNames.clear();
            Set<String> chosen = new HashSet<>();

            boolean allUnique = true;
            for (JComboBox<String> selector : selectors) {
                String name = (String) selector.getSelectedItem();
                if (chosen.contains(name)) {
                    allUnique = false;
                    break;
                }
                chosen.add(name);
                selectedNames.add(name);
            }

            if (!allUnique) {
                JOptionPane.showMessageDialog(dialog, "Setiap pemain harus memilih Pokémon yang berbeda!", "Kesalahan Pilihan", JOptionPane.ERROR_MESSAGE);
                selectedNames.clear();
            } else {
                dialog.dispose();
            }
        });

        dialog.add(selectionPanel, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setBackground(bgColor);
        bottom.add(ok);
        dialog.add(bottom, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        return selectedNames.isEmpty() ? List.of() : selectedNames;
    }

    private class PokemonRenderer extends DefaultListCellRenderer {
        private String[] displayNames;
        private Map<String, ImageIcon> iconCache = new HashMap<>();

        public PokemonRenderer(String[] displayNames, String[] fileNames, String basePath) {
            this.displayNames = displayNames;

            for (int i = 0; i < displayNames.length; i++) {
                String path = basePath + fileNames[i];

                try {
                    Image image = ImageIO.read(new File(path));

                    if (image != null) {
                        Image scaledImage = image.getScaledInstance(32, 32, Image.SCALE_SMOOTH);
                        iconCache.put(displayNames[i], new ImageIcon(scaledImage));
                    } else {
                        System.err.println("Gagal memuat ikon Pokémon (Image is NULL): " + path);
                        iconCache.put(displayNames[i], null);
                    }

                } catch (IOException e) {
                    System.err.println("Gagal memuat ikon Pokémon (IOException): " + path);
                    iconCache.put(displayNames[i], null);
                }
            }
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value != null) {
                String name = (String) value;
                label.setText(" " + name);
                label.setIcon(iconCache.get(name));
                label.setHorizontalAlignment(LEFT);
            }
            return label;
        }
    }


    private void showEndGameDialog(String winnerName, String scoreBoardText) {
        JDialog dialog = new JDialog(this, "Game Selesai", true);
        dialog.setSize(380, 260);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(new Color(70, 40, 20));
        dialog.setLocationRelativeTo(this);

        JLabel label = new JLabel("🎉 " + winnerName + " MENANG!");
        label.setForeground(new Color(255, 240, 210));
        label.setFont(new Font("Monospaced", Font.BOLD, 18));
        label.setHorizontalAlignment(SwingConstants.CENTER);

        JTextArea scoreArea = new JTextArea(scoreBoardText);
        scoreArea.setEditable(false);
        scoreArea.setBackground(new Color(110, 70, 40));
        scoreArea.setForeground(new Color(255, 245, 220));
        scoreArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        scoreArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane scroll = new JScrollPane(scoreArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JButton ok = new JButton("OK");
        ok.setFont(new Font("Monospaced", Font.BOLD, 14));
        ok.setBackground(new Color(120, 80, 50));
        ok.setForeground(Color.WHITE);
        ok.setBorder(BorderFactory.createLineBorder(new Color(160, 110, 70), 2));
        ok.addActionListener(e -> dialog.dispose());

        dialog.add(label, BorderLayout.NORTH);
        dialog.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setBackground(new Color(70, 40, 20));
        bottom.add(ok);
        dialog.add(bottom, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    // ================== INIT GAME ==================

    private void initGame() {
        int nPlayers = showPlayerCountDialog();
        if (nPlayers == 0) System.exit(0);

        List<String> names = showPlayerNamesDialog(nPlayers);
        if (names.isEmpty()) System.exit(0);

        Color[] tokenColors = {
                new Color(80, 200, 255), // Biru Muda
                new Color(255, 230, 80),  // Kuning
                new Color(255, 120, 180), // Pink
                new Color(120, 255, 120)  // Hijau
        };

        players.clear();
        turnQueue.clear();

        for (int i = 0; i < nPlayers; i++) {
            BoardEditor.Player p = new BoardEditor.Player(names.get(i), tokenColors[i]);
            players.add(p);
            turnQueue.offer(p);
        }

        board = new BoardEditor.BoardGraph(BOARD_SIZE);
        dice = new BoardEditor.Dice();

        playerScores.clear();
        for (BoardEditor.Player p : players) playerScores.put(p, 0);

        initScores();

        currentPlayer = turnQueue.poll();
    }

    // ================== RESET GAME STATE ==================

    private void resetGameState() {
        if (moveTimer != null && moveTimer.isRunning()) moveTimer.stop();

        for (BoardEditor.Player p : players) {
            p.position = 1;
            p.moveHistory.clear();
        }

        turnQueue.clear();
        for (BoardEditor.Player p : players) turnQueue.offer(p);
        currentPlayer = turnQueue.poll();

        gameOver = false;
        btnRoll.setEnabled(true);
        dicePanel.setDice(0, true);
        lblDiceText.setText("Dadu: -");
        lblDiceText.setForeground(new Color(240, 220, 190));
        lblStatus.setText("Status: Game di-reset.");

        playerScores.clear();
        for (BoardEditor.Player p : players) playerScores.put(p, 0);
        initScores();

        historyArea.setText("");
        appendHistory("Game di-reset. Peta bajak laut 1..64.");
        appendHistory("Giliran pertama: " + currentPlayer.name + ".");

        updateTurnLabel();
        updateLeaderboard();
        boardPanel.repaint();

        SoundManager.playGameStart();
    }

    // ================== INIT UI ==================

    public void initUI() {
        boardPanel = new BoardPanel();

        JPanel boardWrapper = new JPanel(new GridBagLayout());
        boardWrapper.setBackground(new Color(70, 40, 20));
        boardWrapper.setPreferredSize(BOARD_DIM);
        boardWrapper.setMinimumSize(BOARD_DIM);
        boardWrapper.add(boardPanel);

        JPanel controlPanel = new JPanel();
        controlPanel.setBackground(new Color(110, 70, 40));
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        controlPanel.setPreferredSize(new Dimension(380, BOARD_DIM.height));

        lblTurn = new JLabel();
        lblTurn.setForeground(new Color(255, 245, 220));
        lblTurn.setFont(new Font("Monospaced", Font.BOLD, 16));

        dicePanel = new DicePanel();
        dicePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblDiceText = new JLabel("Dadu: -");
        lblDiceText.setForeground(new Color(240, 220, 190));
        lblDiceText.setFont(new Font("Monospaced", Font.BOLD, 14));

        JLabel lblLeaderboard = new JLabel("Leaderboard:");
        lblLeaderboard.setForeground(new Color(255, 245, 220));
        lblLeaderboard.setFont(new Font("Monospaced", Font.BOLD, 13));

        leaderboardArea = new JTextArea(5, 22);
        leaderboardArea.setEditable(false);
        leaderboardArea.setBackground(new Color(150, 110, 70));
        leaderboardArea.setForeground(new Color(255, 245, 225));
        leaderboardArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        leaderboardArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        leaderboardArea.setLineWrap(true);
        leaderboardArea.setWrapStyleWord(true);

        lblStatus = new JLabel("Status: Game dimulai.");
        lblStatus.setForeground(new Color(240, 220, 190));
        lblStatus.setFont(new Font("Monospaced", Font.PLAIN, 13));

        btnRoll = new JButton("Lempar Dadu");
        btnRoll.setFont(new Font("Monospaced", Font.BOLD, 14));
        btnRoll.setBackground(new Color(130, 90, 60));
        btnRoll.setForeground(Color.WHITE);
        btnRoll.setFocusPainted(false);
        btnRoll.setBorder(BorderFactory.createLineBorder(new Color(180, 130, 90), 2));
        btnRoll.addActionListener(e -> onRollDice());

        btnReset = new JButton("Reset Game");
        btnReset.setFont(new Font("Monospaced", Font.BOLD, 13));
        btnReset.setBackground(new Color(150, 80, 60));
        btnReset.setForeground(Color.WHITE);
        btnReset.setFocusPainted(false);
        btnReset.setBorder(BorderFactory.createLineBorder(new Color(190, 120, 90), 2));
        btnReset.addActionListener(e -> resetGameState());

        // ---- SETUP SLIDER VOLUME ----
        JLabel lblVolume = new JLabel("Volume BGM:");
        lblVolume.setForeground(new Color(240, 220, 190));
        lblVolume.setFont(new Font("Monospaced", Font.BOLD, 13));

        // Range: -50 dB (min) s/d 6 dB (max), awal -10 dB
        volSlider = new JSlider(-50, 6, -10);
        volSlider.setBackground(new Color(110, 70, 40));
        volSlider.setOpaque(false);
        volSlider.setPreferredSize(new Dimension(150, 20));
        volSlider.setAlignmentX(Component.LEFT_ALIGNMENT);

        volSlider.addChangeListener(e -> {
            float val = volSlider.getValue();
            // Jika slider mentok kiri, anggap mute (-80 dB)
            if (val == -50) val = -80.0f;
            SoundManager.setBGMVolume(val);
        });
        // -----------------------------

        JLabel lblHistory = new JLabel("Riwayat Langkah:");
        lblHistory.setForeground(new Color(255, 245, 220));
        lblHistory.setFont(new Font("Monospaced", Font.BOLD, 13));

        historyArea = new JTextArea(15, 25);
        historyArea.setEditable(false);
        historyArea.setBackground(new Color(170, 130, 90));
        historyArea.setForeground(new Color(255, 245, 225));
        historyArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        historyArea.setLineWrap(true);
        historyArea.setWrapStyleWord(true);

        JScrollPane scrollHistory = new JScrollPane(historyArea);
        scrollHistory.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollHistory.setPreferredSize(new Dimension(340, 260));

        controlPanel.add(lblTurn);
        controlPanel.add(Box.createVerticalStrut(10));
        controlPanel.add(dicePanel);
        controlPanel.add(Box.createVerticalStrut(5));
        controlPanel.add(lblDiceText);
        controlPanel.add(Box.createVerticalStrut(8));
        controlPanel.add(lblLeaderboard);
        controlPanel.add(Box.createVerticalStrut(2));
        controlPanel.add(leaderboardArea);
        controlPanel.add(Box.createVerticalStrut(10));
        controlPanel.add(lblStatus);
        controlPanel.add(Box.createVerticalStrut(10));
        controlPanel.add(lblHistory);
        controlPanel.add(Box.createVerticalStrut(5));
        controlPanel.add(scrollHistory);
        controlPanel.add(Box.createVerticalStrut(10));
        controlPanel.add(btnRoll);
        controlPanel.add(Box.createVerticalStrut(5));
        controlPanel.add(btnReset);

        // Tambahkan Komponen Slider ke Panel
        controlPanel.add(Box.createVerticalStrut(15));
        controlPanel.add(lblVolume);
        controlPanel.add(Box.createVerticalStrut(5));
        controlPanel.add(volSlider);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(70, 40, 20));
        root.add(boardWrapper, BorderLayout.CENTER);
        root.add(controlPanel, BorderLayout.EAST);

        setContentPane(root);

        updateTurnLabel();
        updateLeaderboard();
        appendHistory("Game dimulai. Peta bajak laut 1..64.");
        appendHistory("Giliran pertama: " + currentPlayer.name + ".");
    }

    private void appendHistory(String text) {
        historyArea.append(text + "\n");
        historyArea.setCaretPosition(historyArea.getDocument().getLength());
    }

    // ================== EVENT: ROLL DICE ==================

    private void onRollDice() {
        if (gameOver) return;
        if (moveTimer != null && moveTimer.isRunning()) return;
        if (currentPlayer == null) currentPlayer = turnQueue.poll();
        if (!boardPanel.positionsLoaded) {
            lblStatus.setText("Status: ERROR - Posisi Node Belum Dimuat! Jalankan Editor.");
            return;
        }

        btnRoll.setEnabled(false);

        final int[] ticks = {0};
        final int maxTicks = 10;

        javax.swing.Timer rollAnimTimer = new javax.swing.Timer(80, null);
        rollAnimTimer.addActionListener(ev -> {
            ticks[0]++;
            int fakeVal = diceAnimRandom.nextInt(6) + 1;
            boolean fakePos = diceAnimRandom.nextBoolean();
            dicePanel.setDice(fakeVal, fakePos);

            if (ticks[0] >= maxTicks) {
                rollAnimTimer.stop();
                doRealDiceRoll();
            }
        });
        rollAnimTimer.start();
    }

    private void doRealDiceRoll() {
        int diceNumber = dice.rollNumber();
        boolean positive = dice.isPositive();

        currentDiceNumber = diceNumber;
        currentDicePositive = positive;

        useShortestThisRoll = positive && isPrime(currentPlayer.position);

        String warnaText = positive ? "HIJAU (maju)" : "MERAH (mundur)";
        lblDiceText.setText("Dadu: " + diceNumber + " | " + warnaText +
                (useShortestThisRoll ? " | PRIME BOOST: Shortest Path" : ""));
        lblDiceText.setForeground(positive ? new Color(210, 250, 200) : new Color(255, 190, 170));

        SoundManager.playDice();
        dicePanel.setDice(diceNumber, positive);

        startAnimatedMove(diceNumber, positive);
    }

    // ================== ANIMASI GERAK ==================

    private void startAnimatedMove(int diceNumber, boolean positive) {
        stepsLeft = diceNumber;
        movePositive = positive;
        moveInitialPos = currentPlayer.position;

        moveTimer = new javax.swing.Timer(220, e -> {
            if (stepsLeft <= 0) {
                moveTimer.stop();
                finishMoveAfterAnimation();
                return;
            }

            if (movePositive) {
                if (currentPlayer.position >= BOARD_SIZE) {
                    stepsLeft = 0;
                } else {
                    stepForward(board, currentPlayer, useShortestThisRoll);
                    stepsLeft--;
                }
            } else {
                if (currentPlayer.moveHistory.isEmpty()) {
                    stepsLeft = 0;
                } else {
                    stepBackward(currentPlayer);
                    stepsLeft--;
                }
            }

            boardPanel.repaint();
        });

        moveTimer.start();
    }

    private void finishMoveAfterAnimation() {
        int posAfterMove = currentPlayer.position;

        StringBuilder historyText = new StringBuilder();
        historyText.append(currentPlayer.name)
                .append(" melempar dadu: ")
                .append(currentDiceNumber)
                .append(" (").append(currentDicePositive ? "Hijau" : "Merah").append(")")
                .append(useShortestThisRoll ? " | PRIME BOOST aktif." : "")
                .append(". ");

        String status;

        if (movePositive) {
            status = currentPlayer.name + " maju dari " + moveInitialPos + " ke " + posAfterMove;
        } else {
            if (posAfterMove == moveInitialPos) {
                status = currentPlayer.name + " tidak bisa mundur lagi (stack kosong).";
            } else {
                status = currentPlayer.name + " mundur dari " + moveInitialPos + " ke " + posAfterMove;
            }
        }

        int finalPos = currentPlayer.position;
        boolean gotStar = isStarPosition(finalPos);

        if (!nodeClaimed[finalPos] && nodeScores[finalPos] > 0) {
            nodeClaimed[finalPos] = true;
            int gained = nodeScores[finalPos];
            int newTotal = getScore(currentPlayer) + gained;
            playerScores.put(currentPlayer, newTotal);

            String scoreMsg = " | SCORE: +" + gained + " (total " + newTotal + ")";
            status += scoreMsg;
            historyText.append(scoreMsg);
        }

        if (gotStar && finalPos < BOARD_SIZE) {
            status += " ★ BONUS! Posisi bintang (kelipatan 5), dapat giliran lagi.";
        }

        lblStatus.setText("Status: " + status);
        historyText.append(". ");
        appendHistory(historyText.toString());

        if (finalPos >= BOARD_SIZE) {
            gameOver = true;
            lblStatus.setText("Status: " + currentPlayer.name + " MENANG!");
            appendHistory("🎉 " + currentPlayer.name + " MENANG! Mencapai node " + finalPos + ".");

            String scoreBoard = buildScoreBoardText();

            SoundManager.stopBGM();
            SoundManager.playWinner();

            showEndGameDialog(currentPlayer.name, scoreBoard);
            btnRoll.setEnabled(false);
        } else {
            if (gotStar && finalPos < BOARD_SIZE) {
                appendHistory(currentPlayer.name + " mendapat BONUS TURN karena di posisi bintang!");
                btnRoll.setEnabled(true);
            } else {
                turnQueue.offer(currentPlayer);
                currentPlayer = turnQueue.poll();
                appendHistory("Giliran berikutnya: " + currentPlayer.name + ".");
                btnRoll.setEnabled(true);
            }
        }

        updateTurnLabel();
        updateLeaderboard();
        boardPanel.repaint();
    }

    private int stepForward(BoardEditor.BoardGraph board, BoardEditor.Player player, boolean useShortest) {
        int pos = player.position;
        if (pos >= board.size) return pos;
        player.moveHistory.push(pos);

        int newPos = useShortest ? board.getNextOnShortestPath(pos) : board.getNextForward(pos);
        if (newPos > board.size) newPos = board.size;
        if (newPos <= 0) newPos = Math.min(board.size, pos + 1);

        player.position = newPos;
        return newPos;
    }

    private int stepBackward(BoardEditor.Player player) {
        if (player.moveHistory.isEmpty()) return player.position;
        int newPos = player.moveHistory.pop();
        player.position = newPos;
        return newPos;
    }

    private void updateTurnLabel() {
        if (currentPlayer != null && !gameOver) {
            lblTurn.setText("Giliran: " + currentPlayer.name +
                    "  (Posisi: " + currentPlayer.position +
                    ", Skor: " + getScore(currentPlayer) + ")");
        } else if (gameOver) {
            lblTurn.setText("Game selesai.");
        } else {
            lblTurn.setText("Menunggu giliran...");
        }
    }

    // ================== PANEL BOARD (PIRATE MAP) ==================

    private class BoardPanel extends JPanel {

        private Point[] centers;
        private boolean positionsLoaded = false;
        private Image backgroundImage;
        private final int nodeR = 10;

        BoardPanel() {
            setBackground(new Color(70, 40, 20));
            setPreferredSize(BOARD_DIM);
            setMinimumSize(BOARD_DIM);

            loadBackgroundImage("Background Board/bgboard.png");

            if (!loadNodePositions()) {
                System.err.println("Gagal memuat posisi node dari " + POSITION_FILE + ". Jalur tidak bisa ditampilkan.");
            }
        }

        private void loadBackgroundImage(String path) {
            try {
                backgroundImage = ImageIO.read(new File(path));
            } catch (IOException e) {
                System.err.println("Gagal memuat background gambar dari jalur: " + path);
                System.err.println("Pastikan file 'bgboard.png' ada di folder 'Background Board'.");
                backgroundImage = null;
            }
        }

        private boolean loadNodePositions() {
            File file = new File(POSITION_FILE);
            if (!file.exists()) {
                positionsLoaded = false;
                return false;
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                Point[] loadedCenters = new Point[BOARD_SIZE + 1];
                loadedCenters[0] = new Point(0, 0);
                String line;
                int i = 1;
                while ((line = reader.readLine()) != null && i <= BOARD_SIZE) {
                    String[] parts = line.split(",");
                    if (parts.length == 2) {
                        loadedCenters[i] = new Point(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                        i++;
                    }
                }
                if (i > BOARD_SIZE) {
                    centers = loadedCenters;
                    positionsLoaded = true;
                    return true;
                }
            } catch (Exception e) {
                positionsLoaded = false;
                return false;
            }
            positionsLoaded = false;
            return false;
        }


        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            if (backgroundImage != null) {
                g2.drawImage(backgroundImage, 0, 0, w, h, this);
            } else {
                Color seaColor = new Color(20, 70, 110);
                g2.setColor(seaColor);
                g2.fillRect(0, 0, w, h);
            }

            if (!positionsLoaded || centers == null) {
                g2.setColor(Color.RED);
                g2.setFont(new Font("Monospaced", Font.BOLD, 18));
                g2.drawString("ERROR: Posisi Node Belum Dimuat!", 50, h / 2);
                g2.drawString("Jalankan BoardEditor.java dan Simpan", 50, h / 2 + 30);
                g2.dispose();
                return;
            }

            Stroke oldStroke = g2.getStroke();
            g2.setColor(new Color(245, 245, 245, 220));
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    1f, new float[]{8f, 10f}, 0f));
            for (int i = 1; i < BOARD_SIZE; i++) {
                Point a = centers[i];
                Point b = centers[i + 1];
                g2.drawLine(a.x, a.y, b.x, b.y);
            }

            g2.setColor(new Color(255, 220, 80));
            g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int[] e : board.getExtraLinks()) {
                int aIdx = e[0];
                int bIdx = e[1];
                Point pa = centers[aIdx];
                Point pb = centers[bIdx];
                if (aIdx > bIdx) {
                    int t = aIdx; aIdx = bIdx; bIdx = t;
                    Point tp = pa; pa = pb; pb = tp;
                }
                drawArrowLine(g2, pa, pb, nodeR + 4, 10);
            }
            g2.setStroke(oldStroke);

            Font numFont = new Font("Monospaced", Font.BOLD, 11);
            Font scoreFont = new Font("Monospaced", Font.PLAIN, 9);

            for (int pos = 1; pos <= BOARD_SIZE; pos++) {
                Point c = centers[pos];
                int nx = c.x;
                int ny = c.y;

                g2.setColor(new Color(140, 100, 60));
                g2.fillOval(nx - nodeR, ny - nodeR, 2 * nodeR, 2 * nodeR);

                g2.setColor(new Color(235, 215, 175));
                g2.fillOval(nx - (int) (nodeR * 0.7), ny - (int) (nodeR * 0.7),
                        (int) (2 * nodeR * 0.7), (int) (2 * nodeR * 0.7));

                g2.setColor(new Color(90, 60, 35));
                g2.drawOval(nx - nodeR, ny - nodeR, 2 * nodeR, 2 * nodeR);

                g2.setFont(numFont);
                g2.setColor(new Color(60, 35, 20));
                String label = String.valueOf(pos);
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(label);
                int th = fm.getAscent();
                g2.drawString(label, nx - tw / 2, ny + th / 3);

                int nodeScore = nodeScores[pos];
                if (nodeScore > 0 && !nodeClaimed[pos]) {
                    g2.setFont(scoreFont);
                    g2.setColor(new Color(20, 70, 30));
                    String sText = "+" + nodeScore;
                    g2.drawString(sText, nx - nodeR, ny + nodeR + 10);
                }

                if (isStarPosition(pos)) {
                    drawStar(g2, nx + nodeR + 5, ny - nodeR - 3, Math.max(6, nodeR / 2));
                }

                if (isPrime(pos)) {
                    g2.setColor(new Color(255, 190, 0, 220));
                    g2.fillOval(nx + nodeR - 4, ny + nodeR - 4, 6, 6);
                }
            }

            int tokenR = nodeR + 2;
            int offset = Math.max(3, tokenR / 2);
            int tokenSize = (nodeR * 2) + 6;

            for (BoardEditor.Player p : players) {
                int pos = Math.max(1, Math.min(BOARD_SIZE, p.position));
                Point c = centers[pos];

                Image playerIcon = getPlayerTokenImage(p.name, tokenSize);

                int idx = players.indexOf(p);
                int dx = (idx % 2) * offset * 2 - offset;
                int dy = (idx / 2) * offset * 2 - offset;

                int cx = c.x + dx;
                int cy = c.y + dy;

                int drawX = cx - tokenSize / 2;
                int drawY = cy - tokenSize / 2;

                if (playerIcon != null) {
                    g2.drawImage(playerIcon, drawX, drawY, tokenSize, tokenSize, this);

                    g2.setColor(p.tokenColor);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawOval(drawX, drawY, tokenSize, tokenSize);

                } else {
                    g2.setColor(p.tokenColor);
                    g2.fillOval(cx - tokenR, cy - tokenR, 2 * tokenR, 2 * tokenR);

                    g2.setColor(Color.BLACK);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawOval(cx - tokenR, cy - tokenR, 2 * tokenR, 2 * tokenR);
                }
            }

            g2.setFont(new Font("Monospaced", Font.BOLD, 14));
            g2.setColor(new Color(60, 35, 20));
            Point startP = centers[1];
            Point endP = centers[BOARD_SIZE];

            g2.drawString("START", startP.x - 20, startP.y + nodeR + 20);

            drawStar(g2, endP.x, endP.y - 15, 15);
            g2.drawString("FINISH", endP.x - 25, endP.y + nodeR + 20);


            g2.dispose();
        }

        private Image getPlayerTokenImage(String name, int size) {
            for(int i = 0; i < POKEMON_NAMES.length; i++){
                if(POKEMON_NAMES[i].equals(name)){
                    String path = CHARACTER_BASE_PATH + POKEMON_FILES[i];
                    try {
                        Image image = ImageIO.read(new File(path));
                        return image.getScaledInstance(size, size, Image.SCALE_SMOOTH);
                    } catch (IOException e) {
                        return null;
                    }
                }
            }
            return null;
        }

        private void drawStar(Graphics2D g2, int cx, int cy, int r) {
            g2.setColor(new Color(255, 215, 120));
            int points = 10;
            int[] xs = new int[points];
            int[] ys = new int[points];
            double angle = -Math.PI / 2;
            for (int i = 0; i < points; i++) {
                double radius = (i % 2 == 0) ? r : r / 2.5;
                xs[i] = cx + (int) (Math.cos(angle) * radius);
                ys[i] = cy + (int) (Math.sin(angle) * radius);
                angle += Math.PI / 5;
            }
            g2.fillPolygon(xs, ys, points);
        }

        private void drawArrowLine(Graphics2D g2, Point from, Point to, int shrink, int headSize) {
            double ang = Math.atan2(to.y - from.y, to.x - from.x);

            int x1 = from.x + (int) (Math.cos(ang) * shrink);
            int y1 = from.y + (int) (Math.sin(ang) * shrink);
            int x2 = to.x - (int) (Math.cos(ang) * shrink);
            int y2 = to.y - (int) (Math.sin(ang) * shrink);

            g2.drawLine(x1, y1, x2, y2);

            int hx = x2;
            int hy = y2;
            int xL = hx - (int) (Math.cos(ang - Math.PI / 6) * headSize);
            int yL = hy - (int) (Math.sin(ang - Math.PI / 6) * headSize);
            int xR = hx - (int) (Math.cos(ang + Math.PI / 6) * headSize);
            int yR = hy - (int) (Math.sin(ang + Math.PI / 6) * headSize);

            int[] xs = {hx, xL, xR};
            int[] ys = {hy, yL, yR};
            g2.fillPolygon(xs, ys, 3);
        }
    }

    // ================== PANEL DADU ==================

    private static class DicePanel extends JPanel {
        private int value = 0;
        private boolean positive = true;

        private double shakeOffset = 0;
        private double scale = 1.0;
        private javax.swing.Timer shakeTimer;
        private javax.swing.Timer bounceTimer;

        DicePanel() {
            setPreferredSize(new Dimension(60, 60));
            setBackground(new Color(110, 70, 40));
        }

        void setDice(int value, boolean positive) {
            this.value = value;
            this.positive = positive;
            startShake();
        }

        private void startShake() {
            if (shakeTimer != null && shakeTimer.isRunning()) shakeTimer.stop();
            if (bounceTimer != null && bounceTimer.isRunning()) bounceTimer.stop();

            shakeOffset = 0;

            shakeTimer = new javax.swing.Timer(30, e -> {
                shakeOffset = (Math.random() - 0.5) * 8;
                repaint();
            });
            shakeTimer.start();

            javax.swing.Timer stopShake = new javax.swing.Timer(280, e -> {
                shakeTimer.stop();
                shakeOffset = 0;
                startBounce();
            });
            stopShake.setRepeats(false);
            stopShake.start();
        }

        private void startBounce() {
            if (bounceTimer != null && bounceTimer.isRunning()) bounceTimer.stop();
            scale = 1.4;

            bounceTimer = new javax.swing.Timer(30, e -> {
                scale -= 0.07;
                if (scale <= 1.0) {
                    scale = 1.0;
                    bounceTimer.stop();
                }
                repaint();
            });
            bounceTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            int size = (int) ((Math.min(w, h) - 14) * scale);
            int x = (w - size) / 2 + (int) shakeOffset;
            int y = (h - size) / 2;

            Color base = positive ? new Color(45, 160, 45) : new Color(200, 60, 60);

            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillRoundRect(x + 4, y + 6, size, size, 20, 20);

            g2.setColor(base);
            g2.fillRoundRect(x, y, size, size, 20, 20);

            g2.setColor(Color.WHITE);
            int r = size / 9;

            int cx = x + size / 2;
            int cy = y + size / 2;

            int left = x + size / 4;
            int right = x + 3 * size / 4;
            int top = y + size / 4;
            int bottom = y + 3 * size / 4;

            switch (value) {
                case 1 -> drawPip(g2, cx, cy, r);
                case 2 -> { drawPip(g2, left, top, r); drawPip(g2, right, bottom, r); }
                case 3 -> { drawPip(g2, left, top, r); drawPip(g2, cx, cy, r); drawPip(g2, right, bottom, r); }
                case 4 -> {
                    drawPip(g2, left, top, r);
                    drawPip(g2, right, top, r);
                    drawPip(g2, left, bottom, r);
                    drawPip(g2, right, bottom, r);
                }
                case 5 -> {
                    drawPip(g2, left, top, r);
                    drawPip(g2, right, top, r);
                    drawPip(g2, cx, cy, r);
                    drawPip(g2, left, bottom, r);
                    drawPip(g2, right, bottom, r);
                }
                case 6 -> {
                    drawPip(g2, left, top, r);
                    drawPip(g2, right, top, r);
                    drawPip(g2, left, cy, r);
                    drawPip(g2, right, cy, r);
                    drawPip(g2, left, bottom, r);
                    drawPip(g2, right, bottom, r);
                }
            }

            g2.dispose();
        }

        private void drawPip(Graphics2D g2, int x, int y, int r) {
            g2.fillOval(x - r, y - r, 2 * r, 2 * r);
        }
    }
}
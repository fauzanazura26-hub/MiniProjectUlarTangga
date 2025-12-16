import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.sound.sampled.*;
import java.net.URL;
public class SnakeLadder extends JFrame {

    // ================== SOUND MANAGER (INNER CLASS) ==================
    private static class SoundManager {

        private static Clip startClip;
        private static Clip bgmClip;
        private static Clip diceClip;
        private static Clip winnerClip;

        static {
            startClip  = loadClip("Audio/start.wav");   // sound start game
            bgmClip    = loadClip("Audio/backsound.wav");     // background music looping
            diceClip   = loadClip("Audio/rollDice.wav");    // efek roll dice
            winnerClip = loadClip("Audio/winner.wav");  // efek ketika menang
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
                System.err.println("Error loading sound: " + resourceName);
                return null;
            }
        }

        private static void playOnce(Clip clip) {
            if (clip == null) return;
            if (clip.isRunning()) clip.stop();
            clip.setFramePosition(0);
            clip.start();
        }

        // Sound: start game
        public static void playGameStart() {
            playOnce(startClip);
        }

        // Sound: roll dice
        public static void playDice() {
            playOnce(diceClip);
        }

        // Sound: winner
        public static void playWinner() {
            playOnce(winnerClip);
        }

        // BGM: mulai looping
        public static void playBGM() {
            if (bgmClip == null) return;
            if (bgmClip.isRunning()) return;
            bgmClip.setFramePosition(0);
            bgmClip.loop(Clip.LOOP_CONTINUOUSLY);
        }

        // BGM: stop
        public static void stopBGM() {
            if (bgmClip != null && bgmClip.isRunning()) {
                bgmClip.stop();
            }
        }
    }

    // ================== KONFIGURASI BOARD ==================
    private static final int BOARD_SIZE = 64;
    private static final int ROWS = 8;
    private static final int COLS = 8;
    private static final Dimension BOARD_DIM = new Dimension(720, 720);

    // ================== DATA GAME ==================
    private List<Player> players = new ArrayList<>();
    private Queue<Player> turnQueue = new ArrayDeque<>();
    private Player currentPlayer;

    private BoardGraph board;
    private Dice dice;

    private boolean gameOver = false;

    // --------- STATE ANIMASI GERAK -----------
    private javax.swing.Timer moveTimer;
    private int stepsLeft;
    private boolean movePositive;
    private int moveInitialPos;
    private int currentDiceNumber;
    private boolean currentDicePositive;
    private boolean useShortestThisRoll; // aktif kalau posisi awal prima & dadu hijau

    // ================== KOMPONEN GUI ==================
    private BoardPanel boardPanel;
    private JLabel lblTurn;
    private JLabel lblDiceText;
    private JLabel lblStatus;
    private JButton btnRoll;
    private JButton btnReset;
    private JTextArea historyArea;
    private DicePanel dicePanel;

    // ================== MAIN ==================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SnakeLadder().setVisible(true));
    }

    public SnakeLadder() {
        setTitle("Snake & Ladder - Wooden Edition (Animated, 8x8)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        initGame();
        initUI();

        pack();
        setMinimumSize(new Dimension(BOARD_DIM.width + 420, BOARD_DIM.height + 80));
        setLocationRelativeTo(null);

        // SOUND: mulai game + nyalakan BGM
        SoundManager.playGameStart();
        SoundManager.playBGM();
    }

    // ================== UTIL: PRIMA & BINTANG ==================

    /** Posisi bintang: setiap kelipatan 5 (5, 10, ..., 64) */
    private boolean isStarPosition(int n) {
        return n > 0 && n % 5 == 0;
    }

    /** Cek bilangan prima (untuk prime boost) */
    private static boolean isPrime(int n) {
        if (n <= 1) return false;
        if (n <= 3) return true;
        if (n % 2 == 0 || n % 3 == 0) return false;
        for (int i = 5; i * i <= n; i += 6) {
            if (n % i == 0 || n % (i + 2) == 0) return false;
        }
        return true;
    }

    // ================== CUSTOM DIALOGS ==================

    private int showPlayerCountDialog() {
        JDialog dialog = new JDialog(this, "Jumlah Pemain", true);
        dialog.setSize(350, 200);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(new Color(70, 40, 20));
        dialog.setLocationRelativeTo(this);

        JLabel label = new JLabel("Masukkan jumlah pemain (2 - 4):");
        label.setForeground(new Color(255, 240, 210));
        label.setFont(new Font("Monospaced", Font.BOLD, 14));
        label.setHorizontalAlignment(SwingConstants.CENTER);

        JTextField field = new JTextField();
        field.setFont(new Font("Monospaced", Font.PLAIN, 14));
        field.setBackground(new Color(150, 110, 70));
        field.setForeground(new Color(255, 245, 225));
        field.setBorder(BorderFactory.createLineBorder(new Color(110, 70, 40), 2));

        JButton ok = new JButton("OK");
        ok.setFont(new Font("Monospaced", Font.BOLD, 14));
        ok.setBackground(new Color(120, 80, 50));
        ok.setForeground(Color.WHITE);
        ok.setBorder(BorderFactory.createLineBorder(new Color(160, 110, 70), 2));

        final int[] result = {0};

        ok.addActionListener(e -> {
            try {
                int val = Integer.parseInt(field.getText().trim());
                if (val >= 2 && val <= 4) {
                    result[0] = val;
                    dialog.dispose();
                }
            } catch (Exception ignored) {}
        });

        dialog.add(label, BorderLayout.NORTH);
        dialog.add(field, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setBackground(new Color(70, 40, 20));
        bottom.add(ok);
        dialog.add(bottom, BorderLayout.SOUTH);

        dialog.setVisible(true);
        return result[0];
    }

    private List<String> showPlayerNamesDialog(int count) {
        JDialog dialog = new JDialog(this, "Nama Pemain", true);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(new Color(70, 40, 20));

        JPanel fieldPanel = new JPanel();
        fieldPanel.setLayout(new GridLayout(count, 1, 10, 10));
        fieldPanel.setBackground(new Color(70, 40, 20));
        JTextField[] fields = new JTextField[count];

        for (int i = 0; i < count; i++) {
            JTextField tf = new JTextField("Player " + (i + 1));
            tf.setFont(new Font("Monospaced", Font.PLAIN, 14));
            tf.setBackground(new Color(150, 110, 70));
            tf.setForeground(new Color(255, 245, 225));
            tf.setBorder(BorderFactory.createLineBorder(new Color(110, 70, 40), 2));
            fields[i] = tf;
            fieldPanel.add(tf);
        }

        JButton ok = new JButton("Mulai");
        ok.setFont(new Font("Monospaced", Font.BOLD, 14));
        ok.setBackground(new Color(120, 80, 50));
        ok.setForeground(Color.WHITE);
        ok.setBorder(BorderFactory.createLineBorder(new Color(160, 110, 70), 2));

        List<String> names = new ArrayList<>();

        ok.addActionListener(e -> {
            names.clear();
            for (int i = 0; i < count; i++) {
                String name = fields[i].getText().trim();
                if (name.isEmpty()) name = "Player " + (i + 1);
                names.add(name);
            }
            dialog.dispose();
        });

        dialog.add(fieldPanel, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setBackground(new Color(70, 40, 20));
        bottom.add(ok);
        dialog.add(bottom, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        return names;
    }

    private void showEndGameDialog(String winnerName) {
        JDialog dialog = new JDialog(this, "Game Selesai", true);
        dialog.setSize(350, 200);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(new Color(70, 40, 20));
        dialog.setLocationRelativeTo(this);

        JLabel label = new JLabel("🎉 " + winnerName + " MENANG!");
        label.setForeground(new Color(255, 240, 210));
        label.setFont(new Font("Monospaced", Font.BOLD, 18));
        label.setHorizontalAlignment(SwingConstants.CENTER);

        JButton ok = new JButton("OK");
        ok.setFont(new Font("Monospaced", Font.BOLD, 14));
        ok.setBackground(new Color(120, 80, 50));
        ok.setForeground(Color.WHITE);
        ok.setBorder(BorderFactory.createLineBorder(new Color(160, 110, 70), 2));
        ok.addActionListener(e -> dialog.dispose());

        dialog.add(label, BorderLayout.CENTER);

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

        Color[] tokenColors = {
                new Color(80, 200, 255),
                new Color(255, 230, 80),
                new Color(255, 120, 180),
                new Color(120, 255, 120)
        };

        players.clear();
        turnQueue.clear();

        for (int i = 0; i < nPlayers; i++) {
            Player p = new Player(names.get(i), tokenColors[i]);
            players.add(p);
            turnQueue.offer(p);
        }

        board = new BoardGraph(BOARD_SIZE);
        dice = new Dice();

        currentPlayer = turnQueue.poll();
    }

    // ================== RESET GAME STATE ==================

    private void resetGameState() {
        if (moveTimer != null && moveTimer.isRunning()) moveTimer.stop();

        for (Player p : players) {
            p.position = 1;
            p.moveHistory.clear();
        }

        turnQueue.clear();
        for (Player p : players) turnQueue.offer(p);
        currentPlayer = turnQueue.poll();

        gameOver = false;
        btnRoll.setEnabled(true);
        dicePanel.setDice(0, true);
        lblDiceText.setText("Dadu: -");
        lblDiceText.setForeground(new Color(240, 220, 190));
        lblStatus.setText("Status: Game di-reset.");
        lblTurn.setText("Giliran: " + currentPlayer.name + "  (Posisi: " + currentPlayer.position + ")");

        historyArea.setText("");
        appendHistory("Game di-reset. Board: 1.." + BOARD_SIZE + " (zig-zag dari bawah).");
        appendHistory("Giliran pertama: " + currentPlayer.name + ".");

        boardPanel.repaint();

        // SOUND: start game lagi setelah reset
        SoundManager.playGameStart();
    }

    // ================== INIT UI ==================

    private void initUI() {
        boardPanel = new BoardPanel();

        JPanel boardWrapper = new JPanel(new GridBagLayout());
        boardWrapper.setBackground(new Color(70, 40, 20));
        boardWrapper.setPreferredSize(BOARD_DIM);
        boardWrapper.setMinimumSize(BOARD_DIM);
        boardWrapper.setMaximumSize(BOARD_DIM);
        boardWrapper.add(boardPanel);

        JPanel controlPanel = new JPanel();
        controlPanel.setBackground(new Color(110, 70, 40));
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        controlPanel.setPreferredSize(new Dimension(380, BOARD_DIM.height));
        controlPanel.setMinimumSize(new Dimension(380, BOARD_DIM.height));

        lblTurn = new JLabel();
        lblTurn.setForeground(new Color(255, 245, 220));
        lblTurn.setFont(new Font("Monospaced", Font.BOLD, 16));

        dicePanel = new DicePanel();
        dicePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblDiceText = new JLabel("Dadu: -");
        lblDiceText.setForeground(new Color(240, 220, 190));
        lblDiceText.setFont(new Font("Monospaced", Font.BOLD, 14));

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
        scrollHistory.setMinimumSize(new Dimension(340, 260));
        scrollHistory.setMaximumSize(new Dimension(340, 260));

        controlPanel.add(lblTurn);
        controlPanel.add(Box.createVerticalStrut(10));
        controlPanel.add(dicePanel);
        controlPanel.add(Box.createVerticalStrut(5));
        controlPanel.add(lblDiceText);
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

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(70, 40, 20));
        root.add(boardWrapper, BorderLayout.CENTER);
        root.add(controlPanel, BorderLayout.EAST);

        setContentPane(root);

        updateTurnLabel();
        appendHistory("Game dimulai. Board: 1.." + BOARD_SIZE + " (zig-zag dari bawah).");
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

        int diceNumber = dice.rollNumber();
        boolean positive = dice.isPositive();

        currentDiceNumber = diceNumber;
        currentDicePositive = positive;

        useShortestThisRoll = positive && isPrime(currentPlayer.position);

        String warnaText = positive ? "HIJAU (maju)" : "MERAH (mundur)";
        lblDiceText.setText("Dadu: " + diceNumber + " | " + warnaText +
                (useShortestThisRoll ? " | PRIME BOOST: Shortest Path" : ""));
        lblDiceText.setForeground(positive ? new Color(210, 250, 200) : new Color(255, 190, 170));

        // Suara roll dice
        SoundManager.playDice();

        dicePanel.setDice(diceNumber, positive);

        startAnimatedMove(diceNumber, positive);
    }

    // ================== ANIMASI GERAK ==================

    private void startAnimatedMove(int diceNumber, boolean positive) {
        btnRoll.setEnabled(false);
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

        if (gotStar && finalPos < BOARD_SIZE) {
            status += " ★ BONUS! Posisi bintang (kelipatan 5), dapat giliran lagi.";
        }

        lblStatus.setText("Status: " + status);
        historyText.append(status);
        appendHistory(historyText.toString());

        if (finalPos >= BOARD_SIZE) {
            gameOver = true;
            lblStatus.setText("Status: " + currentPlayer.name + " MENANG!");
            appendHistory("🎉 " + currentPlayer.name + " MENANG! Mencapai kotak " + finalPos + ".");

            // SOUND: stop BGM + play winner
            SoundManager.stopBGM();
            SoundManager.playWinner();

            showEndGameDialog(currentPlayer.name);
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
        boardPanel.repaint();
    }

    private int stepForward(BoardGraph board, Player player, boolean useShortest) {
        int pos = player.position;
        if (pos >= board.size) return pos;
        player.moveHistory.push(pos);

        int newPos = useShortest ? board.getNextOnShortestPath(pos) : board.getNextForward(pos);
        if (newPos > board.size) newPos = board.size;
        if (newPos <= 0) newPos = Math.min(board.size, pos + 1);

        player.position = newPos;
        return newPos;
    }

    private int stepBackward(Player player) {
        if (player.moveHistory.isEmpty()) return player.position;
        int newPos = player.moveHistory.pop();
        player.position = newPos;
        return newPos;
    }

    private void updateTurnLabel() {
        if (currentPlayer != null && !gameOver) {
            lblTurn.setText("Giliran: " + currentPlayer.name + "  (Posisi: " + currentPlayer.position + ")");
        } else if (gameOver) {
            lblTurn.setText("Game selesai.");
        } else {
            lblTurn.setText("Menunggu giliran...");
        }
    }

    // ================== PLAYER CLASS ==================

    private static class Player {
        String name;
        int position;
        Stack<Integer> moveHistory;
        Color tokenColor;

        Player(String name, Color color) {
            this.name = name;
            this.position = 1;
            this.moveHistory = new Stack<>();
            this.tokenColor = color;
        }
    }

    // ================== DICE CLASS (LOGIC) ==================

    private static class Dice {
        private Random random = new Random();

        int rollNumber() {
            return random.nextInt(6) + 1;
        }

        boolean isPositive() {
            return random.nextDouble() < 0.7;
        }
    }

    // ================== BOARD GRAPH ==================

    private static class BoardGraph {
        int size;
        Map<Integer, List<Integer>> adjacency;
        private Random rand = new Random();
        private List<int[]> extraLinks = new ArrayList<>();

        BoardGraph(int size) {
            this.size = size;
            this.adjacency = new HashMap<>();
            buildGraph();
            addRandomLinksUndirected(5); // 5 random links
        }

        // graph linear 1 -> 2 -> 3 -> ... -> size
        private void buildGraph() {
            for (int i = 1; i <= size; i++) {
                List<Integer> neighbors = new ArrayList<>();
                if (i < size) neighbors.add(i + 1);
                adjacency.put(i, neighbors);
            }
        }

        /**
         * Tambah k random link UNDIRECTED dengan syarat:
         * - a != b
         * - |a - b| != 1 (bukan tetangga linear biasa)
         * - tiap pasangan unik
         * - SETIAP NODE hanya boleh muncul sekali di SEMUA extra links
         */
        private void addRandomLinksUndirected(int k) {
            Set<String> usedPairs = new HashSet<>();
            Set<Integer> usedNodes = new HashSet<>();

            int attempts = 0;
            int maxAttempts = k * 1000; // biar ga infinite loop

            while (extraLinks.size() < k && attempts < maxAttempts) {
                attempts++;

                int a = rand.nextInt(size) + 1;
                int b = rand.nextInt(size) + 1;
                if (a == b) continue;

                // node ini sudah dipakai di link lain? skip
                if (usedNodes.contains(a) || usedNodes.contains(b)) continue;

                // jangan edge tetangga linear biasa
                if (Math.abs(a - b) == 1) continue;

                int u = Math.min(a, b);
                int v = Math.max(a, b);
                String key = u + "-" + v;
                if (usedPairs.contains(key)) continue;

                adjacency.computeIfAbsent(a, z -> new ArrayList<>());
                adjacency.computeIfAbsent(b, z -> new ArrayList<>());
                if (!adjacency.get(a).contains(b)) adjacency.get(a).add(b);
                if (!adjacency.get(b).contains(a)) adjacency.get(b).add(a);

                extraLinks.add(new int[]{a, b});
                usedPairs.add(key);
                usedNodes.add(a);
                usedNodes.add(b);
            }
        }

        int getNextForward(int pos) {
            List<Integer> neighbors = adjacency.get(pos);
            if (neighbors == null || neighbors.isEmpty()) return pos;

            int linear = pos + 1;
            if (neighbors.contains(linear)) return linear;
            return neighbors.get(0);
        }

        int getNextOnShortestPath(int pos) {
            if (pos >= size) return pos;

            int target = size;
            int[] prev = new int[size + 1];
            Arrays.fill(prev, -1);

            Queue<Integer> q = new ArrayDeque<>();
            boolean[] vis = new boolean[size + 1];

            q.offer(pos);
            vis[pos] = true;

            while (!q.isEmpty()) {
                int u = q.poll();
                if (u == target) break;

                List<Integer> nbrs = adjacency.getOrDefault(u, Collections.emptyList());
                nbrs = new ArrayList<>(nbrs);
                if (nbrs.contains(u + 1)) {
                    nbrs.remove((Integer) (u + 1));
                    nbrs.add(0, u + 1);
                }

                for (int v : nbrs) {
                    if (v < 1 || v > size) continue;
                    if (!vis[v]) {
                        vis[v] = true;
                        prev[v] = u;
                        q.offer(v);
                    }
                }
            }

            if (prev[target] == -1) {
                return Math.min(size, pos + 1);
            }

            int cur = target;
            Deque<Integer> path = new ArrayDeque<>();
            while (cur != -1) {
                path.push(cur);
                cur = prev[cur];
            }
            if (!path.isEmpty() && path.peek() == pos) path.pop();
            if (path.isEmpty()) return Math.min(size, pos + 1);
            return path.peek();
        }

        List<int[]> getExtraLinks() {
            return extraLinks;
        }
    }


    // ================== PANEL BOARD ==================

    private class BoardPanel extends JPanel {

        BoardPanel() {
            setBackground(new Color(70, 40, 20));
            setPreferredSize(BOARD_DIM);
            setMinimumSize(BOARD_DIM);
            setMaximumSize(BOARD_DIM);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            int margin = 30;
            int boardWidth = w - 2 * margin;
            int boardHeight = h - 2 * margin;

            int cellW = boardWidth / COLS;
            int cellH = boardHeight / ROWS;

            g2.setColor(new Color(90, 55, 30));
            g2.fillRoundRect(margin - 10, margin - 10, boardWidth + 20, boardHeight + 20, 20, 20);

            Font numFont = new Font("Monospaced", Font.BOLD, 11);
            g2.setFont(numFont);

            for (int pos = 1; pos <= BOARD_SIZE; pos++) {
                CellCoord coord = getCellCoord(pos, margin, cellW, cellH);

                int x = coord.x;
                int y = coord.y;
                int row = coord.rowFromTop;
                int col = coord.col;

                if ((row + col) % 2 == 0)
                    g2.setColor(new Color(210, 180, 130));
                else
                    g2.setColor(new Color(180, 140, 90));
                g2.fillRect(x, y, cellW - 2, cellH - 2);

                g2.setColor(new Color(110, 70, 40));
                g2.drawRect(x, y, cellW - 2, cellH - 2);

                g2.setColor(new Color(60, 35, 20));
                String text = String.valueOf(pos);
                FontMetrics fm = g2.getFontMetrics();
                int tx = x + 2;
                int ty = y + fm.getAscent() + 1;
                g2.drawString(text, tx, ty);

                if (isStarPosition(pos)) {
                    int starCx = x + cellW - cellW / 4;
                    int starCy = y + cellH / 4;
                    drawStar(g2, starCx, starCy, Math.min(cellW, cellH) / 6);
                }

                if (isPrime(pos)) {
                    g2.setColor(new Color(190, 255, 190, 200));
                    g2.fillOval(x + cellW - 10, y + cellH - 10, 6, 6);
                }
            }

            Stroke old = g2.getStroke();
            g2.setColor(new Color(120, 200, 200));
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    1f, new float[]{10f, 7f}, 0f));
            for (int[] e : board.getExtraLinks()) {
                int a = e[0], b = e[1];
                Point pa = getCellCenter(a, margin, cellW, cellH);
                Point pb = getCellCenter(b, margin, cellW, cellH);
                g2.drawLine(pa.x, pa.y, pb.x, pb.y);
            }
            g2.setStroke(old);

            int radius = Math.min(cellW, cellH) / 3;
            int offsetStep = Math.max(3, radius / 2);

            for (Player p : players) {
                int pos = Math.max(1, Math.min(BOARD_SIZE, p.position));
                Point center = getCellCenter(pos, margin, cellW, cellH);

                int idx = players.indexOf(p);
                int dx = (idx % 2) * offsetStep - offsetStep / 2;
                int dy = (idx / 2) * offsetStep - offsetStep / 2;

                int cx = center.x + dx;
                int cy = center.y + dy;

                g2.setColor(p.tokenColor);
                g2.fillOval(cx - radius / 2, cy - radius / 2, radius, radius);

                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(cx - radius / 2, cy - radius / 2, radius, radius);
            }

            g2.dispose();
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

        private CellCoord getCellCoord(int pos, int margin, int cellW, int cellH) {
            int index = pos - 1;
            int logicalRowFromBottom = index / COLS;
            int normalCol = index % COLS;

            int col;
            if (logicalRowFromBottom % 2 == 0) col = normalCol;
            else col = COLS - 1 - normalCol;

            int rowFromTop = ROWS - 1 - logicalRowFromBottom;

            int x = margin + col * cellW;
            int y = margin + rowFromTop * cellH;

            return new CellCoord(x, y, rowFromTop, col);
        }

        private Point getCellCenter(int pos, int margin, int cellW, int cellH) {
            CellCoord c = getCellCoord(pos, margin, cellW, cellH);
            int cx = c.x + (cellW - 2) / 2;
            int cy = c.y + (cellH - 2) / 2;
            return new Point(cx, cy);
        }

        private class CellCoord {
            int x, y, rowFromTop, col;
            CellCoord(int x, int y, int rowFromTop, int col) {
                this.x = x;
                this.y = y;
                this.rowFromTop = rowFromTop;
                this.col = col;
            }
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
            setPreferredSize(new Dimension(140, 140));
            setBackground(new Color(110, 70, 40));
        }

        void setDice(int value, boolean positive) {
            this.value = value;
            this.positive = positive;
            startShake();
        }

        private void startShake() {
            if (shakeTimer != null && shakeTimer.isRunning())
                shakeTimer.stop();

            if (bounceTimer != null && bounceTimer.isRunning())
                bounceTimer.stop();

            shakeOffset = 0;

            shakeTimer = new javax.swing.Timer(30, e -> {
                shakeOffset = (Math.random() - 0.5) * 14;
                repaint();
            });
            shakeTimer.start();

            javax.swing.Timer stopShake = new javax.swing.Timer(350, e -> {
                shakeTimer.stop();
                shakeOffset = 0;
                startBounce();
            });
            stopShake.setRepeats(false);
            stopShake.start();
        }

        private void startBounce() {
            if (bounceTimer != null && bounceTimer.isRunning())
                bounceTimer.stop();

            scale = 1.6;

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

            int size = (int) ((Math.min(w, h) - 20) * scale);
            int x = (w - size) / 2 + (int) shakeOffset;
            int y = (h - size) / 2;

            Color base = positive ? new Color(45, 160, 45) : new Color(200, 60, 60);

            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillRoundRect(x + 8, y + 10, size, size, 28, 28);

            g2.setColor(base);
            g2.fillRoundRect(x, y, size, size, 28, 28);

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

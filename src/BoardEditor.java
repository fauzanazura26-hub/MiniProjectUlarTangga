//import javax.swing.*;
//import java.awt.*;
//import java.awt.event.*;
//import java.io.*;
//import java.util.*;
//import java.util.List;
//import javax.imageio.ImageIO;
//
///**
// * BoardEditor - Mode Developer untuk mengedit, memuat, dan menyimpan
// * posisi node pada papan permainan Snake & Ladder.
// *
// * Diperbarui untuk menggunakan dimensi BOARD_DIM (640x680) agar konsisten dengan Game Mode.
// */
//public class BoardEditor extends JFrame {
//
//    private static final int BOARD_SIZE = 64;
//    // --- BOARD_DIM DISAMAKAN DENGAN SNAKELADDER.JAVA (640x680) ---
//    private static final Dimension BOARD_DIM = new Dimension(640, 680);
//    // -------------------------------------------------------------
//    private static final String POSITION_FILE = "node_positions.txt";
//
//    private BoardGraph boardGraph;
//    private BoardEditorPanel editorPanel;
//    private Image backgroundImage;
//
//    // ================== KELAS UTILITY UNTUK DIBAGI (PUBLIC STATIC) ==================
//
//    public static class Player {
//        public String name;
//        public int position;
//        public Stack<Integer> moveHistory;
//        public Color tokenColor;
//
//        public Player(String name, Color color) {
//            this.name = name;
//            this.position = 1;
//            this.moveHistory = new Stack<>();
//            this.tokenColor = color;
//        }
//    }
//
//    public static class Dice {
//        private Random random = new Random();
//        public int rollNumber()  { return random.nextInt(6) + 1; }
//        public boolean isPositive() { return random.nextDouble() < 0.7; }
//    }
//
//    public static class BoardGraph {
//        public int size;
//        public Map<Integer, List<Integer>> adjacency;
//        private Random rand = new Random();
//        private List<int[]> extraLinks = new ArrayList<>();
//
//        public BoardGraph(int size) {
//            this.size = size;
//            this.adjacency = new HashMap<>();
//            buildGraph();
//            addRandomLinksUndirected(5);
//        }
//
//        private void buildGraph() {
//            for (int i = 1; i <= size; i++) {
//                List<Integer> neighbors = new ArrayList<>();
//                if (i < size) neighbors.add(i + 1);
//                adjacency.put(i, neighbors);
//            }
//        }
//
//        private void addRandomLinksUndirected(int k) {
//            Set<String> usedPairs = new HashSet<>();
//            Set<Integer> usedNodes = new HashSet<>();
//            int attempts = 0;
//            int maxAttempts = k * 1000;
//
//            while (extraLinks.size() < k && attempts < maxAttempts) {
//                attempts++;
//                int a = rand.nextInt(size) + 1;
//                int b = rand.nextInt(size) + 1;
//                if (a == b) continue;
//                if (usedNodes.contains(a) || usedNodes.contains(b)) continue;
//                if (Math.abs(a - b) == 1) continue;
//
//                int u = Math.min(a, b);
//                int v = Math.max(a, b);
//                String key = u + "-" + v;
//                if (usedPairs.contains(key)) continue;
//
//                adjacency.computeIfAbsent(a, z -> new ArrayList<>());
//                adjacency.computeIfAbsent(b, z -> new ArrayList<>());
//                if (!adjacency.get(a).contains(b)) adjacency.get(a).add(b);
//                if (!adjacency.get(b).contains(a)) adjacency.get(b).add(a);
//
//                extraLinks.add(new int[]{a, b});
//                usedPairs.add(key);
//                usedNodes.add(a);
//                usedNodes.add(b);
//            }
//        }
//
//        public int getNextForward(int pos) {
//            List<Integer> neighbors = adjacency.get(pos);
//            if (neighbors == null || neighbors.isEmpty()) return pos;
//            int linear = pos + 1;
//            if (neighbors.contains(linear)) return linear;
//            return neighbors.get(0);
//        }
//
//        public int getNextOnShortestPath(int pos) {
//            if (pos >= size) return pos;
//            int target = size;
//            int[] prev = new int[size + 1];
//            Arrays.fill(prev, -1);
//
//            Queue<Integer> q = new ArrayDeque<>();
//            boolean[] vis = new boolean[size + 1];
//
//            q.offer(pos);
//            vis[pos] = true;
//
//            while (!q.isEmpty()) {
//                int u = q.poll();
//                if (u == target) break;
//
//                List<Integer> nbrs = adjacency.getOrDefault(u, Collections.emptyList());
//                nbrs = new ArrayList<>(nbrs);
//                if (u < size && adjacency.get(u).contains(u + 1)) {
//                    nbrs.remove((Integer) (u + 1));
//                    nbrs.add(0, u + 1);
//                }
//
//                for (int v : nbrs) {
//                    if (v < 1 || v > size) continue;
//                    if (!vis[v]) {
//                        vis[v] = true;
//                        prev[v] = u;
//                        q.offer(v);
//                    }
//                }
//            }
//
//            if (prev[target] == -1) {
//                return Math.min(size, pos + 1);
//            }
//
//            int cur = target;
//            Deque<Integer> path = new ArrayDeque<>();
//            while (cur != -1 && cur != pos) {
//                path.push(cur);
//                cur = prev[cur];
//            }
//            if (path.isEmpty()) return Math.min(size, pos + 1);
//            return path.peek();
//        }
//
//        public List<int[]> getExtraLinks() {
//            return extraLinks;
//        }
//    }
//    // =================================================================
//
//    public static void main(String[] args) {
//        SwingUtilities.invokeLater(() -> new BoardEditor().setVisible(true));
//    }
//
//    public BoardEditor() {
//        setTitle("Snake & Ladder - Board Editor (DEV MODE)");
//        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//
//        // Mengunci ukuran jendela agar board tidak terdistorsi
//        setResizable(false);
//
//        boardGraph = new BoardGraph(BOARD_SIZE);
//        editorPanel = new BoardEditorPanel();
//
//        // PATH BACKGROUND PETA DIPERBAIKI: Langsung menunjuk ke 'Background Board/bgboard.png'
//        loadBackgroundImage("Background Board/bgboard.png");
//
//        initUI();
//
//        pack();
//        // Mengatur ukuran frame agar sesuai dengan Board Dimensi (640x680) + Controls
//        setMinimumSize(new Dimension(BOARD_DIM.width + 200, BOARD_DIM.height + 80));
//        setMaximumSize(new Dimension(BOARD_DIM.width + 200, BOARD_DIM.height + 80));
//        setLocationRelativeTo(null);
//
//        if (!loadNodePositions()) {
//            System.out.println("Gagal memuat posisi dari file. Menggunakan posisi default.");
//            editorPanel.generateDefaultPoints(editorPanel.getWidth(), editorPanel.getHeight());
//        }
//        editorPanel.repaint();
//    }
//
//    private void loadBackgroundImage(String path) {
//        try {
//            backgroundImage = ImageIO.read(new File(path));
//        } catch (IOException e) {
//            System.err.println("Gagal memuat background gambar editor dari jalur: " + path);
//            System.err.println("Pastikan file 'bgboard.png' ada di folder 'Background Board'.");
//            backgroundImage = null;
//        }
//    }
//
//    // ================== UI ==================
//
//    private void initUI() {
//        JPanel controlPanel = new JPanel();
//        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
//        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
//
//        JButton btnSave = new JButton("Simpan Posisi");
//        btnSave.addActionListener(e -> saveNodePositions());
//
//        JButton btnLoad = new JButton("Muat Posisi");
//        btnLoad.addActionListener(e -> {
//            if (loadNodePositions()) {
//                JOptionPane.showMessageDialog(this, "Posisi berhasil dimuat dari " + POSITION_FILE, "Sukses", JOptionPane.INFORMATION_MESSAGE);
//                editorPanel.repaint();
//            } else {
//                JOptionPane.showMessageDialog(this, "Gagal memuat posisi dari " + POSITION_FILE, "Error", JOptionPane.ERROR_MESSAGE);
//            }
//        });
//
//        controlPanel.add(btnSave);
//        controlPanel.add(Box.createVerticalStrut(10));
//        controlPanel.add(btnLoad);
//        controlPanel.add(Box.createVerticalStrut(10));
//        controlPanel.add(new JLabel("Drag & Drop node untuk mengedit jalur."));
//        controlPanel.add(new JLabel("Simpan setelah selesai."));
//
//
//        JPanel root = new JPanel(new BorderLayout());
//
//        // Wrapper untuk Board Panel agar ukurannya tetap
//        JPanel boardWrapper = new JPanel(new GridBagLayout());
//        boardWrapper.setPreferredSize(BOARD_DIM);
//        boardWrapper.add(editorPanel);
//
//        root.add(boardWrapper, BorderLayout.CENTER);
//        root.add(controlPanel, BorderLayout.EAST);
//
//        setContentPane(root);
//    }
//
//    // ================== FILE I/O ==================
//
//    private void saveNodePositions() {
//        if (editorPanel.getCenters() == null || editorPanel.getCenters().length <= 1) return;
//
//        try (PrintWriter writer = new PrintWriter(new FileWriter(POSITION_FILE))) {
//            for (int i = 1; i <= BOARD_SIZE; i++) {
//                Point p = editorPanel.getCenters()[i];
//                writer.println(p.x + "," + p.y);
//            }
//            JOptionPane.showMessageDialog(this, "Posisi node berhasil disimpan ke " + POSITION_FILE, "Sukses", JOptionPane.INFORMATION_MESSAGE);
//        } catch (IOException e) {
//            JOptionPane.showMessageDialog(this, "Gagal menyimpan posisi: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
//        }
//    }
//
//    private boolean loadNodePositions() {
//        File file = new File(POSITION_FILE);
//        if (!file.exists()) return false;
//
//        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
//            Point[] loadedCenters = new Point[BOARD_SIZE + 1];
//            loadedCenters[0] = new Point(0, 0);
//            String line;
//            int i = 1;
//            while ((line = reader.readLine()) != null && i <= BOARD_SIZE) {
//                String[] parts = line.split(",");
//                if (parts.length == 2) {
//                    loadedCenters[i] = new Point(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
//                    i++;
//                }
//            }
//            if (i > BOARD_SIZE) {
//                editorPanel.setCenters(loadedCenters);
//                return true;
//            }
//        } catch (Exception e) {
//            return false;
//        }
//        return false;
//    }
//
//    // ================== BOARD EDITOR PANEL ==================
//
//    private class BoardEditorPanel extends JPanel {
//
//        private Point[] centers;
//        private final int nodeR = 10; // Ukuran Node sama dengan SnakeLadder.java
//
//        private int draggedIndex = -1;
//        private Point dragOffset;
//
//        public BoardEditorPanel() {
//            setBackground(new Color(20, 70, 110));
//            // --- Menggunakan dimensi statis 640x680 ---
//            setPreferredSize(BOARD_DIM);
//            setMinimumSize(BOARD_DIM);
//            setMaximumSize(BOARD_DIM);
//            // --------------------------------------------
//
//            addMouseListener(new MouseAdapter() {
//                @Override
//                public void mousePressed(MouseEvent e) {
//                    draggedIndex = findNode(e.getPoint());
//                    if (draggedIndex != -1) {
//                        dragOffset = new Point(e.getX() - centers[draggedIndex].x, e.getY() - centers[draggedIndex].y);
//                    }
//                }
//
//                @Override
//                public void mouseReleased(MouseEvent e) {
//                    draggedIndex = -1;
//                }
//            });
//
//            addMouseMotionListener(new MouseMotionAdapter() {
//                @Override
//                public void mouseDragged(MouseEvent e) {
//                    if (draggedIndex != -1) {
//                        int newX = Math.min(Math.max(nodeR, e.getX() - dragOffset.x), getWidth() - nodeR);
//                        int newY = Math.min(Math.max(nodeR, e.getY() - dragOffset.y), getHeight() - nodeR);
//
//                        centers[draggedIndex].setLocation(newX, newY);
//                        repaint();
//                    }
//                }
//            });
//        }
//
//        public Point[] getCenters() { return centers; }
//        public void setCenters(Point[] newCenters) { this.centers = newCenters; }
//
//        private int findNode(Point p) {
//            if (centers == null) return -1;
//            for (int i = 1; i <= BOARD_SIZE; i++) {
//                Point c = centers[i];
//                if (c.distance(p) <= nodeR + 5) {
//                    return i;
//                }
//            }
//            return -1;
//        }
//
//        public void generateDefaultPoints(int w, int h) {
//            int margin = 30;
//            int boardW = w - 2 * margin;
//            int boardH = h - 2 * margin;
//
//            double[][] anchors = {
//                    {0.08, 0.85}, {0.30, 0.80}, {0.48, 0.75},
//                    {0.70, 0.85}, {0.90, 0.65}, {0.80, 0.45},
//                    {0.60, 0.35}, {0.35, 0.30}, {0.10, 0.40}, {0.20, 0.15},
//                    {0.45, 0.10}, {0.70, 0.18}, {0.88, 0.35},
//            };
//
//            int nA = anchors.length;
//            double[] segLen = new double[nA - 1];
//            double total = 0;
//
//            java.awt.geom.Point2D.Double[] pts = new java.awt.geom.Point2D.Double[nA];
//            for (int i = 0; i < nA; i++) {
//                double x = margin + anchors[i][0] * boardW;
//                double y = margin + anchors[i][1] * boardH;
//                pts[i] = new java.awt.geom.Point2D.Double(x, y);
//            }
//
//            for (int i = 0; i < nA - 1; i++) {
//                double dx = pts[i + 1].x - pts[i].x;
//                double dy = pts[i + 1].y - pts[i].y;
//                segLen[i] = Math.hypot(dx, dy);
//                total += segLen[i];
//            }
//
//            centers = new Point[BOARD_SIZE + 1];
//            centers[0] = new Point(0, 0);
//
//            for (int idx = 0; idx < BOARD_SIZE; idx++) {
//                double dist = (total * idx) / (BOARD_SIZE - 1);
//                double acc = 0;
//                int seg = 0;
//                while (seg < segLen.length && acc + segLen[seg] < dist) {
//                    acc += segLen[seg];
//                    seg++;
//                }
//                double t;
//                if (seg >= segLen.length) {
//                    seg = segLen.length - 1;
//                    t = 1.0;
//                } else {
//                    t = (dist - acc) / segLen[seg];
//                }
//                double x = pts[seg].x + (pts[seg + 1].x - pts[seg].x) * t;
//                double y = pts[seg].y + (pts[seg + 1].y - pts[seg].y) * t;
//                centers[idx + 1] = new Point((int) x, (int) y);
//            }
//        }
//
//        @Override
//        protected void paintComponent(Graphics g) {
//            super.paintComponent(g);
//            Graphics2D g2 = (Graphics2D) g.create();
//            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
//
//            if (centers == null) return;
//
//            int w = getWidth();
//            int h = getHeight();
//
//            // Menggambar Background (mengakses variabel global)
//            if (BoardEditor.this.backgroundImage != null) {
//                g2.drawImage(BoardEditor.this.backgroundImage, 0, 0, w, h, this);
//            } else {
//                g2.setColor(new Color(20, 70, 110));
//                g2.fillRect(0, 0, w, h);
//            }
//
//            // Jalur normal: garis putus-putus
//            Stroke oldStroke = g2.getStroke();
//            g2.setColor(new Color(245, 245, 245, 220));
//            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
//                    1f, new float[]{8f, 10f}, 0f));
//            for (int i = 1; i < BOARD_SIZE; i++) {
//                Point a = centers[i];
//                Point b = centers[i + 1];
//                g2.drawLine(a.x, a.y, b.x, b.y);
//            }
//
//            // Jalur shortcut (Yellow Arrow)
//            g2.setColor(new Color(255, 220, 80));
//            g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
//            for (int[] e : boardGraph.getExtraLinks()) {
//                Point pa = centers[e[0]];
//                Point pb = centers[e[1]];
//                g2.drawLine(pa.x, pa.y, pb.x, pb.y);
//            }
//            g2.setStroke(oldStroke);
//
//
//            // Gambar node (seragam dengan Game Mode)
//            Font numFont = new Font("Monospaced", Font.BOLD, 11);
//            for (int i = 1; i <= BOARD_SIZE; i++) {
//                Point c = centers[i];
//                int nx = c.x;
//                int ny = c.y;
//
//                // node kayu
//                Color nodeColor = (i == draggedIndex) ? Color.RED : new Color(140, 100, 60);
//                g2.setColor(nodeColor);
//                g2.fillOval(nx - nodeR, ny - nodeR, 2 * nodeR, 2 * nodeR);
//
//                g2.setColor(new Color(235, 215, 175));
//                g2.fillOval(nx - (int) (nodeR * 0.7), ny - (int) (nodeR * 0.7),
//                        (int) (2 * nodeR * 0.7), (int) (2 * nodeR * 0.7));
//
//                g2.setColor(new Color(90, 60, 35));
//                g2.drawOval(nx - nodeR, ny - nodeR, 2 * nodeR, 2 * nodeR);
//
//                // Nomor node
//                g2.setFont(numFont);
//                g2.setColor(new Color(60, 35, 20));
//                String label = String.valueOf(i);
//                FontMetrics fm = g2.getFontMetrics();
//                int tw = fm.stringWidth(label);
//                int th = fm.getAscent();
//                g2.drawString(label, nx - tw / 2, ny + th / 3);
//            }
//
//            g2.dispose();
//        }
//    }
//}
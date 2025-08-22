 package com.behavior.goodmanners;

import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * GoodManners JavaFX educational game.
 * Shows scenario prompts, plays audio, and gives feedback via a pulsing green box.
 * Refactored for robustness: enum state, centralized sound management, defensive loading.
 *
 * <p><strong>Overview:</strong>
 * Displays a sequence of social scenarios read from a JSON file. For each scenario:
 * a prompt image & audio are shown; the learner chooses "Good" or "Bad"; the app
 * plays corresponding feedback audio and shows a pulsing feedback panel with text/animation.
 * Ends with a simple end screen offering replay or exit.</p>
 *
 * <p><strong>Architecture:</strong>
 * - Fixed-size canvas-based renderer with a small state machine {@link State}.
 * - Scenario data model with validation and asset fallbacks.
 * - Centralized {@link SoundManager} to prevent overlapping {@link MediaPlayer}s.</p>
 *
 * <p><strong>Assets:</strong> Loaded from disk paths under {@code assets/} (JSON, images, audio).
 * In a production build, consider packaging assets as classpath resources.</p>
 *
 * <p><strong>Threading:</strong> Rendering occurs on the JavaFX Application Thread via
 * {@link javafx.animation.AnimationTimer}. Media callbacks also occur on FX thread.</p>
 *
 * <p><strong>Limitations:</strong> No localization framework; font choices are fixed; assumes
 * presence of scenario assets on disk; uses simple time-based progression for feedback.</p>
 *
 * <p><strong>UPDATE (Warnings Fix):</strong>
 * - The unchecked/unused lambda parameter in Timeline keyframes has been renamed to {@code ignored}.
 * - {@code Scenario.getCurrentAnim()} and {@code AnimationWrapper.draw(...)} are now invoked in {@code renderFeedback()} so they are used locally and the animation is visible.</p>
 *
 * <p><strong>Author:</strong> Tennie White</p>
 */
public class GoodManners extends Application {

    // ─── Constants ────────────────────────────────────────────────────────────────
    private static final int SCREEN_W = 1000;
    private static final int SCREEN_H = 750;
    private static final int FEEDBACK_DELAY_MS = 5000;
    private static final Color BG_COLOR = Color.rgb(153, 217, 234);

    // ─── State machine ───────────────────────────────────────────────────────────
    private enum State { PROMPT, FEEDBACK, END }

    // ─── UI COMPONENTS ───────────────────────────────────────────────────────────
    private Canvas canvas;
    private GraphicsContext gc;
    private Button btnGood, btnBad, btnPlay, btnExit;
    private Pane root;

    // ─── GAME STATE ───────────────────────────────────────────────────────────────
    private final List<Scenario> scenarios = new ArrayList<>();
    private int index = 0;
    private State state = State.PROMPT;
    private long feedbackStart = 0;
    private boolean promptAudioPlayed = false;
    private long promptStartTime = 0;

    // Centralized sound manager to avoid overlapping MediaPlayers
    private final SoundManager soundManager = new SoundManager();

    // Feedback box pulse state
    private double boxScale = 1.0;
    private javafx.animation.Timeline boxPulseTimeline = null;

    // Optional end screen background
    private Image cloudEndScreen = null;

    /**
     * Standard JavaFX launcher.
     * @param args CLI args (unused)
     */
    public static void main(String[] args) {
        launch(args);
    }

    // ─── Entry point for JavaFX ───────────────────────────────────────────────────
    /**
     * Initializes assets, UI controls, main canvas, and starts the render loop.
     * Loads scenarios from {@code assets/scenarios.json}; exits if none are valid.
     */
    @Override
    public void start(Stage stage) {
        // Load scenario definitions (JSON)
        try {
            loadScenarios("assets/scenarios.json");
        } catch (Exception e) {
            System.err.println("Failed to load scenarios.json: " + e.getMessage());
            e.printStackTrace();
            Platform.exit();
            return;
        }

        // Setup canvas and GraphicsContext
        canvas = new Canvas(SCREEN_W, SCREEN_H);
        gc = canvas.getGraphicsContext2D();

        // Create interactive buttons
        btnGood = createImageButton("assets/check.png", _ -> onGood());
        btnBad  = createImageButton("assets/x.png", _ -> onBad());
        btnPlay = createImageButton("assets/playagain.png", _ -> onPlayAgain());
        btnExit  = createImageButton("assets/exit.png", _ -> Platform.exit());

        // Initialize visibility explicitly
        btnGood.setVisible(false);
        btnBad.setVisible(false);
        btnPlay.setVisible(false);
        btnExit.setVisible(false);

        root = new Pane(canvas, btnGood, btnBad, btnPlay, btnExit);
        layoutButtons();

        // Load optional end screen background
        File cloudBg = new File("assets/background_manners.png");
        if (cloudBg.exists()) {
            cloudEndScreen = new Image(cloudBg.toURI().toString(), SCREEN_W, SCREEN_H, false, true);
        }

        // Set up scene and stage
        Scene scene = new Scene(root, SCREEN_W, SCREEN_H, BG_COLOR);
        stage.setScene(scene);
        stage.setTitle("Good Manners Game");
        stage.show();

        // Main loop: animation timer driving render
        new javafx.animation.AnimationTimer() {
            public void handle(long now) {
                render();
            }
        }.start();
    }

    // ─── Load scenarios from JSON file with basic validation ──────────────────────
    /**
     * Loads and validates scenario objects from a JSON array file.
     * Invalid entries are skipped with a warning; throws if none are valid.
     * @param path filesystem path to scenarios.json
     * @throws Exception when file is missing or all entries are invalid
     */
    private void loadScenarios(String path) throws Exception {
        File f = new File(path);
        if (!f.exists()) {
            throw new Exception("scenarios.json not found at " + path);
        }
        String text = Files.readString(Path.of(path));
        JSONArray arr = new JSONArray(text);
        for (int i = 0; i < arr.length(); i++) { // loop over each scenario
            JSONObject obj = arr.getJSONObject(i);
            try {
                scenarios.add(new Scenario(obj));
            } catch (IllegalArgumentException e) {
                System.err.println("Skipping invalid scenario at index " + i + ": " + e.getMessage());
            }
        }
        if (scenarios.isEmpty()) {
            throw new Exception("No valid scenarios loaded.");
        }
    }

    // ─── Creates button with image and bounce animation ──────────────────────────
    /**
     * Builds an image-backed button with a gentle bounce animation and click handler.
     * Falls back to an empty icon if the image is missing.
     * @param path image file path on disk (e.g., assets/check.png)
     * @param handler mouse click handler
     * @return configured Button instance
     */
    private Button createImageButton(String path, javafx.event.EventHandler<MouseEvent> handler) {
        ImageView icon;
        File file = new File(path);
        if (file.exists()) {
            icon = new ImageView(new Image(file.toURI().toString()));
        } else {
            System.err.println("Missing button image: " + path);
            icon = new ImageView(); // placeholder empty
        }
        icon.setFitWidth(160);
        icon.setFitHeight(160);
        animateBounce(icon);
        Button btn = new Button("", icon);
        btn.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, null, null)));
        btn.setOnMouseClicked(handler);
        return btn;
    }

    // ─── Position UI buttons on screen ───────────────────────────────────────────
    /** Computes button positions relative to screen size; call after creating buttons. */
    private void layoutButtons() {
        double centerX = SCREEN_W / 2.0;
        double baseY = SCREEN_H * 0.78;
        btnGood.setLayoutX(centerX - SCREEN_W * 0.25);
        btnBad.setLayoutX(centerX + SCREEN_W * 0.05);
        btnGood.setLayoutY(baseY);
        btnBad.setLayoutY(baseY);
        btnPlay.setLayoutX(centerX - SCREEN_W * 0.25);
        btnExit.setLayoutX(centerX + SCREEN_W * 0.05);
        btnPlay.setLayoutY(SCREEN_H * 0.55);
        btnExit.setLayoutY(SCREEN_H * 0.55);
    }

    // ─── Main rendering dispatch based on current state ─────────────────────────
    /** Clears background and delegates to state-specific renderers. */
    private void render() {
        // clear background
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, SCREEN_W, SCREEN_H);

        if (index >= scenarios.size()) {
            state = State.END;
        }

        switch (state) { // state machine switch
            case PROMPT -> renderPrompt();
            case FEEDBACK -> renderFeedback();
            case END -> renderEnd();
        }
    }

    // ─── Render the prompt state (scenario question) ────────────────────────────
    /** Draws prompt visuals and plays prompt audio (once), then enables choice buttons. */
    private void renderPrompt() {
        if (index >= scenarios.size()) return; // defensive
        Scenario scen = scenarios.get(index);

        // Draw background for prompt
        gc.drawImage(scen.bgPromptImage, scen.bgX, scen.bgY, scen.bgW, scen.bgH);

        // Draw the prompt text
        drawWrappedText(gc, scen.prompt, Font.font("Comic Sans MS", FontWeight.BOLD, 36),
                SCREEN_W / 2 + 60, 70, 420, 110, Pos.CENTER);

        // Play prompt audio once
        if (!promptAudioPlayed) {
            soundManager.stop();
            scen.playPromptSound(soundManager);
            promptStartTime = System.currentTimeMillis();
            promptAudioPlayed = true;
        }

        // Enable good/bad buttons after short delay to avoid accidental taps
        boolean allowClick = (System.currentTimeMillis() - promptStartTime > 1500);
        btnGood.setVisible(allowClick);
        btnBad.setVisible(allowClick);
        btnPlay.setVisible(false);
        btnExit.setVisible(false);
    }

    // ─── Render feedback: pulsing green box with text inside (no gray overlay) ───
    /** Shows feedback panel and advances to next scenario after a fixed delay. */
    private void renderFeedback() {
        if (index >= scenarios.size()) return;
        Scenario scen = scenarios.get(index);

        // Draw scenario's feedback background
        gc.drawImage(scen.bgFeedbackImage, scen.bgX, scen.bgY, scen.bgW, scen.bgH);

        // ── UPDATE: Draw optional feedback animation (if available) ─────────────
        AnimationWrapper anim = scen.getCurrentAnim(); // ensures getCurrentAnim() is used
        if (anim != null) {
            anim.draw(gc); // ensures draw(GraphicsContext) is used and visible
        }

        // Box base parameters
        double baseX = 700;
        double baseY = 150;
        double boxW = 300;
        double boxH = 220;

        // Apply pulse scaling
        double scaledW = boxW * boxScale;
        double scaledH = boxH * boxScale;
        double x = baseX - (scaledW - boxW) / 2;
        double y = baseY - (scaledH - boxH) / 2;

        // Choose colors based on whether the feedback was good or bad
        boolean good = scen.wasGood();
        Color fillColor = good ? Color.web("#D4F8D4") : Color.web("#F8D4D4"); // pale green/red
        Color borderColor = good ? Color.web("#4CAF50") : Color.web("#E53935");

        // Optional drop shadow to separate box from background
        gc.applyEffect(new DropShadow(12, Color.gray(0.3)));

        // Draw pulsing rounded box
        gc.setFill(fillColor);
        gc.fillRoundRect(x, y, scaledW, scaledH, 20, 20);
        gc.setStroke(borderColor);
        gc.setLineWidth(5);
        gc.strokeRoundRect(x, y, scaledW, scaledH, 20, 20);

        // Clear effect before drawing text so text isn't blurred
        gc.setEffect(null);

        // Draw the feedback text inside the box
        Font feedbackFont = Font.font("Comic Sans MS", FontWeight.BOLD, 22);
        drawWrappedText(gc, scen.getCurrentFeedback(), feedbackFont,
                x + 15, y + 40, scaledW - 30, scaledH - 60, Pos.TOP_CENTER);

        // Advance state after delay
        if (System.currentTimeMillis() - feedbackStart > FEEDBACK_DELAY_MS) {
            soundManager.stop();
            index++;
            state = index >= scenarios.size() ? State.END : State.PROMPT;
            promptAudioPlayed = false;
            stopBoxPulse();
        }

        btnGood.setVisible(false);
        btnBad.setVisible(false);
    }

    // ─── Render end-of-game screen ──────────────────────────────────────────────
    /** Draws end screen, stops audio, and shows Play/Exit controls. */
    private void renderEnd() {
        if (cloudEndScreen != null) {
            gc.drawImage(cloudEndScreen, 0, 0, SCREEN_W, SCREEN_H);
        }
        drawCentered(gc, "You've finished!", Font.font("Comic Sans MS", FontWeight.BOLD, 52),
                SCREEN_W / 2, 200, Color.BLACK);
        soundManager.stop();
        btnPlay.setVisible(true);
        btnExit.setVisible(true);
        btnGood.setVisible(false);
        btnBad.setVisible(false);
    }

    // ─── User interaction handlers ──────────────────────────────────────────────
    /** Marks current answer as good and transitions to FEEDBACK state. */
    private void onGood() {
        if (index >= scenarios.size()) return;
        Scenario scen = scenarios.get(index);
        scen.markGood();
        soundManager.stop();
        scen.playFeedbackSound(true, soundManager);
        state = State.FEEDBACK;
        feedbackStart = System.currentTimeMillis();
        startBoxPulse();
    }

    /** Marks current answer as bad and transitions to FEEDBACK state. */
    private void onBad() {
        if (index >= scenarios.size()) return;
        Scenario scen = scenarios.get(index);
        scen.markBad();
        soundManager.stop();
        scen.playFeedbackSound(false, soundManager);
        state = State.FEEDBACK;
        feedbackStart = System.currentTimeMillis();
        startBoxPulse();
    }

    /** Resets game state to the first scenario and returns to PROMPT. */
    private void onPlayAgain() {
        soundManager.stop();
        index = 0;
        state = State.PROMPT;
        promptAudioPlayed = false;
        stopBoxPulse();
    }

    // ─── Utility: word-wrap and draw multi-line text ─────────────────────────────
    /**
     * Simple word-wrap routine that draws multiple lines within a bounding box.
     * @param gc graphics context
     * @param text the text to draw
     * @param font font to use
     * @param x left position
     * @param y top position
     * @param maxWidth max width before wrapping
     * @param maxHeight (unused cap; reserved for future clipping)
     * @param align alignment hint (currently only the x,y are used)
     */
    private void drawWrappedText(GraphicsContext gc, String text, Font font,
                                 double x, double y, double maxWidth, double maxHeight, Pos align) {
        gc.setFont(font);
        gc.setFill(Color.BLACK);
        Text helper = new Text();
        helper.setFont(font);
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        double curY = y;

        for (String word : words) { // loop over words to build wrapped lines
            String testLine = line + word + " ";
            helper.setText(testLine);
            if (helper.getLayoutBounds().getWidth() > maxWidth) {
                gc.fillText(line.toString(), x, curY);
                line = new StringBuilder(word + " ");
                curY += helper.getLayoutBounds().getHeight() + 4;
            } else {
                line.append(word).append(" ");
            }
        }
        if (!line.isEmpty()) {
            gc.fillText(line.toString(), x, curY);
        }
    }

    // ─── Utility: draw centered text ─────────────────────────────────────────────
    /**
     * Draws a single line of text centered at the given coordinates.
     * @param gc graphics context
     * @param text text to draw
     * @param font font to use
     * @param cx center x
     * @param cy baseline reference around this y (adjusted by font bounds)
     * @param color fill color
     */
    private void drawCentered(GraphicsContext gc, String text, Font font, double cx, double cy, Color color) {
        gc.setFont(font);
        gc.setFill(color);
        Text helper = new Text(text);
        helper.setFont(font);
        double w = helper.getLayoutBounds().getWidth();
        double h = helper.getLayoutBounds().getHeight();
        gc.fillText(text, cx - w / 2, cy + h / 4);
    }

    // ─── Animate bounce for icons (used in buttons) ─────────────────────────────
    /** Adds a subtle continuous bounce animation to an ImageView. */
    private void animateBounce(ImageView view) {
        ScaleTransition scaleUp = new ScaleTransition(Duration.millis(500), view);
        scaleUp.setToX(1.1);
        scaleUp.setToY(1.1);
        ScaleTransition scaleDown = new ScaleTransition(Duration.millis(500), view);
        scaleDown.setToX(1.0);
        scaleDown.setToY(1.0);
        SequentialTransition bounce = new SequentialTransition(scaleUp, scaleDown);
        bounce.setCycleCount(ScaleTransition.INDEFINITE);
        bounce.play();
    }

    // ─── Pulse control for feedback box ──────────────────────────────────────────
    /** Starts the pulsing timeline for the feedback panel. */
    private void startBoxPulse() {
        stopBoxPulse();
        boxPulseTimeline = new javafx.animation.Timeline(
                // UPDATE: rename unused lambda parameter to 'ignored' to silence warnings
                new javafx.animation.KeyFrame(Duration.ZERO, ignored -> boxScale = 1.0),
                new javafx.animation.KeyFrame(Duration.millis(400), ignored -> boxScale = 1.08),
                new javafx.animation.KeyFrame(Duration.millis(800), ignored -> boxScale = 1.0)
        );
        boxPulseTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        boxPulseTimeline.play();
    }

    /** Stops and clears the pulsing timeline for the feedback panel. */
    private void stopBoxPulse() {
        if (boxPulseTimeline != null) {
            boxPulseTimeline.stop();
            boxPulseTimeline = null;
        }
        boxScale = 1.0;
    }

    // ─── Centralized sound manager to avoid overlapping players ─────────────────
    /**
     * Thin wrapper around {@link MediaPlayer} lifecycle for one-at-a-time playback.
     * Ensures prior audio is stopped/disposed before starting a new clip.
     */
    private static class SoundManager {
        private MediaPlayer player = null;

        /** Plays an audio file from disk if it exists; logs errors otherwise. */
        public void play(String filePath) {
            if (filePath == null || filePath.isBlank()) {
                System.err.println("SoundManager: empty file path.");
                return;
            }
            File f = new File(filePath);
            if (!f.exists()) {
                System.err.println("SoundManager: audio file missing: " + filePath);
                return;
            }
            stop();
            try {
                Media media = new Media(f.toURI().toString());
                player = new MediaPlayer(media);
                player.setOnError(() -> System.err.println("MediaPlayer error: " + player.getError().getMessage()));
                player.play();
            } catch (Exception e) {
                System.err.println("Failed to play sound: " + filePath + " cause: " + e.getMessage());
                e.printStackTrace();
            }
        }

        /** Stops and disposes the active player, if any. */
        public void stop() {
            if (player != null) {
                player.stop();
                player.dispose();
                player = null;
            }
        }
    }

    // ─── Scenario representation with validation and asset fallback ─────────────
    /**
     * Encapsulates a single scenario's prompt, assets, and feedback content.
     * Validates required fields and substitutes blank placeholders for missing images.
     */
    private static class Scenario {
        public final String prompt;
        public final String promptAudioPath;
        public final Image bgPromptImage, bgFeedbackImage;
        public final String posAudioPath, negAudioPath;
        public final String feedbackGood, feedbackBad;
        public final AnimationWrapper animGood, animBad;
        private boolean wasGood;

        public double bgX = 0, bgY = 0, bgW = SCREEN_W, bgH = SCREEN_H;

        /** Constructs a Scenario from a JSON object; throws for missing required fields. */
        public Scenario(JSONObject obj) {
            this.prompt = requireString(obj, "prompt_text");
            this.promptAudioPath = requireString(obj, "prompt_audio");

            this.bgPromptImage = loadImageSafely(obj.optString("background"));
            this.bgFeedbackImage = loadImageSafely(obj.optString("background_feedback"));

            JSONObject pos = obj.optJSONObject("positive");
            JSONObject neg = obj.optJSONObject("negative");
            if (pos == null || neg == null) throw new IllegalArgumentException("Missing positive or negative section.");

            this.feedbackGood = requireString(pos, "feedback_text");
            this.feedbackBad = requireString(neg, "feedback_text");
            this.posAudioPath = requireString(pos, "audio");
            this.negAudioPath = requireString(neg, "audio");

            // allow empty/zero to disable frame loading gracefully
            this.animGood = new AnimationWrapper(pos.optString("frames_pattern"), pos.optInt("frame_count", 0));
            this.animBad = new AnimationWrapper(neg.optString("frames_pattern"), neg.optInt("frame_count", 0));
        }

        private static String requireString(JSONObject o, String key) {
            String v = o.optString(key, "").trim();
            if (v.isEmpty()) throw new IllegalArgumentException("Missing or empty field: " + key);
            return v;
        }

        private Image loadImageSafely(String path) {
            if (path == null || path.isBlank()) {
                System.err.println("Image path blank, using placeholder.");
                return new Image("about:blank", SCREEN_W, SCREEN_H, false, true);
            }
            File f = new File(path);
            if (f.exists()) {
                return new Image(f.toURI().toString(), SCREEN_W, SCREEN_H, false, true);
            } else {
                System.err.println("Missing image asset: " + path + ", using blank placeholder.");
                return new Image("about:blank", SCREEN_W, SCREEN_H, false, true);
            }
        }

        /** Plays the prompt audio via the shared sound manager. */
        public void playPromptSound(SoundManager sm) {
            sm.play(promptAudioPath);
        }

        /** Plays positive/negative feedback audio depending on the choice. */
        public void playFeedbackSound(boolean good, SoundManager sm) {
            if (good) sm.play(posAudioPath);
            else sm.play(negAudioPath);
        }

        /** Marks the learner's choice as "good". */
        public void markGood() {
            wasGood = true;
        }

        /** Marks the learner's choice as "bad". */
        public void markBad() {
            wasGood = false;
        }

        /** @return whether the last choice was marked as good. */
        public boolean wasGood() {
            return wasGood;
        }

        /** @return feedback text matching the last choice. */
        public String getCurrentFeedback() {
            return wasGood ? feedbackGood : feedbackBad;
        }

        /** @return animation wrapper matching the last choice. */
        public AnimationWrapper getCurrentAnim() {
            return wasGood ? animGood : animBad;
        }
    }

    // ─── Animation wrapper with silent fallback if no frames ───────────────────
    /**
     * Simple frame-based animation loader/runner. If frames are missing or count=0,
     * draw() renders a neutral placeholder instead of failing.
     */
    private static class AnimationWrapper {
        private final List<Image> frames = new ArrayList<>();
        private int index = 0;
        private long lastFrameTime = System.currentTimeMillis();

        /**
         * @param pattern filename pattern with "{n}" token, e.g. "assets/anim/frame_{n}.png"
         * @param count number of frames to attempt to load; non-positive disables animation
         */
        public AnimationWrapper(String pattern, int count) {
            if (pattern == null || pattern.isBlank() || count <= 0) {
                // silent no-op when disabled
                return;
            }
            for (int i = 1; i <= count; i++) { // load each frame
                try {
                    String resolved = pattern.replace("{n}", String.valueOf(i));
                    File f = new File(resolved);
                    if (f.exists()) {
                        frames.add(new Image(f.toURI().toString()));
                    } else {
                        System.err.println("Missing animation frame: " + resolved);
                    }
                } catch (Exception e) {
                    System.err.println("Error loading frame for pattern " + pattern + ": " + e.getMessage());
                }
            }
        }

        /** Draws the current frame (or a placeholder if none). */
        public void draw(GraphicsContext gc) {
            double x = 700, y = 150, w = 220, h = 220;
            if (frames.isEmpty()) {
                // fallback placeholder (light gray box with "No anim")
                gc.setFill(Color.LIGHTGRAY);
                gc.fillRoundRect(x, y, w, h, 20, 20);
                gc.setStroke(Color.GRAY);
                gc.setLineWidth(4);
                gc.strokeRoundRect(x, y, w, h, 20, 20);
                gc.setFont(Font.font("System", 18));
                gc.setFill(Color.DARKGRAY);
                gc.fillText("No anim", x + 50, y + h / 2 + 5);
                return;
            }
            if (System.currentTimeMillis() - lastFrameTime > 150) {
                index = (index + 1) % frames.size();
                lastFrameTime = System.currentTimeMillis();
            }
            gc.drawImage(frames.get(index), x, y, w, h);
        }
    }
}

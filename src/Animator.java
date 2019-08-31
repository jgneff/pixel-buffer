/*
 * Copyright (C) 2019 John Neffenger
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;

/**
 * A JavaFX animation to test the support for a WritableImage backed by a
 * ByteBuffer. Run with a command like the following:
 * <pre>{@code
 * $HOME/opt/jdk-12.0.1+12/bin/java --add-modules=javafx.graphics \
 *     --module-path=$HOME/lib/javafx-sdk-13-dev/lib \
 *     -Dprism.order=sw -Djavafx.animation.pulse=2 Animator
 * }</pre>
 *
 * @see
 * <a href="https://github.com/javafxports/openjdk-jfx/pull/472">JDK-8167148</a>
 * - Add native rendering support by supporting WritableImages backed by NIO
 * ByteBuffers
 * @author John Neffenger
 */
public class Animator extends Application {

    private static final String TITLE = "Animator";
    private static final String IMAGE = "duke-waving.gif";

    private final ArrayList<BufferedImage> frames;
    private final int width;
    private final int height;
    private final int[] array;
    private final ImageView view;
    private final Parent root;

    private AnimationTimer animation;
    private boolean isRunning;

    private static ArrayList<BufferedImage> loadFrames(String filename) throws IOException {
        ArrayList<BufferedImage> list = new ArrayList<>();
        try (var input = Animator.class.getResourceAsStream(filename)) {
            if (input == null) {
                throw new IOException("Error loading image");
            }
            try (var stream = ImageIO.createImageInputStream(input)) {
                ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
                reader.setInput(stream);
                int count = reader.getNumImages(true);
                if (count == 0) {
                    throw new IllegalArgumentException("Error reading GIF image");
                }
                for (int i = 0; i < count; i++) {
                    list.add(reader.read(i));
                }
            }
        }
        return list;
    }

    public Animator() throws IOException {
        frames = loadFrames(IMAGE);
        BufferedImage first = frames.get(0);
        width = first.getWidth();
        height = first.getHeight();
        array = new int[width * height];
        view = new ImageView();
        root = new StackPane(view);
    }

    private void toggleTimers() {
        if (isRunning == true) {
            animation.stop();
            isRunning = false;
        } else {
            animation.start();
            isRunning = true;
        }
    }

    private void onMousePressed(MouseEvent event) {
        if (!event.isSynthesized()) {
            event.consume();
            toggleTimers();
        }
    }

    private void onKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        if (code == KeyCode.Q || code == KeyCode.ESCAPE) {
            event.consume();
            Platform.exit();
        }
    }

    @Override
    public void init() {

        /*
         * Uses the traditional method to convert AWT images to JavaFX images.
         */
        AnimationTimer animationOld = new AnimationTimer() {
            private int index;

            @Override
            public void handle(long now) {
                WritableImage jfxImage = new WritableImage(width, height);
                frames.get(index).getRGB(0, 0, width, height, array, 0, width);
                jfxImage.getPixelWriter().setPixels(0, 0, width, height,
                        PixelFormat.getIntArgbInstance(), array, 0, width);
                view.setImage(jfxImage);
                index = index == frames.size() - 1 ? 0 : index + 1;
            }
        };

        /*
         * Tests the new conversion method using the PixelBuffer class. Note
         * that this method should use two buffers to avoid writing to an image
         * in use by the QuantumRenderer thread.
         */
        AnimationTimer animationNew = new AnimationTimer() {
            private final ByteBuffer bb = ByteBuffer.allocateDirect(width * height * Integer.BYTES);
            private final PixelFormat<ByteBuffer> pf = PixelFormat.getByteBgraPreInstance();
            private final PixelBuffer<ByteBuffer> pb = new PixelBuffer<>(width, height, bb, pf);
            private final WritableImage jfxImage = new WritableImage(pb);
            private final int[] array = new int[width * height];

            private int index;

            @Override
            public void start() {
                super.start();
                bb.order(ByteOrder.nativeOrder());
                view.setImage(jfxImage);
            }

            @Override
            public void handle(long now) {
                frames.get(index).getRGB(0, 0, width, height, array, 0, width);
                bb.asIntBuffer().put(array);
                pb.updateBuffer((b) -> new Rectangle2D(0, 0, width, height));
                index = index == frames.size() - 1 ? 0 : index + 1;
            }
        };

        /*
         * Selects one of the AnimationTimer instances above.
         */
        animation = animationNew;
    }

    @Override
    public void start(Stage stage) {
        Scene scene = new Scene(root, 800, 600);
        scene.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        scene.addEventHandler(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        stage.setTitle(TITLE);
        stage.setScene(scene);
        stage.show();
        animation.start();
        isRunning = true;
    }

    @Override
    public void stop() {
        animation.stop();
        isRunning = false;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

/*
 * Copyright (C) 2019-2020 John Neffenger
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
import java.nio.IntBuffer;
import java.util.ArrayList;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;

/**
 * A JavaFX animation to test the support for a WritableImage backed by a
 * PixelBuffer. Run with a command like the following:
 * <pre>{@code
 * $HOME/opt/jdk-14.0.1/bin/java \
 *     --add-modules=javafx.graphics \
 *     --module-path=$HOME/lib/javafx-sdk-15/lib \
 *     -Dprism.order=sw -Djavafx.animation.pulse=2 \
 *     -cp dist/pixel-buffer.jar Animator
 * }</pre>
 *
 * @see
 * <a href="https://github.com/javafxports/openjdk-jfx/pull/472">
 * javafxports/openjdk-jfx#472</a> JDK-8167148: Add native rendering support by
 * supporting WritableImages backed by NIO ByteBuffers
 * @author John Neffenger
 */
public class Animator extends Application {

    private static final String TITLE = "Animator";
    private static final String IMAGE = "duke-waving.gif";

    private final ArrayList<BufferedImage> frames;
    private final int width;
    private final int height;
    private final ImageView view;
    private final StackPane root;

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

    private void onKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        if (code == KeyCode.SPACE) {
            event.consume();
            toggleTimers();
        } else if (code == KeyCode.Q || code == KeyCode.ESCAPE) {
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
            private final int[] array = new int[width * height];

            private int index;

            @Override
            public void handle(long now) {
                WritableImage image = new WritableImage(width, height);
                frames.get(index).getRGB(0, 0, width, height, array, 0, width);
                image.getPixelWriter().setPixels(0, 0, width, height,
                        PixelFormat.getIntArgbInstance(), array, 0, width);
                view.setImage(image);
                index = index == frames.size() - 1 ? 0 : index + 1;
            }
        };

        /*
         * Tests the new conversion method using the PixelBuffer class. Note
         * that this method should (but doesn't) use two buffers to avoid
         * writing to an image in use by the QuantumRenderer thread.
         */
        AnimationTimer animationNewByte = new AnimationTimer() {
            private final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(width * height * Integer.BYTES);
            private final PixelFormat<ByteBuffer> pixelFormat = PixelFormat.getByteBgraPreInstance();
            private final PixelBuffer<ByteBuffer> pixelBuffer = new PixelBuffer<>(width, height, byteBuffer, pixelFormat);
            private final WritableImage image = new WritableImage(pixelBuffer);
            private final int[] array = new int[width * height];

            private int index;

            @Override
            public void start() {
                super.start();
                byteBuffer.order(ByteOrder.nativeOrder());
                view.setImage(image);
            }

            @Override
            public void handle(long now) {
                frames.get(index).getRGB(0, 0, width, height, array, 0, width);
                byteBuffer.asIntBuffer().put(array);
                pixelBuffer.updateBuffer((b) -> new Rectangle2D(0, 0, width, height));
                index = index == frames.size() - 1 ? 0 : index + 1;
            }
        };

        /*
         * Tests the new conversion method using the PixelBuffer class. Note
         * that this method should (but doesn't) use two buffers to avoid
         * writing to an image in use by the QuantumRenderer thread.
         */
        AnimationTimer animationNewInt = new AnimationTimer() {
            private final IntBuffer intBuffer = IntBuffer.allocate(width * height);
            private final PixelFormat<IntBuffer> pixelFormat = PixelFormat.getIntArgbPreInstance();
            private final PixelBuffer<IntBuffer> pixelBuffer = new PixelBuffer<>(width, height, intBuffer, pixelFormat);
            private final WritableImage image = new WritableImage(pixelBuffer);

            private int index;

            @Override
            public void start() {
                super.start();
                view.setImage(image);
            }

            @Override
            public void handle(long now) {
                frames.get(index).getRGB(0, 0, width, height, intBuffer.array(), 0, width);
                pixelBuffer.updateBuffer((b) -> new Rectangle2D(0, 0, width, height));
                index = index == frames.size() - 1 ? 0 : index + 1;
            }
        };

        /*
         * Selects one of the AnimationTimer instances above.
         */
//        animation = animationOld;
//        animation = animationNewByte;
        animation = animationNewInt;
    }

    @Override
    public void start(Stage stage) {
        Scene scene = new Scene(root, 800, 600);
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

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
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;
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
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/**
 * A JavaFX application to test the new {@link PixelBuffer} class. Run with a
 * command like the following:
 * <pre>{@code
 * $HOME/opt/jdk-14.0.1/bin/java \
 *     --add-modules=javafx.graphics \
 *     --module-path=$HOME/lib/javafx-sdk-15/lib \
 *     -cp dist/pixel-buffer.jar Viewer
 * }</pre>
 *
 * @see
 * <a href="https://github.com/javafxports/openjdk-jfx/pull/472">
 * javafxports/openjdk-jfx#472</a> JDK-8167148: Add native rendering support by
 * supporting WritableImages backed by NIO ByteBuffers
 * @author John Neffenger
 */
public class Viewer extends Application {

    private static final String TITLE = "Viewer";
    private static final String IMAGE = "PNG_transparency_demonstration_1.png";
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final Color BACKGROUND = Color.grayRgb(224);

    private final BufferedImage awtSolid;
    private final BufferedImage awtImage;
    private final int width;
    private final int height;
    private final ImageView view;
    private final StackPane root;
    private final List<Runnable> methods;

    private int index;

    private static void saveImage(String name, IntBuffer buffer, int width, int height) throws IOException {
        IntBuffer src = buffer.duplicate().clear();
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, src.get());
            }
        }
        ImageIO.write(image, "png", new File(name));
    }

    private static BufferedImage solidImage(int width, int height) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, java.awt.Color.DARK_GRAY.getRGB());
            }
        }
        return image;
    }

    private static BufferedImage loadImage(String filename) throws IOException {
        try (var input = Animator.class.getResourceAsStream(filename)) {
            return ImageIO.read(input);
        }
    }

    public Viewer() throws IOException {
        awtImage = loadImage(IMAGE);
        width = awtImage.getWidth();
        height = awtImage.getHeight();
        awtSolid = solidImage(width, height);
        view = new ImageView();
        root = new StackPane(view);

        methods = Arrays.asList(
                this::clear,
                this::oldDraw,
                this::clear,
                this::oldCopy,
                this::clear,
                this::newDraw,
                this::clear,
                this::newCopy,
                this::clear,
                this::oneCopy
        );
    }

    /**
     * Clears the view with a solid dark gray image.
     */
    private void clear() {
        var array = new int[width * height];
        var image = new WritableImage(width, height);
        // Returns the pixels in the default RGB color model (TYPE_INT_ARGB).
        awtSolid.getRGB(0, 0, width, height, array, 0, width);
        image.getPixelWriter().setPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), array, 0, width);
        view.setImage(image);
    }

    /**
     * Converts the image like {@code javafx.embed.swing.SwingFXUtils} using an
     * intermediate {@code BufferedImage} of type TYPE_INT_ARGB_PRE.
     */
    private void oldDraw() {
        System.out.println("oldDraw: Draws to intermediate AWT image; writes to JavaFX image.");
        var copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        var graphics = copy.createGraphics();
        graphics.drawImage(awtImage, 0, 0, null);
        graphics.dispose();
        var image = new WritableImage(width, height);

        int[] data = ((DataBufferInt) copy.getRaster().getDataBuffer()).getData();
        image.getPixelWriter().setPixels(0, 0, width, height,
                PixelFormat.getIntArgbPreInstance(), data, 0, width);
        view.setImage(image);
    }

    /**
     * Converts the image using an intermediate integer array.
     */
    private void oldCopy() {
        System.out.println("oldCopy: Copies to intermediate array; writes to JavaFX image.");
        var array = new int[width * height];
        var image = new WritableImage(width, height);

        // Returns the pixels in the default RGB color model (TYPE_INT_ARGB).
        awtImage.getRGB(0, 0, width, height, array, 0, width);
        image.getPixelWriter().setPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), array, 0, width);
        view.setImage(image);
    }

    /**
     * Converts the image using the new {@link PixelBuffer} class and an
     * intermediate {@code BufferedImage} of type TYPE_INT_ARGB_PRE.
     */
    private void newDraw() {
        System.out.println("newDraw: Draws to intermediate AWT image; updates pixel buffer.");
        var copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        var graphics = copy.createGraphics();
        graphics.drawImage(awtImage, 0, 0, null);
        graphics.dispose();

        // Creates a PixelBuffer with the BYTE_BGRA_PRE pixel format.
        var byteBuffer = ByteBuffer.allocateDirect(width * height * Integer.BYTES);
        var pixelFormat = PixelFormat.getByteBgraPreInstance();
        var pixelBuffer = new PixelBuffer<>(width, height, byteBuffer, pixelFormat);
        var image = new WritableImage(pixelBuffer);

        int[] data = ((DataBufferInt) copy.getRaster().getDataBuffer()).getData();
        byteBuffer.order(ByteOrder.nativeOrder()).asIntBuffer().put(data);
        pixelBuffer.updateBuffer((b) -> new Rectangle2D(0, 0, width, height));
        view.setImage(image);
    }

    /**
     * Converts the image using the new {@link PixelBuffer} class and an
     * intermediate integer array.
     */
    private void newCopy() {
        System.out.println("newCopy: Copies to intermediate array; updates pixel buffer (wrong alpha).");
        var array = new int[width * height];

        // Creates a PixelBuffer with the BYTE_BGRA_PRE pixel format.
        var byteBuffer = ByteBuffer.allocateDirect(width * height * Integer.BYTES);
        var pixelFormat = PixelFormat.getByteBgraPreInstance();
        var pixelBuffer = new PixelBuffer<>(width, height, byteBuffer, pixelFormat);
        var image = new WritableImage(pixelBuffer);

        // Returns the pixels in the default RGB color model (TYPE_INT_ARGB).
        awtImage.getRGB(0, 0, width, height, array, 0, width);
        byteBuffer.order(ByteOrder.nativeOrder()).asIntBuffer().put(array);
        pixelBuffer.updateBuffer((b) -> new Rectangle2D(0, 0, width, height));
        view.setImage(image);
    }

    /**
     * Converts the image by copying the pixels just once directly into the
     * integer array that backs the {@link PixelBuffer<IntBuffer>}.
     */
    private void oneCopy() {
        System.out.println("oneCopy: Copies directly to integer pixel buffer (wrong alpha).");

        // Creates a PixelBuffer with the INT_ARGB_PRE pixel format.
        var intBuffer = IntBuffer.allocate(width * height);
        var pixelFormat = PixelFormat.getIntArgbPreInstance();
        var pixelBuffer = new PixelBuffer<>(width, height, intBuffer, pixelFormat);
        var image = new WritableImage(pixelBuffer);

        // Returns the pixels in the default RGB color model (TYPE_INT_ARGB).
        awtImage.getRGB(0, 0, width, height, intBuffer.array(), 0, width);
        pixelBuffer.updateBuffer((b) -> new Rectangle2D(0, 0, width, height));
        view.setImage(image);
    }

    private void onKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        if (code == KeyCode.SPACE) {
            event.consume();
            Platform.runLater(methods.get(index));
            if (index == 0) {
                System.out.println();
            }
            index = index == methods.size() - 1 ? 0 : index + 1;
        } else if (code == KeyCode.Q || code == KeyCode.ESCAPE) {
            event.consume();
            Platform.exit();
        }
    }

    @Override
    public void start(Stage stage) throws IOException {
        Scene scene = new Scene(root, WIDTH, HEIGHT, BACKGROUND);
        scene.addEventHandler(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        stage.setTitle(TITLE);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

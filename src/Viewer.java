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

import java.awt.Color;
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
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/**
 * A JavaFX application to test the new {@link PixelBuffer} class. Run with a
 * command like the following:
 * <pre>{@code
 * $HOME/opt/jdk-13.0.2/bin/java --add-modules=javafx.graphics \
 *     --module-path=$HOME/lib/javafx-sdk-14/lib Viewer
 * }</pre>
 *
 * @see
 * <a href="https://github.com/javafxports/openjdk-jfx/pull/472">JDK-8167148</a>
 * - Add native rendering support by supporting WritableImages backed by NIO
 * ByteBuffers
 * @author John Neffenger
 */
public class Viewer extends Application {

    private static final String TITLE = "Viewer";
    private static final String IMAGE = "Renoir_by_Bazille.jpg";

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
                image.setRGB(x, y, Color.DARK_GRAY.getRGB());
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
     * Clears the view with a solid red image.
     */
    private void clear() {
        // Returns the pixels in the default RGB color model (TYPE_INT_ARGB).
        var intArray = new int[width * height];
        awtSolid.getRGB(0, 0, width, height, intArray, 0, width);
        var jfxImage = new WritableImage(width, height);
        jfxImage.getPixelWriter().setPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), intArray, 0, width);
        view.setImage(jfxImage);
    }

    /**
     * Converts the image as {@code javafx.embed.swing.SwingFXUtils} does by
     * using an intermediate {@code BufferedImage} of type TYPE_INT_ARGB_PRE.
     */
    private void oldDraw() {
        System.out.println("oldDraw: Drawing to intermediate AWT image; writing to JavaFX image.");
        var copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        var graphics = copy.createGraphics();
        graphics.drawImage(awtImage, 0, 0, null);
        graphics.dispose();
        int[] data = ((DataBufferInt) copy.getRaster().getDataBuffer()).getData();
        var jfxImage = new WritableImage(width, height);
        jfxImage.getPixelWriter().setPixels(0, 0, width, height,
                PixelFormat.getIntArgbPreInstance(), data, 0, width);
        view.setImage(jfxImage);
    }

    /**
     * Converts the image using an intermediate integer array.
     */
    private void oldCopy() {
        System.out.println("oldCopy: Copying to intermediate array; writing to JavaFX image.");
        // Returns the pixels in the default RGB color model (TYPE_INT_ARGB).
        var intArray = new int[width * height];
        awtImage.getRGB(0, 0, width, height, intArray, 0, width);
        var jfxImage = new WritableImage(width, height);
        jfxImage.getPixelWriter().setPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), intArray, 0, width);
        view.setImage(jfxImage);
    }

    /**
     * Converts the image using the new {@link PixelBuffer} class and an
     * intermediate {@code BufferedImage} of type TYPE_INT_ARGB_PRE.
     */
    private void newDraw() {
        System.out.println("newDraw: Drawing to intermediate AWT image; updating pixel buffer.");
        var copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        var graphics = copy.createGraphics();
        graphics.drawImage(awtImage, 0, 0, null);
        graphics.dispose();
        int[] data = ((DataBufferInt) copy.getRaster().getDataBuffer()).getData();

        // Creating a PixelBuffer using BYTE_BGRA_PRE pixel format.
        var byteBuffer = ByteBuffer.allocateDirect(width * height * Integer.BYTES);
        var bytePixelBuffer = new PixelBuffer<>(width, height, byteBuffer,
                PixelFormat.getByteBgraPreInstance());
        var byteImage = new WritableImage(bytePixelBuffer);

        byteBuffer.order(ByteOrder.nativeOrder()).asIntBuffer().put(data);
        bytePixelBuffer.updateBuffer((b) -> new Rectangle2D(0, 0, width, height));
        view.setImage(byteImage);
    }

    /**
     * Converts the image using the new {@link PixelBuffer} class and an
     * intermediate integer array.
     */
    private void newCopy() {
        System.out.println("newCopy: Copying to intermediate array; updating pixel buffer.");
        // Returns the pixels in the default RGB color model (TYPE_INT_ARGB).
        var intArray = new int[width * height];
        awtImage.getRGB(0, 0, width, height, intArray, 0, width);

        // Creating a PixelBuffer using BYTE_BGRA_PRE pixel format.
        var byteBuffer = ByteBuffer.allocateDirect(width * height * Integer.BYTES);
        var bytePixelBuffer = new PixelBuffer<>(width, height, byteBuffer,
                PixelFormat.getByteBgraPreInstance());
        var byteImage = new WritableImage(bytePixelBuffer);

        byteBuffer.order(ByteOrder.nativeOrder()).asIntBuffer().put(intArray);
        bytePixelBuffer.updateBuffer((b) -> new Rectangle2D(0, 0, width, height));
        view.setImage(byteImage);
    }

    /**
     * Converts the image by copying the pixels just once directly into the
     * integer array that backs the {@link PixelBuffer<IntBuffer>}.
     */
    private void oneCopy() {
        System.out.println("oneCopy: Copying directly to integer pixel buffer.");

        // Creating a PixelBuffer using INT_ARGB_PRE pixel format.
        var intBuffer = IntBuffer.allocate(width * height);
        var intPixelBuffer = new PixelBuffer<>(width, height, intBuffer,
                PixelFormat.getIntArgbPreInstance());
        var intImage = new WritableImage(intPixelBuffer);

        // Returns the pixels in the default RGB color model (TYPE_INT_ARGB).
        awtImage.getRGB(0, 0, width, height, intBuffer.array(), 0, width);
        intPixelBuffer.updateBuffer((b) -> new Rectangle2D(0, 0, width, height));
        view.setImage(intImage);
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
        Scene scene = new Scene(root, 501, 600);
        scene.addEventHandler(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        stage.setTitle(TITLE);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

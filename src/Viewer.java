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
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
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
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/**
 * A JavaFX application to test the new {@link PixelBuffer} class. Run with a
 * command like the following:
 * <pre>{@code
 * $HOME/opt/jdk-12.0.1+12/bin/java \
 *     --add-modules=javafx.graphics \
 *     --module-path=$HOME/lib/javafx-sdk-13-dev/lib \
 *     Viewer
 * }</pre>
 *
 * @see
 * <a href="https://github.com/javafxports/openjdk-jfx/pull/472">JDK-8167148</a>
 * - Add native rendering support by supporting WritableImages backed by NIO
 * ByteBuffers
 * @author John Neffenger
 */
public class Viewer extends Application {

    private final BufferedImage awtImage;
    private final int width;
    private final int height;
    private final ImageView view;
    private final Parent root;

    private final ByteBuffer byteBuffer;
    private final PixelFormat<ByteBuffer> pixelFormat;
    private final PixelBuffer<ByteBuffer> pixelBuffer;
    private final WritableImage jfxImage;
    private final int[] intArray;

    private final IntBuffer intBuffer;
    private final PixelFormat<IntBuffer> intPixelFormat;
    private final PixelBuffer<IntBuffer> intPixelBuffer;

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

    private static BufferedImage loadImage(String filename) throws IOException {
        try (var input = Animator.class.getResourceAsStream(filename)) {
            return ImageIO.read(input);
        }
    }

    public Viewer() throws IOException {
        awtImage = loadImage("Renoir_by_Bazille.jpg");
        width = awtImage.getWidth();
        height = awtImage.getHeight();
        view = new ImageView();
        root = new StackPane(view);

        byteBuffer = ByteBuffer.allocateDirect(width * height * Integer.BYTES);
        pixelFormat = PixelFormat.getByteBgraPreInstance();
        pixelBuffer = new PixelBuffer<>(width, height, byteBuffer, pixelFormat);
        jfxImage = new WritableImage(pixelBuffer);
        intArray = new int[width * height];

        intBuffer = IntBuffer.allocate(width * height);
        intPixelFormat = PixelFormat.getIntArgbInstance();
        intPixelBuffer = new PixelBuffer<>(width, height, intBuffer, intPixelFormat);
    }

    private void onKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        if (code == KeyCode.Q || code == KeyCode.ESCAPE) {
            event.consume();
            Platform.exit();
        }
    }

    /**
     * Converts the image like {@code javafx.embed.swing.SwingFXUtils} using an
     * intermediate ARGB {@code BufferedImage}.
     */
    private void oldDraw() {
        var copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = copy.createGraphics();
        graphics.drawImage(awtImage, 0, 0, null);
        int[] data = ((DataBufferInt) copy.getRaster().getDataBuffer()).getData();
        jfxImage.getPixelWriter().setPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), data, 0, width);
    }

    /**
     * Converts the image using an intermediate integer array.
     */
    private void oldCopy() {
        awtImage.getRGB(0, 0, width, height, intArray, 0, width);
        jfxImage.getPixelWriter().setPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), intArray, 0, width);
    }

    /**
     * Converts the image using the new {@link PixelBuffer} class and an
     * intermediate ARGB {@code BufferedImage}.
     */
    private void newDraw() {
        var copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = copy.createGraphics();
        graphics.drawImage(awtImage, 0, 0, null);
        int[] data = ((DataBufferInt) copy.getRaster().getDataBuffer()).getData();
        byteBuffer.order(ByteOrder.nativeOrder()).asIntBuffer().put(data);
        pixelBuffer.updateBuffer((b) -> new Rectangle2D(0, 0, width, height));
    }

    /**
     * Converts the image using the new {@link PixelBuffer} class and an
     * intermediate integer array.
     */
    private void newCopy() {
        awtImage.getRGB(0, 0, width, height, intArray, 0, width);
        byteBuffer.order(ByteOrder.nativeOrder()).asIntBuffer().put(intArray);
        pixelBuffer.updateBuffer((b) -> new Rectangle2D(0, 0, width, height));
    }

    /**
     * Converts the image by copying the pixels just once directly into the
     * integer array that backs the {@link PixelBuffer<IntBuffer>}. Fails with:
     * <pre>{@code
     * java.lang.UnsupportedOperationException: Format Not yet supported
     * }</pre>
     */
    private void oneCopy() {
        awtImage.getRGB(0, 0, width, height, intBuffer.array(), 0, width);
        intPixelBuffer.updateBuffer((b) -> new Rectangle2D(0, 0, width, height));
    }

    @Override
    public void start(Stage stage) throws IOException {
        Scene scene = new Scene(root, 501, 600);
        scene.addEventHandler(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        stage.setTitle("Viewer");
        stage.setScene(scene);
        view.setImage(jfxImage);
        stage.show();

//        oldDraw();
//        oldCopy();
//        newDraw();
        newCopy();

//        oneCopy();
//        view.setImage(new WritableImage(intPixelBuffer));
    }

    public static void main(String[] args) {
        launch(args);
    }
}

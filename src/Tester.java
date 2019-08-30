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

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/**
 * A JavaFX application to test the new {@link PixelBuffer} class on an image
 * with transparency. Run with a command like the following:
 * <pre>{@code
 * $HOME/opt/jdk-12.0.1+12/bin/java --add-modules=javafx.graphics \
 *     --module-path=$HOME/lib/javafx-sdk-13-dev/lib Tester
 * }</pre>
 *
 * @see
 * <a href="https://github.com/javafxports/openjdk-jfx/pull/472">JDK-8167148</a>
 * - Add native rendering support by supporting WritableImages backed by NIO
 * ByteBuffers
 * @author John Neffenger
 */
public class Tester extends Application {

    private static final Color JFX_BACKGROUND = Color.grayRgb(224);
    private static final java.awt.Color AWT_BACKGROUND = new java.awt.Color(224, 224, 224);

    private final BufferedImage pngImage;
    private final int width;
    private final int height;
    private final ImageView view;
    private final Pane root;

    private final WritableImage jfxImage;

    private final int[] rgbArray;
    private final ByteBuffer byteBuffer;
    private final PixelBuffer<ByteBuffer> pixelBuffer;
    private final WritableImage nioImage;

    private final List<Callable<Image>> methods;

    private String message;
    private int index;

    private static BufferedImage loadImage(String filename) throws IOException {
        try (var input = Animator.class.getResourceAsStream(filename)) {
            return ImageIO.read(input);
        }
    }

    public Tester() throws IOException {
        pngImage = loadImage("PNG_transparency_demonstration_1.png");
        width = pngImage.getWidth();
        height = pngImage.getHeight();
        view = new ImageView();
        root = new StackPane(view);

        jfxImage = new WritableImage(width, height);

        rgbArray = new int[width * height];
        byteBuffer = ByteBuffer.allocateDirect(width * height * Integer.BYTES);
        pixelBuffer = new PixelBuffer<>(width, height, byteBuffer, PixelFormat.getByteBgraPreInstance());
        nioImage = new WritableImage(pixelBuffer);

        methods = Arrays.asList(
                this::drawWrite,
                this::drawWritePre,
                this::drawPreWritePre,
                this::drawPreWrite,
                this::drawRgbWrite,
                this::drawBgrWrite,
                this::drawAbgrWrite,
                this::drawAbgrPreWrite,
                this::copyWrite,
                this::copyWritePre,
                this::nioDrawPrePut,
                this::nioCopyPut
        );
    }

    private Image oldDraw(BufferedImage awtImage, PixelFormat<IntBuffer> format) {
        Graphics2D graphics = awtImage.createGraphics();
        graphics.setBackground(AWT_BACKGROUND);
        graphics.clearRect(0, 0, width, height);
        graphics.drawImage(pngImage, 0, 0, null);
        graphics.dispose();
        int[] data = ((DataBufferInt) awtImage.getRaster().getDataBuffer()).getData();
        jfxImage.getPixelWriter().setPixels(0, 0, width, height, format, data, 0, width);
        return jfxImage;
    }

    private Image oldDraw4Byte(BufferedImage awtImage, PixelFormat<ByteBuffer> format) {
        Graphics2D graphics = awtImage.createGraphics();
        graphics.setBackground(AWT_BACKGROUND);
        graphics.clearRect(0, 0, width, height);
        graphics.drawImage(pngImage, 0, 0, null);
        graphics.dispose();
        byte[] data = ((DataBufferByte) awtImage.getRaster().getDataBuffer()).getData();
        jfxImage.getPixelWriter().setPixels(0, 0, width, height, format, data, 0, width * Integer.BYTES);
        return jfxImage;
    }

    private Image nioDraw(BufferedImage awtImage) {
        Graphics2D graphics = awtImage.createGraphics();
        graphics.setBackground(AWT_BACKGROUND);
        graphics.clearRect(0, 0, width, height);
        graphics.drawImage(pngImage, 0, 0, null);
        graphics.dispose();
        int[] data = ((DataBufferInt) awtImage.getRaster().getDataBuffer()).getData();
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(data);
        pixelBuffer.updateBuffer((b) -> new Rectangle2D(0, 0, width, height));
        return nioImage;
    }

    private Image oldCopy(PixelFormat<IntBuffer> format) {
        pngImage.getRGB(0, 0, width, height, rgbArray, 0, width);
        jfxImage.getPixelWriter().setPixels(0, 0, width, height, format, rgbArray, 0, width);
        return jfxImage;
    }

    private Image nioCopy() {
        pngImage.getRGB(0, 0, width, height, rgbArray, 0, width);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(rgbArray);
        pixelBuffer.updateBuffer((b) -> new Rectangle2D(0, 0, width, height));
        return nioImage;
    }

    private Image drawWrite() {
        message = String.format("%d - Drawing TYPE_INT_ARGB\tWriting IntArgbInstance", index + 1);
        System.out.println(message);
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        return oldDraw(awtImage, PixelFormat.getIntArgbInstance());
    }

    private Image drawWritePre() {
        message = String.format("%d - Drawing TYPE_INT_ARGB\tWriting IntArgbPreInstance (working?)", index + 1);
        System.out.println(message);
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        return oldDraw(awtImage, PixelFormat.getIntArgbPreInstance());
    }

    private Image drawPreWritePre() {
        message = String.format("%d - Drawing TYPE_INT_ARGB_PRE\tWriting IntArgbPreInstance", index + 1);
        System.out.println(message);
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        return oldDraw(awtImage, PixelFormat.getIntArgbPreInstance());
    }

    private Image drawPreWrite() {
        message = String.format("%d - Drawing TYPE_INT_ARGB_PRE\tWriting IntArgbInstance (working?)", index + 1);
        System.out.println(message);
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        return oldDraw(awtImage, PixelFormat.getIntArgbInstance());
    }

    private Image drawRgbWrite() {
        message = String.format("%d - Drawing TYPE_INT_RGB\tWriting IntArgbInstance (blank)", index + 1);
        System.out.println(message);
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        return oldDraw(awtImage, PixelFormat.getIntArgbInstance());
    }

    private Image drawBgrWrite() {
        message = String.format("%d - Drawing TYPE_INT_BGR\tWriting IntArgbInstance (blank)", index + 1);
        System.out.println(message);
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_BGR);
        return oldDraw(awtImage, PixelFormat.getIntArgbInstance());
    }

    private Image drawAbgrWrite() {
        message = String.format("%d - Drawing TYPE_4BYTE_ABGR\tWriting ByteBgraInstance (wrong colors)", index + 1);
        System.out.println(message);
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        return oldDraw4Byte(awtImage, PixelFormat.getByteBgraInstance());
    }

    private Image drawAbgrPreWrite() {
        message = String.format("%d - Drawing TYPE_4BYTE_ABGR_PRE\tWriting ByteBgraPreInstance (wrong colors)", index + 1);
        System.out.println(message);
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR_PRE);
        return oldDraw4Byte(awtImage, PixelFormat.getByteBgraPreInstance());
    }

    private Image copyWrite() {
        message = String.format("%d - Copying TYPE_INT_ARGB\tWriting IntArgbInstance", index + 1);
        System.out.println(message);
        return oldCopy(PixelFormat.getIntArgbInstance());
    }

    private Image copyWritePre() {
        message = String.format("%d - Copying TYPE_INT_ARGB\tWriting IntArgbPreInstance (wrong alpha blending)", index + 1);
        System.out.println(message);
        return oldCopy(PixelFormat.getIntArgbPreInstance());
    }

    private Image nioDrawPrePut() {
        message = String.format("%d - Drawing TYPE_INT_ARGB_PRE\tPutting ByteBgraPreInstance", index + 1);
        System.out.println(message);
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        return nioDraw(awtImage);
    }

    private Image nioCopyPut() {
        message = String.format("%d - Copying TYPE_INT_ARGB\tPutting ByteBgraPreInstance (wrong alpha blending)", index + 1);
        System.out.println(message);
        return nioCopy();
    }

    private void onKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        if (code == KeyCode.SPACE) {
            event.consume();
            try {
                view.setImage(methods.get(index).call());
            } catch (Exception ex) {
                System.err.println(ex);
            } finally {
                index = index == methods.size() - 1 ? 0 : index + 1;
                if (index == 0) {
                    System.out.println();
                }
            }
        } else if (code == KeyCode.Q || code == KeyCode.ESCAPE) {
            event.consume();
            Platform.exit();
        }
    }

    @Override
    public void start(Stage stage) throws IOException {
        Scene scene = new Scene(root, 800, 600, JFX_BACKGROUND);
        scene.addEventHandler(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        stage.setTitle("Viewer");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

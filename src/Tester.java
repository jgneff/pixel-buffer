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

    private static final Color BACKGROUND = Color.grayRgb(224);
    private static final int MAX_EXTRA_TAB = 24;
    private static final String MSG_OK = "OK";
    private static final String MSG_ALPHA = "Wrong alpha";
    private static final String MSG_BLANK = "Blank image";
    private static final String MSG_COLORS = "Wrong colors";

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

    private int index;

    private static BufferedImage loadImage(String filename) throws IOException {
        try (var input = Animator.class.getResourceAsStream(filename)) {
            return ImageIO.read(input);
        }
    }

    private static String getName(int type) {
        String name = null;
        switch (type) {
            case BufferedImage.TYPE_CUSTOM:
                name = "CUSTOM";
                break;
            case BufferedImage.TYPE_INT_RGB:
                name = "INT_RGB";
                break;
            case BufferedImage.TYPE_INT_ARGB:
                name = "INT_ARGB";
                break;
            case BufferedImage.TYPE_INT_ARGB_PRE:
                name = "INT_ARGB_PRE";
                break;
            case BufferedImage.TYPE_INT_BGR:
                name = "INT_BGR";
                break;
            case BufferedImage.TYPE_3BYTE_BGR:
                name = "3BYTE_BGR";
                break;
            case BufferedImage.TYPE_4BYTE_ABGR:
                name = "4BYTE_ABGR";
                break;
            case BufferedImage.TYPE_4BYTE_ABGR_PRE:
                name = "4BYTE_ABGR_PRE";
                break;
            default:
                name = "unknown";
                break;
        }
        return name;
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

    private void log(int type, PixelFormat format, String comment) {
        String awtType = getName(type);
        String jfxType = format.getType().toString();
        StringBuffer message = new StringBuffer();
        message.append(String.format("%02d - Source: %s", index + 1, awtType));
        message.append(message.length() < MAX_EXTRA_TAB ? "\t\t" : "\t");
        message.append(String.format("Target: %s\t%s", jfxType, comment));
        System.out.println(message);
    }

    private void log(BufferedImage image, PixelFormat format, String comment) {
        log(image.getType(), format, comment);
    }

    private Image oldDraw(BufferedImage awtImage, PixelFormat<IntBuffer> format) {
        Graphics2D graphics = awtImage.createGraphics();
        graphics.drawImage(pngImage, 0, 0, null);
        graphics.dispose();
        int[] data = ((DataBufferInt) awtImage.getRaster().getDataBuffer()).getData();
        jfxImage.getPixelWriter().setPixels(0, 0, width, height, format, data, 0, width);
        return jfxImage;
    }

    private Image oldDraw4Byte(BufferedImage awtImage, PixelFormat<ByteBuffer> format) {
        Graphics2D graphics = awtImage.createGraphics();
        graphics.drawImage(pngImage, 0, 0, null);
        graphics.dispose();
        byte[] data = ((DataBufferByte) awtImage.getRaster().getDataBuffer()).getData();
        jfxImage.getPixelWriter().setPixels(0, 0, width, height, format, data, 0, width * Integer.BYTES);
        return jfxImage;
    }

    private Image nioDraw(BufferedImage awtImage) {
        Graphics2D graphics = awtImage.createGraphics();
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
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var format = PixelFormat.getIntArgbInstance();
        log(awtImage, format, MSG_OK);
        return oldDraw(awtImage, format);
    }

    private Image drawWritePre() {
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var format = PixelFormat.getIntArgbPreInstance();
        log(awtImage, format, MSG_ALPHA);
        return oldDraw(awtImage, format);
    }

    private Image drawPreWritePre() {
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        var format = PixelFormat.getIntArgbPreInstance();
        log(awtImage, format, MSG_OK);
        return oldDraw(awtImage, format);
    }

    private Image drawPreWrite() {
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        var format = PixelFormat.getIntArgbInstance();
        log(awtImage, format, MSG_ALPHA);
        return oldDraw(awtImage, format);
    }

    private Image drawRgbWrite() {
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var format = PixelFormat.getIntArgbInstance();
        log(awtImage, format, MSG_BLANK);
        return oldDraw(awtImage, format);
    }

    private Image drawBgrWrite() {
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_BGR);
        var format = PixelFormat.getIntArgbInstance();
        log(awtImage, format, MSG_BLANK);
        return oldDraw(awtImage, format);
    }

    private Image drawAbgrWrite() {
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        var format = PixelFormat.getByteBgraInstance();
        log(awtImage, format, MSG_COLORS);
        return oldDraw4Byte(awtImage, format);
    }

    private Image drawAbgrPreWrite() {
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR_PRE);
        var format = PixelFormat.getByteBgraPreInstance();
        log(awtImage, format, MSG_COLORS);
        return oldDraw4Byte(awtImage, format);
    }

    private Image copyWrite() {
        int type = BufferedImage.TYPE_INT_ARGB;
        var format = PixelFormat.getIntArgbInstance();
        log(type, format, MSG_OK);
        return oldCopy(format);
    }

    private Image copyWritePre() {
        int type = BufferedImage.TYPE_INT_ARGB;
        var format = PixelFormat.getIntArgbPreInstance();
        log(type, format, MSG_ALPHA);
        return oldCopy(format);
    }

    private Image nioDrawPrePut() {
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        var format = pixelBuffer.getPixelFormat();
        log(awtImage, format, MSG_OK);
        return nioDraw(awtImage);
    }

    private Image nioCopyPut() {
        int type = BufferedImage.TYPE_INT_ARGB;
        var format = pixelBuffer.getPixelFormat();
        log(type, format, MSG_ALPHA);
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
        Scene scene = new Scene(root, 800, 600, BACKGROUND);
        scene.addEventHandler(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        stage.setTitle("Viewer");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

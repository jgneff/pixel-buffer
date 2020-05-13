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
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/**
 * A JavaFX application to test the new {@link PixelBuffer} class on an image
 * with transparency. Run with a command like the following:
 * <pre>{@code
 * $HOME/opt/jdk-14.0.1/bin/java \
 *     --add-modules=javafx.graphics \
 *     --module-path=$HOME/lib/javafx-sdk-15/lib \
 *     -cp dist/pixel-buffer.jar Tester
 * }</pre>
 *
 * @see
 * <a href="https://github.com/javafxports/openjdk-jfx/pull/472">
 * javafxports/openjdk-jfx#472</a> JDK-8167148: Add native rendering support by
 * supporting WritableImages backed by NIO ByteBuffers
 * @author John Neffenger
 */
public class Tester extends Application {

    private static final String TITLE = "Tester";
    private static final String IMAGE = "PNG_transparency_demonstration_1.png";

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
    private final StackPane root;

    private final WritableImage jfxImage;

    private final int[] rgbArray;

    private final ByteBuffer byteBuffer;
    private final PixelBuffer<ByteBuffer> bytePixelBuffer;
    private final WritableImage nioByteImage;

    private final IntBuffer intBuffer;
    private final PixelBuffer<IntBuffer> intPixelBuffer;
    private final WritableImage nioIntImage;

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
        pngImage = loadImage(IMAGE);
        width = pngImage.getWidth();
        height = pngImage.getHeight();
        view = new ImageView();
        root = new StackPane(view);

        jfxImage = new WritableImage(width, height);

        rgbArray = new int[width * height];

        intBuffer = IntBuffer.allocate(width * height);
        intPixelBuffer = new PixelBuffer<>(width, height, intBuffer, PixelFormat.getIntArgbPreInstance());
        nioIntImage = new WritableImage(intPixelBuffer);

        byteBuffer = ByteBuffer.allocateDirect(width * height * Integer.BYTES);
        bytePixelBuffer = new PixelBuffer<>(width, height, byteBuffer, PixelFormat.getByteBgraPreInstance());
        nioByteImage = new WritableImage(bytePixelBuffer);

        methods = Arrays.asList(
                this::drawArgbSetArgb,
                this::drawArgbSetArgbPre,
                this::drawArgbPreSetArgb,
                this::drawArgbPreSetArgbPre,
                this::drawRgbSetArgb,
                this::drawBgrSetArgb,
                this::drawAbgrSetBgra,
                this::drawAbgrPreSetBgraPre,
                this::copyArgbSetArgb,
                this::copyArgbSetArgbPre,
                this::drawArgbPrePutBytes,
                this::copyArgbPutBytes,
                this::drawArgbPrePutInts,
                this::copyArgbPutInts
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

    private Image oldDrawInt(BufferedImage awtImage, PixelFormat<IntBuffer> format) {
        Graphics2D graphics = awtImage.createGraphics();
        graphics.drawImage(pngImage, 0, 0, null);
        graphics.dispose();
        int[] data = ((DataBufferInt) awtImage.getRaster().getDataBuffer()).getData();
        jfxImage.getPixelWriter().setPixels(0, 0, width, height, format, data, 0, width);
        return jfxImage;
    }

    private Image oldDrawByte(BufferedImage awtImage, PixelFormat<ByteBuffer> format) {
        Graphics2D graphics = awtImage.createGraphics();
        graphics.drawImage(pngImage, 0, 0, null);
        graphics.dispose();
        byte[] data = ((DataBufferByte) awtImage.getRaster().getDataBuffer()).getData();
        jfxImage.getPixelWriter().setPixels(0, 0, width, height, format, data, 0, width * Integer.BYTES);
        return jfxImage;
    }

    private Image oldCopyInt(PixelFormat<IntBuffer> format) {
        pngImage.getRGB(0, 0, width, height, rgbArray, 0, width);
        jfxImage.getPixelWriter().setPixels(0, 0, width, height, format, rgbArray, 0, width);
        return jfxImage;
    }

    private Image nioDrawInt(BufferedImage awtImage) {
        Graphics2D graphics = awtImage.createGraphics();
        graphics.drawImage(pngImage, 0, 0, null);
        graphics.dispose();
        int[] data = ((DataBufferInt) awtImage.getRaster().getDataBuffer()).getData();
        intBuffer.put(data);
        intBuffer.clear();
        intPixelBuffer.updateBuffer((b) -> new Rectangle2D(0, 0, width, height));
        return nioIntImage;
    }

    private Image nioDrawByte(BufferedImage awtImage) {
        Graphics2D graphics = awtImage.createGraphics();
        graphics.drawImage(pngImage, 0, 0, null);
        graphics.dispose();
        int[] data = ((DataBufferInt) awtImage.getRaster().getDataBuffer()).getData();
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(data);
        bytePixelBuffer.updateBuffer((b) -> new Rectangle2D(0, 0, width, height));
        return nioByteImage;
    }

    private Image nioCopyInt() {
        pngImage.getRGB(0, 0, width, height, rgbArray, 0, width);
        intBuffer.put(rgbArray);
        intBuffer.clear();
        intPixelBuffer.updateBuffer((b) -> new Rectangle2D(0, 0, width, height));
        return nioIntImage;
    }

    private Image nioCopyByte() {
        pngImage.getRGB(0, 0, width, height, rgbArray, 0, width);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(rgbArray);
        bytePixelBuffer.updateBuffer((b) -> new Rectangle2D(0, 0, width, height));
        return nioByteImage;
    }

    /*
     * 01 - INT_ARGB -> INT_ARGB (correct)
     */
    private Image drawArgbSetArgb() {
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var format = PixelFormat.getIntArgbInstance();
        log(awtImage, format, MSG_OK);
        return oldDrawInt(awtImage, format);
    }

    /*
     * 02 - INT_ARGB -> INT_ARGB_PRE (wrong alpha)
     */
    private Image drawArgbSetArgbPre() {
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var format = PixelFormat.getIntArgbPreInstance();
        log(awtImage, format, MSG_ALPHA);
        return oldDrawInt(awtImage, format);
    }

    /*
     * 03 - INT_ARGB_PRE -> INT_ARGB (wrong alpha)
     */
    private Image drawArgbPreSetArgb() {
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        var format = PixelFormat.getIntArgbInstance();
        log(awtImage, format, MSG_ALPHA);
        return oldDrawInt(awtImage, format);
    }

    /*
     * 04 - INT_ARGB_PRE -> INT_ARGB_PRE (correct)
     */
    private Image drawArgbPreSetArgbPre() {
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        var format = PixelFormat.getIntArgbPreInstance();
        log(awtImage, format, MSG_OK);
        return oldDrawInt(awtImage, format);
    }

    /*
     * 05 - INT_RGB -> INT_ARGB (blank)
     */
    private Image drawRgbSetArgb() {
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var format = PixelFormat.getIntArgbInstance();
        log(awtImage, format, MSG_BLANK);
        return oldDrawInt(awtImage, format);
    }

    /*
     * 06 - INT_BGR -> INT_ARGB (blank)
     */
    private Image drawBgrSetArgb() {
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_BGR);
        var format = PixelFormat.getIntArgbInstance();
        log(awtImage, format, MSG_BLANK);
        return oldDrawInt(awtImage, format);
    }

    /*
     * 07 - 4BYTE_ABGR -> BYTE_BGRA (wrong colors)
     */
    private Image drawAbgrSetBgra() {
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        var format = PixelFormat.getByteBgraInstance();
        log(awtImage, format, MSG_COLORS);
        return oldDrawByte(awtImage, format);
    }

    /*
     * 08 - 4BYTE_ABGR_PRE -> BYTE_BGRA_PRE (wrong colors)
     */
    private Image drawAbgrPreSetBgraPre() {
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR_PRE);
        var format = PixelFormat.getByteBgraPreInstance();
        log(awtImage, format, MSG_COLORS);
        return oldDrawByte(awtImage, format);
    }

    /*
     * 09 - INT_ARGB -> INT_ARGB (correct)
     */
    private Image copyArgbSetArgb() {
        int type = BufferedImage.TYPE_INT_ARGB;
        var format = PixelFormat.getIntArgbInstance();
        log(type, format, MSG_OK);
        return oldCopyInt(format);
    }

    /*
     * 10 - INT_ARGB -> INT_ARGB_PRE (wrong alpha)
     */
    private Image copyArgbSetArgbPre() {
        int type = BufferedImage.TYPE_INT_ARGB;
        var format = PixelFormat.getIntArgbPreInstance();
        log(type, format, MSG_ALPHA);
        return oldCopyInt(format);
    }

    /*
     * 11 - INT_ARGB_PRE -> BYTE_BGRA_PRE (correct)
     */
    private Image drawArgbPrePutBytes() {
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        var format = bytePixelBuffer.getPixelFormat();
        log(awtImage, format, MSG_OK);
        return nioDrawByte(awtImage);
    }

    /*
     * 12 - INT_ARGB -> BYTE_BGRA_PRE (wrong alpha)
     */
    private Image copyArgbPutBytes() {
        int type = BufferedImage.TYPE_INT_ARGB;
        var format = bytePixelBuffer.getPixelFormat();
        log(type, format, MSG_ALPHA);
        return nioCopyByte();
    }

    /*
     * 13 - INT_ARGB_PRE -> INT_ARGB_PRE (correct)
     */
    private Image drawArgbPrePutInts() {
        var awtImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        var format = intPixelBuffer.getPixelFormat();
        log(awtImage, format, MSG_OK);
        return nioDrawInt(awtImage);
    }

    /*
     * 14 - INT_ARGB -> INT_ARGB_PRE (wrong alpha)
     */
    private Image copyArgbPutInts() {
        int type = BufferedImage.TYPE_INT_ARGB;
        var format = intPixelBuffer.getPixelFormat();
        log(type, format, MSG_ALPHA);
        return nioCopyInt();
    }

    private void onKeyPressed(KeyEvent event) {
        KeyCode code = event.getCode();
        if (code == KeyCode.SPACE) {
            event.consume();
            try {
                if (index == 0) {
                    System.out.println();
                }
                view.setImage(methods.get(index).call());
            } catch (Exception ex) {
                System.err.println(ex);
            } finally {
                index = index == methods.size() - 1 ? 0 : index + 1;
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
        stage.setTitle(TITLE);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

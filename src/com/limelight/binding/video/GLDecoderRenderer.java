package com.limelight.binding.video;


import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureData;
import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.DecodeUnit;
import com.limelight.nvstream.av.video.VideoDepacketizer;
import com.limelight.nvstream.av.video.cpu.AvcDecoder;

import javax.media.opengl.*;
import javax.media.opengl.awt.GLCanvas;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;


/**
 * Author: spartango
 * Date: 2/1/14
 * Time: 11:42 PM.
 */
public class GLDecoderRenderer extends SwingCpuDecoderRenderer implements  GLEventListener {
    protected int targetFps;
    protected int width, height;

    protected Graphics      graphics;
    protected JFrame        frame;
    protected float         frameVideoAspectDiff;
    protected BufferedImage image;
    protected int[]         imageBuffer;
    protected IntBuffer     wrappedImageBuffer;

    protected static final int DECODER_BUFFER_SIZE = 92 * 1024;
    protected ByteBuffer decoderBuffer;

    protected long lastRender = System.currentTimeMillis();

    protected final GLProfile      glprofile;
    protected final GLCapabilities glcapabilities;
    protected final GLCanvas       glcanvas;
    private         FPSAnimator    animator;


    private Thread decoderThread;
    private boolean dying = false;

    private Texture texture;
    private TextureData textureData;

    public GLDecoderRenderer() {
        GLProfile.initSingleton();
        glprofile = GLProfile.getDefault();
        glcapabilities = new GLCapabilities(glprofile);
        glcanvas = new GLCanvas(glcapabilities);
    }

    @Override public boolean setup(int width, int height, int redrawRate, Object renderTarget, int drFlags) {
        this.targetFps = redrawRate;
        this.width = width;
        this.height = height;

        // Two threads to ease the work, especially for higher resolutions and frame rates
        int avcFlags = AvcDecoder.FAST_BILINEAR_FILTERING | AvcDecoder.SLICE_THREADING;
        int threadCount = 2;

        frame = (JFrame) renderTarget;
        graphics = frame.getGraphics();

        // Force the renderer to use a buffered image that's friendly with OpenGL
        avcFlags |= AvcDecoder.NATIVE_COLOR_ARGB;
        image = new BufferedImage(width, height,
                                  BufferedImage.TYPE_INT_ARGB);
        imageBuffer = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        wrappedImageBuffer = IntBuffer.wrap(imageBuffer);

        int err = AvcDecoder.init(width, height, avcFlags, threadCount);
        if (err != 0) {
            throw new IllegalStateException("AVC decoder initialization failure: " + err);
        }

        decoderBuffer = ByteBuffer.allocate(DECODER_BUFFER_SIZE + AvcDecoder.getInputPaddingSize());
        System.out.println("Using OpenGL rendering");

        // Add canvas to the frame
        glcanvas.setSize(frame.getWidth(), frame.getHeight());
        frameVideoAspectDiff = Math.abs((frame.getWidth() / (float)frame.getHeight()) - (width / (float)height)) / 2.0f;
        glcanvas.addGLEventListener(this);

        for (MouseListener m : frame.getMouseListeners()) {
            glcanvas.addMouseListener(m);
        }

        for (KeyListener k : frame.getKeyListeners()) {
            glcanvas.addKeyListener(k);
        }

        for (MouseMotionListener m : frame.getMouseMotionListeners()) {
            glcanvas.addMouseMotionListener(m);
        }

        frame.setLayout(null);
        frame.add(glcanvas, 0, 0);

        animator = new FPSAnimator(glcanvas, targetFps);
        return true;
    }

    @Override public boolean start(final VideoDepacketizer depacketizer) {
        animator.start();
        decoderThread = new Thread() {
            @Override
            public void run() {
                DecodeUnit du;
                while (!dying) {
                    try {
                        du = depacketizer.takeNextDecodeUnit();
                    } catch (InterruptedException e1) {
                        return;
                    }

                    if (du != null) {
                        submitDecodeUnit(du);
                        depacketizer.freeDecodeUnit(du);
                    }

                }
            }
        };
        decoderThread.setPriority(Thread.MAX_PRIORITY - 1);
        decoderThread.setName("Video - Decoder (CPU)");
        decoderThread.start();

        return true;
    }


    @Override
    public void reshape(GLAutoDrawable glautodrawable, int x, int y, int width, int height) {
    }

    @Override
    public void init(GLAutoDrawable glautodrawable) {
        GL2 gl = glautodrawable.getGL().getGL2();
        texture = new Texture(0);
        texture.bind(gl);
        textureData = new TextureData(glprofile,
                                        4,
                                        width,
                                        height,
                                        0,
                                        gl.GL_BGRA,
                                        gl.GL_UNSIGNED_INT_8_8_8_8_REV,
                                        false,
                                        false,
                                        true,
                                        wrappedImageBuffer,
                                        null);
    }

    @Override
    public void dispose(GLAutoDrawable glautodrawable) {
        if (texture != null) {
            textureData = null;
            texture.destroy(glautodrawable.getGL().getGL2());
            texture = null;
        }
    }

    @Override
    public void display(GLAutoDrawable glautodrawable) {
        // Decode the image
        AvcDecoder.getRgbFrameInt(imageBuffer, imageBuffer.length);

        GL2 gl = glautodrawable.getGL().getGL2();
        gl.glClearColor(0f, 0f, 0f, 1f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);

        gl.glEnable(gl.GL_TEXTURE_2D);
        wrappedImageBuffer.rewind();
        // OpenGL only supports BGRA and RGBA, rather than ARGB or ABGR (from the buffer)
        // So we instruct it to read the packed RGB values in the appropriate (REV) order

        texture.enable(gl);
        texture.updateImage(gl, textureData);

        gl.glBegin(gl.GL_QUADS);
        // This flips the texture as it draws it, as the opengl coordinate system is different
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(-1.0f, 1.0f - frameVideoAspectDiff, 1.0f); // Bottom Left Of The Texture and Quad

        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(1.0f, 1.0f - frameVideoAspectDiff, 1.0f); // Bottom Right Of The Texture and Quad

        gl.glTexCoord2f(1.0f, 1.0f);
        gl.glVertex3f(1.0f, -1.0f + frameVideoAspectDiff, 1.0f); // Top Right Of The Texture and Quad

        gl.glTexCoord2f(0.0f, 1.0f);
        gl.glVertex3f(-1.0f, -1.0f + frameVideoAspectDiff, 1.0f);

        gl.glEnd();
        texture.disable(gl);
        lastRender = System.currentTimeMillis();
    }

    /**
     * Releases resources held by the decoder.
     */
    @Override public void release() {
        stop();
        AvcDecoder.destroy();
    }

    /**
     * Give a unit to be decoded to the decoder.
     *
     * @param decodeUnit the unit to be decoded
     * @return true if the unit was decoded successfully, false otherwise
     */
    @Override public boolean submitDecodeUnit(DecodeUnit decodeUnit) {
        byte[] data;

        // Use the reserved decoder buffer if this decode unit will fit
        if (decodeUnit.getDataLength() <= DECODER_BUFFER_SIZE) {
            decoderBuffer.clear();

            for (ByteBufferDescriptor bbd : decodeUnit.getBufferList()) {
                decoderBuffer.put(bbd.data, bbd.offset, bbd.length);
            }

            data = decoderBuffer.array();
        } else {
            data = new byte[decodeUnit.getDataLength() + AvcDecoder.getInputPaddingSize()];

            int offset = 0;
            for (ByteBufferDescriptor bbd : decodeUnit.getBufferList()) {
                System.arraycopy(bbd.data, bbd.offset, data, offset, bbd.length);
                offset += bbd.length;
            }
        }

        return (AvcDecoder.decode(data, 0, decodeUnit.getDataLength()) == 0);
    }

    /**
     * Stops the decoding and rendering of the video stream.
     */
    @Override public void stop() {
        animator.stop();
        dying = true;
        decoderThread.interrupt();
    }
}


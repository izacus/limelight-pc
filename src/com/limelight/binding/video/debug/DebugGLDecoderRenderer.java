package com.limelight.binding.video.debug;

import com.jogamp.opengl.util.awt.TextRenderer;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureData;
import com.limelight.binding.video.GLDecoderRenderer;
import com.limelight.nvstream.av.video.cpu.AvcDecoder;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import java.awt.*;
import java.nio.IntBuffer;

/**
 * Author: spartango
 * Date: 2/2/14
 * Time: 12:13 PM.
 */
public class DebugGLDecoderRenderer extends GLDecoderRenderer {
    private TextRenderer renderer;

    @Override
    public void init(GLAutoDrawable glautodrawable) {
        Font font = new Font("Monospaced", Font.BOLD, 12);
        renderer = new TextRenderer(font, true, false);
    }

    @Override
    public void display(GLAutoDrawable glautodrawable) {

        long decodeStart = System.currentTimeMillis();

        // Decode the image
        boolean decoded = AvcDecoder.getRgbFrameInt(imageBuffer, imageBuffer.length);

        long decodeTime = System.currentTimeMillis() - decodeStart;
        long renderStart = System.currentTimeMillis();

        GL2 gl = glautodrawable.getGL().getGL2();

        IntBuffer bufferRGB = IntBuffer.wrap(imageBuffer);
        gl.glEnable(gl.GL_TEXTURE_2D);
        // OpenGL only supports BGRA and RGBA, rather than ARGB or ABGR (from the buffer)
        // So we instruct it to read the packed RGB values in the appropriate (REV) order
        Texture texture = new Texture(gl,
                                      new TextureData(glprofile,
                                                      4,
                                                      width,
                                                      height,
                                                      0,
                                                      gl.GL_BGRA,
                                                      gl.GL_UNSIGNED_INT_8_8_8_8_REV,
                                                      false,
                                                      false,
                                                      true,
                                                      bufferRGB,
                                                      null));
        texture.enable(gl);
        texture.bind(gl);

        gl.glBegin(gl.GL_QUADS);
        // This flips the texture as it draws it, as the opengl coordinate system is different
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(-1.0f, 1.0f, 1.0f); // Bottom Left Of The Texture and Quad

        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(1.0f, 1.0f, 1.0f); // Bottom Right Of The Texture and Quad

        gl.glTexCoord2f(1.0f, 1.0f);
        gl.glVertex3f(1.0f, -1.0f, 1.0f); // Top Right Of The Texture and Quad

        gl.glTexCoord2f(0.0f, 1.0f);
        gl.glVertex3f(-1.0f, -1.0f, 1.0f);

        gl.glEnd();
        texture.disable(gl);
        texture.destroy(gl);

        long renderTime = System.currentTimeMillis() - renderStart;
        long refreshTime = System.currentTimeMillis() - lastRender;

        String info = refreshTime
                      + "("
                      + decodeTime
                      + "+"
                      + renderTime
                      + ")ms "
                      + Math.round(1000.0
                         / refreshTime)
                      + "FPS";

        renderer.beginRendering(glautodrawable.getWidth(), glautodrawable.getHeight());
        renderer.setColor(0.0f, 1.0f, 0.0f, 1.0f);
        renderer.draw(info, 0, glautodrawable.getHeight() - 12);
        renderer.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        renderer.endRendering();

        lastRender = System.currentTimeMillis();
    }

}

package com.vorono4ka.editor.renderer.listeners;

import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLCanvas;
import com.vorono4ka.editor.Main;
import com.vorono4ka.editor.renderer.Stage;
import com.vorono4ka.editor.renderer.texture.GLImage;
import com.vorono4ka.editor.renderer.texture.khronos.ExtensionKhronosTextureLoader;
import com.vorono4ka.editor.renderer.texture.khronos.KhronosTextureLoaders;

public class EventListener implements GLEventListener {
    private ExtensionKhronosTextureLoader extensionKhronosTextureLoader;

    @Override
    public void init(GLAutoDrawable glAutoDrawable) {
        GL3 gl = glAutoDrawable.getGL().getGL3();

        GLCanvas canvas = Main.editor.getWindow().getCanvas();

//        extensionKhronosTextureLoader = new ExtensionKhronosTextureLoader(gl);
//        KhronosTextureLoaders.registerLoader(extensionKhronosTextureLoader);

        GLImage.khronosTextureLoader = KhronosTextureLoaders.getLoader();

        Stage.getInstance().init(gl, 0, 0, canvas.getWidth(), canvas.getHeight());
    }

    @Override
    public void dispose(GLAutoDrawable glAutoDrawable) {
        glAutoDrawable.getAnimator().stop();

        KhronosTextureLoaders.unregisterLoader(extensionKhronosTextureLoader);
    }

    @Override
    public void display(GLAutoDrawable glAutoDrawable) {
        Stage.getInstance().update();
    }

    @Override
    public void reshape(GLAutoDrawable glAutoDrawable, int x, int y, int width, int height) {
        GL3 gl = glAutoDrawable.getGL().getGL3();

        gl.glViewport(x, y, width, height);

        Stage.getInstance().unbindRender();
        Stage.getInstance().init(gl, 0, 0, width, height);
    }
}

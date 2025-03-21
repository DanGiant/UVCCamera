package com.serenegiant.widget;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class YUYVRenderer implements GLSurfaceView.Renderer {
    private final static String TAG = YUYVRenderer.class.getCanonicalName();
    private int mProgram;
    private int m_yTextureId;
    private int m_uvTextureId;
    private FloatBuffer mVertexBuffer;
    private FloatBuffer mTexCoordBuffer;

    private int mYUYVWidth;
    private int mYUYVHeight;
    private ByteBuffer mYUYVBuffer;

    private final String vertexShaderCode =
            "attribute vec4 aPosition;\n" +
            "attribute vec2 aTexCoord;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "  gl_Position = aPosition;\n" +
            "  vTexCoord = aTexCoord;\n" +
            "}\n";

    private final String fragmentShaderCode =
            "precision mediump float;\n" +
            "varying vec2 vTexCoord;\n" +
            "uniform sampler2D y_texture;\n" +
            "uniform sampler2D uv_texture;\n" +
            "void main() {\n" +
            "   vec3 yuv;\n" +
            "   yuv.x = texture2D(y_texture, vTexCoord).r - 0.063;\n" +
            "   vec4 yuyv = texture2D(uv_texture, vTexCoord);\n" +
            "   yuv.y = yuyv.g - 0.502;\n" +
            "   yuv.z = yuyv.a - 0.502;\n" +
            "   vec3 rgb = mat3(1.164,  1.164, 1.164,\n" +
            "                     0.0, -0.392, 2.017,\n" +
            "                   1.596, -0.813,   0.0) * yuv;\n" +
            "   gl_FragColor = vec4(rgb, 1.0);\n" +
            "}\n";

    public YUYVRenderer(int width, int height) {

        mYUYVWidth = width;
        mYUYVHeight = height;
        mYUYVBuffer = ByteBuffer.allocateDirect(width * height * 2);
        mYUYVBuffer.order(ByteOrder.nativeOrder());

        float[] vertices = {
                -1.0f, -1.0f,
                1.0f, -1.0f,
                -1.0f, 1.0f,
                1.0f, 1.0f
        };

        float[] texCoords = {
                0.0f, 1.0f,
                1.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f
        };

        mVertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mVertexBuffer.put(vertices);
        mVertexBuffer.position(0);

        mTexCoordBuffer = ByteBuffer.allocateDirect(texCoords.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mTexCoordBuffer.put(texCoords);
        mTexCoordBuffer.position(0);
    }

    public void updateYUYVData(byte[] data) {
        if (data != null && data.length == mYUYVWidth * mYUYVHeight * 2) {
            mYUYVBuffer.clear();
            mYUYVBuffer.put(data);
            mYUYVBuffer.position(0);
        }
    }

    private void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + error);
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated");

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        mProgram = GLES20.glCreateProgram();

        GLES20.glAttachShader(mProgram, vertexShader);
        checkGlError("glAttachShader vertexShader");

        GLES20.glAttachShader(mProgram, fragmentShader);
        checkGlError("glAttachShader fragmentShader");

        GLES20.glLinkProgram(mProgram);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(mProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: ");
            Log.e(TAG, GLES20.glGetProgramInfoLog(mProgram));
            GLES20.glDeleteProgram(mProgram);
            mProgram = 0;
            return;
        }

        int[] yTextures = new int[1];
        GLES20.glGenTextures(1, yTextures, 0);

        m_yTextureId = yTextures[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, m_yTextureId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        int[] uvTextures = new int[1];
        GLES20.glGenTextures(1, uvTextures, 0);
        m_uvTextureId = uvTextures[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, m_uvTextureId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(mProgram);
        checkGlError("glUseProgram");

        int positionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        checkGlError("glGetAttribLocation aPosition");

        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, mVertexBuffer);

        int texCoordHandle = GLES20.glGetAttribLocation(mProgram, "aTexCoord");
        checkGlError("glGetAttribLocation aTexCoord");

        GLES20.glEnableVertexAttribArray(texCoordHandle);
        checkGlError("glEnableVertexAttribArray texCoordHandle");

        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, mTexCoordBuffer);


        //upload YUYV data
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, m_yTextureId);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE_ALPHA, mYUYVWidth, mYUYVHeight, 0,
                GLES20.GL_LUMINANCE_ALPHA, GLES20.GL_UNSIGNED_BYTE, mYUYVBuffer);
        int yTextureHandle = GLES20.glGetUniformLocation(mProgram, "y_texture");
        GLES20.glUniform1i(yTextureHandle, 0);

        //update YUYV data
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, m_uvTextureId);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mYUYVWidth >> 1, mYUYVHeight, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mYUYVBuffer);
        int uvTextureHandle = GLES20.glGetUniformLocation(mProgram, "uv_texture");
        GLES20.glUniform1i(uvTextureHandle, 1);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }
}
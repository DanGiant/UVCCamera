package com.serenegiant.widget;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
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
    private FloatBuffer mTexCoordBufferFlipHorz;
    private FloatBuffer mTexCoordBufferFlipVert;

    private int mYUYVWidth;
    private int mYUYVHeight;
    private ByteBuffer mYUYVBuffer;

    private int mViewWidth;
    private int mViewHeight;

    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private final float[] mMvpMatrix = new float[16];
    private Rotation mRotation = Rotation.Rotate_0;
    private boolean mFlipHorizontal = false;
    private boolean mFlipVertical = false;
    private int mvpMatrixHandle;
    private final String vertexShaderCode =
            "uniform mat4 uMVPMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec2 aTexCoord;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "  gl_Position = uMVPMatrix * aPosition;\n" +
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

    public enum Rotation {
        Rotate_0,
        Rotate_90,
        Rotate_180,
        Rotate_270
    };

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

        // 水平镜像翻转的纹理坐标
        float[] texCoordsFlipHorz = {
                1.0f, 1.0f,  // 左下角
                0.0f, 1.0f,  // 右下角
                1.0f, 0.0f,  // 左上角
                0.0f, 0.0f   // 右上角
        };

        // 垂直镜像翻转的纹理坐标
        float[] texCoordsFlipVert = {
                0.0f, 0.0f,  // 左下角
                1.0f, 0.0f,  // 右下角
                0.0f, 1.0f,  // 左上角
                1.0f, 1.0f   // 右上角
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

        mTexCoordBufferFlipHorz = ByteBuffer.allocateDirect(texCoordsFlipHorz.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mTexCoordBufferFlipHorz.put(texCoordsFlipHorz);
        mTexCoordBufferFlipHorz.position(0);

        mTexCoordBufferFlipVert = ByteBuffer.allocateDirect(texCoordsFlipVert.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mTexCoordBufferFlipVert.put(texCoordsFlipVert);
        mTexCoordBufferFlipVert.position(0);
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

        // 获取着色器程序中的变量句柄
        mvpMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");

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
        mViewWidth = width;
        mViewHeight = height;
        GLES20.glViewport(0, 0, mViewWidth, mViewHeight);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // 清除屏幕
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // 使用着色器程序
        GLES20.glUseProgram(mProgram);
        checkGlError("glUseProgram");

        int rotateDegree = 0;
        if (mRotation == Rotation.Rotate_90)
            rotateDegree = 90;
        else if (mRotation == Rotation.Rotate_180)
            rotateDegree = 180;
        else if (mRotation == Rotation.Rotate_270)
            rotateDegree = 270;

        // 计算投影矩阵
        float viewAspectRatio = (float) mViewWidth / mViewHeight;
        float yuyvAspectRatio = 0.0f;
        if (rotateDegree == 0 || rotateDegree == 180) {
            yuyvAspectRatio = (float) mYUYVWidth / mYUYVHeight;
        } else {
            yuyvAspectRatio = (float) mYUYVHeight / mYUYVWidth;
        }
        if (viewAspectRatio > yuyvAspectRatio) {
            // 视图比图像宽，上下留黑边
            Matrix.orthoM(mProjectionMatrix, 0,
                    -viewAspectRatio / yuyvAspectRatio,
                    viewAspectRatio / yuyvAspectRatio,
                    -1, 1,
                    -1, 1);
        } else {
            // 视图比图像高，左右留黑边
            Matrix.orthoM(mProjectionMatrix, 0,
                    -1, 1,
                    -yuyvAspectRatio / viewAspectRatio,
                    yuyvAspectRatio / viewAspectRatio,
                    -1, 1);
        }

        // 设置视图矩阵
        Matrix.setIdentityM(mViewMatrix, 0);
        Matrix.multiplyMM(mMvpMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);

        if (rotateDegree > 0) {
            // 初始化旋转矩阵
            float[] rotationMatrix = new float[16];
            Matrix.setIdentityM(rotationMatrix, 0);
            Matrix.rotateM(rotationMatrix, 0, rotateDegree, 0, 0, 1); // 绕 Z 轴旋转 90 度

            // 应用旋转矩阵
            float[] finalMatrix = new float[16];
            Matrix.multiplyMM(finalMatrix, 0, mMvpMatrix, 0, rotationMatrix, 0);

            // 传递 MVP 矩阵
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, finalMatrix, 0);
        } else {
            // 传递 MVP 矩阵
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMvpMatrix, 0);
        }


        int positionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        checkGlError("glGetAttribLocation aPosition");

        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, mVertexBuffer);

        int texCoordHandle = GLES20.glGetAttribLocation(mProgram, "aTexCoord");
        checkGlError("glGetAttribLocation aTexCoord");

        GLES20.glEnableVertexAttribArray(texCoordHandle);
        checkGlError("glEnableVertexAttribArray texCoordHandle");

        if (mFlipHorizontal) {
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, mTexCoordBufferFlipHorz);
        } else if (mFlipVertical) {
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, mTexCoordBufferFlipVert);
        }
        else {
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, mTexCoordBuffer);
        }



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

    public void setRotation(Rotation rotation) {
        mRotation = rotation;
        Log.d(TAG, "Rotation to " + rotationString(mRotation));
    }

    public Rotation getRotation() {
        return mRotation;
    }

    private String rotationString(Rotation rotation) {
        if (rotation == Rotation.Rotate_0)
            return "Rotate_0";
        else if (rotation == Rotation.Rotate_90)
            return "Rotate_90";
        else if (rotation == Rotation.Rotate_180)
            return "Rotate_180";
        else
            return "Rotate_270";
    }

    public void setFlipHorizontal(boolean flipHorizontal) {
        mFlipVertical = false;
        mFlipHorizontal = flipHorizontal;
    }

    public boolean getFlipHorizontal() {
        return mFlipHorizontal;
    }

    public void setFlipVertical(boolean flipVertical) {
        mFlipHorizontal = false;
        mFlipVertical = flipVertical;
    }

    public boolean getFlipVertical() {
        return mFlipVertical;
    }
}
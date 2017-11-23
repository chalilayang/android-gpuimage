/*
 * Copyright (C) 2012 CyberAgent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.co.cyberagent.android.gpuimage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import jp.co.cyberagent.android.gpuimage.util.TextureRotationUtil;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.util.Log;

import static jp.co.cyberagent.android.gpuimage.OpenGlUtils.NO_TEXTURE;

public class GPUImageTwoInputFilter extends GPUImageFilter {
    private static final String TAG = GPUImageTwoInputFilter.class.getSimpleName();
    private static final String VERTEX_SHADER = "attribute vec4 position;\n" +
            "attribute vec4 inputTextureCoordinate;\n" +
            "attribute vec4 inputTextureCoordinate2;\n" +
            " \n" +
            "varying vec2 textureCoordinate;\n" +
            "varying vec2 textureCoordinate2;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position = position;\n" +
            "    textureCoordinate = inputTextureCoordinate.xy;\n" +
            "    textureCoordinate2 = inputTextureCoordinate2.xy;\n" +
            "}";

    public int mFilterSecondTextureCoordinateAttribute;
    public int mFilterInputTextureUniform2;
    public int mFilterSourceTexture2 = NO_TEXTURE;
    private ByteBuffer mTexture2CoordinatesBuffer;
    private Bitmap mBitmap;

    public GPUImageTwoInputFilter(String fragmentShader) {
        this(VERTEX_SHADER, fragmentShader);
    }

    public GPUImageTwoInputFilter(String vertexShader, String fragmentShader) {
        super(vertexShader, fragmentShader);
        setRotation(Rotation.ROTATION_90, false, false);
    }

    @Override
    public void onInit() {
        super.onInit();

        mFilterSecondTextureCoordinateAttribute = GLES20.glGetAttribLocation(getProgram(), "inputTextureCoordinate2");
        mFilterInputTextureUniform2 = GLES20.glGetUniformLocation(getProgram(), "inputImageTexture2"); // This does assume a name of "inputImageTexture2" for second input texture in the fragment shader
        GLES20.glEnableVertexAttribArray(mFilterSecondTextureCoordinateAttribute);

        if (mBitmap != null&&!mBitmap.isRecycled()) {
            setBitmap(mBitmap);
        }
    }
    
    public void setBitmap(final Bitmap bitmap) {
        if (bitmap != null && bitmap.isRecycled()) {
            return;
        }
        mBitmap = bitmap;
        if (mBitmap == null) {
            return;
        }
//        runOnDraw(new Runnable() {
//            public void run() {
//                if (mFilterSourceTexture2 == OpenGlUtils.NO_TEXTURE) {
//                    if (bitmap == null || bitmap.isRecycled()) {
//                        return;
//                    }
//                    GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
//                    mFilterSourceTexture2 = OpenGlUtils.loadTexture(bitmap, OpenGlUtils.NO_TEXTURE, false);
//                }
//            }
//        });
    }

    private int copyTextId = NO_TEXTURE;
    public void updateTexture() {
        if (filterCallback == null) {
            return;
        }
        final int[] sizes = filterCallback.getImageSize();
        if (sizes[0] == 0 || sizes[1] == 0) {
            return;
        }
        if (mBitmap == null) {
            return;
        }
        if (textID == NO_TEXTURE) {
            return;
        }
        IntBuffer mRGBuffer = filterCallback.getRGBBuffer();
        if (mRGBuffer == null) {
            return;
        }
        if (mFilterSourceTexture2 == NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        }
        if (copyTextId == NO_TEXTURE) {
            int textures[] = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            copyTextId = textures[0];
        }
//        OpenGlUtils.copyTexture(textID, copyTextId, sizes[0], sizes[1]);
//        copyTextId = OpenGlUtils.loadTexture(mBitmap, copyTextId, false);
        copyTextId = OpenGlUtils.loadTexture(mRGBuffer, sizes[0], sizes[1], NO_TEXTURE);
        mFilterSourceTexture2 = copyTextId;
        Log.i(TAG, "run: mFilterSourceTexture2 " + mFilterSourceTexture2);
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public void recycleBitmap() {
        if (mBitmap != null && !mBitmap.isRecycled()) {
            mBitmap.recycle();
            mBitmap = null;
        }
    }

    public void onDestroy() {
        super.onDestroy();
        GLES20.glDeleteTextures(1, new int[]{
                mFilterSourceTexture2
        }, 0);
        mFilterSourceTexture2 = NO_TEXTURE;
    }

    private ByteBuffer tempTextureBuffer;
    private float scaleFactor = 1.0f;
    @Override
    protected void onDrawArraysPre() {
        GLES20.glEnableVertexAttribArray(mFilterSecondTextureCoordinateAttribute);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFilterSourceTexture2);
        GLES20.glUniform1i(mFilterInputTextureUniform2, 3);

        scaleFactor = scaleFactor - 0.04f;
        if (scaleFactor < 0.1f) {
            scaleFactor = 1.0f;
            updateTexture();
        }
        mTexture2CoordinatesBuffer.position(0);
        float[] floats = new float[8];
        mTexture2CoordinatesBuffer.asFloatBuffer().get(floats);
        float[] newFloats = TextureRotationUtil.scale(scaleFactor, floats);
        if (tempTextureBuffer == null) {
            tempTextureBuffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
        }
        FloatBuffer fBuffer = tempTextureBuffer.asFloatBuffer();
        tempTextureBuffer.position(0);
        fBuffer.put(newFloats);
        fBuffer.flip();
        GLES20.glVertexAttribPointer(mFilterSecondTextureCoordinateAttribute,
                2, GLES20.GL_FLOAT, false, 0, tempTextureBuffer);
    }

    public void setRotation(
            final Rotation rotation, final boolean flipHorizontal, final boolean flipVertical) {
        float[] buffer = TextureRotationUtil.getRotation(rotation, flipHorizontal, flipVertical);

        ByteBuffer bBuffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
        FloatBuffer fBuffer = bBuffer.asFloatBuffer();
        fBuffer.put(buffer);
        fBuffer.flip();

        mTexture2CoordinatesBuffer = bBuffer;
    }
}

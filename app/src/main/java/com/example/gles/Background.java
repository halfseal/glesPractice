package com.example.gles;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class Background {
  int buffer;
  int program;

  int texID;

  private final String vscode = "" +
          "attribute vec3 vPosition;" +
          "attribute vec2 vTexcoord;" +
          "" +
          "varying vec2 tc;" +
          "" +
          "void main() {" +
          "  gl_Position = vec4(vPosition, 1.0);" +
          "  tc = vec2(vTexcoord);" +
          "}";

  private final String fscode = "" +
          "precision mediump float;" +
          "" +
          "uniform sampler2D tex;" +
          "" +
          "varying vec2 tc;" +
          "" +
          "void main() {" +
          "  gl_FragColor = texture2D(tex, tc);" +
          "}";

  public Background(Context context) throws IOException {
    float[] vertex = {
            1.0f, 1.0f, 0.0f, /*x, y, z*/ 1.0f, 1.0f,
            -1.0f, 1.0f, 0.0f, 0.0f, 1.0f,
            -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,

            -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
            1.0f, -1.0f, 0.0f, 1.0f, 0.0f,
            1.0f, 1.0f, 0.0f, 1.0f, 1.0f
    };
    ByteBuffer bb = ByteBuffer.allocateDirect(vertex.length * 4);
    bb.order(ByteOrder.nativeOrder());
    FloatBuffer vb = bb.asFloatBuffer();
    vb.put(vertex);
    vb.position(0);

    int[] buffers = new int[1];
    GLES20.glGenBuffers(1, buffers, 0);
    buffer = buffers[0];

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer);
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertex.length * 4, vb, GLES20.GL_DYNAMIC_DRAW);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

    int vs = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
    GLES20.glShaderSource(vs, vscode);
    GLES20.glCompileShader(vs);
    int fs = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
    GLES20.glShaderSource(fs, fscode);
    GLES20.glCompileShader(fs);

    program = GLES20.glCreateProgram();
    GLES20.glAttachShader(program, vs);
    GLES20.glAttachShader(program, fs);
    GLES20.glLinkProgram(program);


    int[] textures = new int[1];
    GLES20.glGenTextures(1, textures, 0);
    texID = textures[0];

    Bitmap texBitmap = BitmapFactory.decodeStream(context.getAssets().open("frame1.png"));
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texID);
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, texBitmap, 0);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
  }

  float time = 0.0f;

  public void draw(float dt) {
    time += dt;

    GLES20.glUseProgram(program);

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer);
    int vpos = GLES20.glGetAttribLocation(program, "vPosition");
    GLES20.glEnableVertexAttribArray(vpos);
    GLES20.glVertexAttribPointer(vpos, 3, GLES20.GL_FLOAT, false, 5 * 4, 0);

    int tpos = GLES20.glGetAttribLocation(program, "vTexcoord");
    GLES20.glEnableVertexAttribArray(tpos);
    GLES20.glVertexAttribPointer(tpos, 2, GLES20.GL_FLOAT, false, 5 * 4, 3 * 4);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);



    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texID);
    int pos = GLES20.glGetUniformLocation(program, "tex");
    GLES20.glUniform1i(pos, GLES20.GL_TEXTURE0);

    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

    GLES20.glDisableVertexAttribArray(vpos);
    GLES20.glDisableVertexAttribArray(tpos);
  }
}

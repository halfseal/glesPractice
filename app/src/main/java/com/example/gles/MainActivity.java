package com.example.gles;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.appcompat.app.AppCompatActivity;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
  GLSurfaceView glView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    glView = (GLSurfaceView) findViewById(R.id.surfaceView);
    glView.setEGLContextClientVersion(2);
    glView.setPreserveEGLContextOnPause(true);
    glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
    glView.setRenderer(this);
  }

  Triangle triangle;

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1.0f);
    triangle = new Triangle();
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    GLES20.glViewport(0, 0, width, height);
    triangle.changeSize(width, height);
  }

  long lastTime = SystemClock.elapsedRealtime();

  @Override
  public void onDrawFrame(GL10 gl) {
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    long currentTime = SystemClock.elapsedRealtime();
    float dt = (float) (currentTime - lastTime) / 1000.0f;
    lastTime = currentTime;

    triangle.draw(dt);
  }
}
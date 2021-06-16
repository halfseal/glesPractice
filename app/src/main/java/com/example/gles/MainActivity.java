package com.example.gles;

import android.annotation.SuppressLint;
import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
  GLSurfaceView glView;

  boolean installRequested = false;
  Session session;

  Background background;
  Cube cube;
  PointCloudRenderer pointCloudRenderer;

  Button recordButton;

  boolean isCollecting = false;
  ArrayList<FloatBuffer> pointList = new ArrayList<>();

  @SuppressLint("ClickableViewAccessibility")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    glView = (GLSurfaceView) findViewById(R.id.surfaceView);
    glView.setEGLContextClientVersion(2);
    glView.setPreserveEGLContextOnPause(true);
    glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
    glView.setRenderer(this);

    recordButton = (Button) findViewById(R.id.recordButton);
    recordButton.setOnClickListener(l -> {
      if (isCollecting) {
        isCollecting = false;
        glView.queueEvent(() -> {
          pointCloudRenderer.fix(pointList);
        });
      } else {
        isCollecting = true;
        pointList = new ArrayList<>();
      }
    });
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (session == null) {
      String message = null;
      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }

        // Create the session.
        session = new Session(/* context= */ this);

      } catch (UnavailableArcoreNotInstalledException
              | UnavailableUserDeclinedInstallationException e) {
        message = "Please install ARCore";
      } catch (UnavailableApkTooOldException e) {
        message = "Please update ARCore";
      } catch (UnavailableSdkTooOldException e) {
        message = "Please update this app";
      } catch (UnavailableDeviceNotCompatibleException e) {
        message = "This device does not support AR";
      } catch (Exception e) {
        message = "Failed to create AR session";
      }

      if (message != null) {
        Toast.makeText(this, "TODO: handle exception " + message, Toast.LENGTH_LONG).show();
        return;
      }
    }

    try {
      session.resume();
    } catch (CameraNotAvailableException e) {
      Toast.makeText(this, "Camera not available. Try restarting the app.", Toast.LENGTH_LONG).show();
      session = null;
      return;
    }

    glView.onResume();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
              .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1.0f);
    cube = new Cube();
    try {
      background = new Background(this);
      pointCloudRenderer = new PointCloudRenderer();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  int width = 1, height = 1;

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    this.width = width;
    this.height = height;

    GLES20.glViewport(0, 0, width, height);
    cube.changeSize(width, height);
  }

  long lastTime = SystemClock.elapsedRealtime();

  @Override
  public void onDrawFrame(GL10 gl) {
    if (session == null) return;

    try {
      session.setCameraTextureName(background.texID);
      session.setDisplayGeometry(((WindowManager) this.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation(), width, height);

      Frame frame = session.update();

      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

      long currentTime = SystemClock.elapsedRealtime();
      float dt = (float) (currentTime - lastTime) / 1000.0f;
      lastTime = currentTime;

      GLES20.glDisable(GLES20.GL_DEPTH_TEST);
      background.draw(dt);
      GLES20.glEnable(GLES20.GL_DEPTH_TEST);

      Camera camera = frame.getCamera();
      float[] projMX = new float[16];
      camera.getProjectionMatrix(projMX, 0, 0.1f, 100.0f);
      float[] viewMX = new float[16];
      camera.getViewMatrix(viewMX, 0);

      if (isCollecting) {
        pointList.add(frame.acquirePointCloud().getPoints());
        pointCloudRenderer.update(frame.acquirePointCloud());
      } else {
        cube.draw(dt, viewMX, projMX);
      }
      pointCloudRenderer.draw(viewMX, projMX);

    } catch (CameraNotAvailableException e) {
      e.printStackTrace();
    }
  }

}
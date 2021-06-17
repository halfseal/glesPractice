package com.example.gles;

import android.annotation.SuppressLint;
import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.AsyncTask;
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

import java.nio.FloatBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
  GLSurfaceView glView;

  boolean installRequested = false;
  Session session;
  Camera camera;

  SimpleDraw forDebugging;
  Background background;
  ArrayList<Cube> cubes;

  PointCloudRenderer pointCloudRenderer;

  PointCollector pointCollector;

  Button recordButton;

  boolean isCollecting = false;
  boolean isPointPicked = false;

  FindPlane findPlane = null;

  int seedID = -1;
  float[] seedPointArr = new float[]{0.0f, 0.0f, 0.0f, 1.0f};

  @SuppressLint("ClickableViewAccessibility")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

    setContentView(R.layout.activity_main);
    glView = (GLSurfaceView) findViewById(R.id.surfaceView);
    glView.setEGLContextClientVersion(2);
    glView.setPreserveEGLContextOnPause(true);
    glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
    glView.setRenderer(this);

    recordButton = (Button) findViewById(R.id.recordButton);
    recordButton.setOnClickListener(l -> {
      if (isCollecting) {
        // collecting 끝내기 위해 버튼 누름
        glView.queueEvent(() -> {
          pointCloudRenderer.fix(pointCollector.getPointBuffer());
        });

        isCollecting = false;
      } else {
        // collecting 시작하기 위해 버튼 누름
        isCollecting = true;
        isPointPicked = false;
        findPlane = null;
        cubes.clear();
        pointCollector = new PointCollector();
      }
    });

    glView.setOnTouchListener((view, event) -> {
      if (findPlane != null && findPlane.plane != null) {
        glView.queueEvent(() -> {
          Cube cube = new Cube();
          cube.xyz = new float[]{camera.getPose().tx(), camera.getPose().ty(), camera.getPose().tz()};
          cubes.add(cube);
        });
      } else if (pointCollector.isFiltered) {
        float[] rayInfo = rayPicking(event.getX(), event.getY(), glView.getMeasuredWidth(), glView.getMeasuredHeight(), camera);
        float[] ray_origin = new float[]{rayInfo[0], rayInfo[1], rayInfo[2]};
        float[] ray_dir = new float[]{rayInfo[3], rayInfo[4], rayInfo[5]};
        pickPoint(pointCollector.getPointBuffer(), ray_origin, ray_dir);

        isPointPicked = true;

        findPlane = new FindPlane(pointCollector.getPointBuffer(), seedID, camera);
        if (findPlane.getStatus() == AsyncTask.Status.FINISHED || findPlane.getStatus() == AsyncTask.Status.RUNNING) {
          findPlane.cancel(true);
          findPlane = new FindPlane(pointCollector.getPointBuffer(), seedID, camera);
        }
        findPlane.execute();
        return true;
      }
      return false;
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
    cubes = new ArrayList<>();

    forDebugging = new SimpleDraw();
    pointCollector = new PointCollector();
    pointCloudRenderer = new PointCloudRenderer();
    background = new Background();
  }

  int width = 1, height = 1;

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    this.width = width;
    this.height = height;

    GLES20.glViewport(0, 0, width, height);
  }

  long lastTime = SystemClock.elapsedRealtime();

  @Override
  public void onDrawFrame(GL10 gl) {
    if (session == null) return;

    try {
      session.setCameraTextureName(background.texID);
      session.setDisplayGeometry(((WindowManager) this.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation(), width, height);

      Frame frame = session.update();
      camera = frame.getCamera();

      float[] projMX = new float[16];
      camera.getProjectionMatrix(projMX, 0, 0.1f, 100.0f);
      float[] viewMX = new float[16];
      camera.getViewMatrix(viewMX, 0);

      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

      long currentTime = SystemClock.elapsedRealtime();
      float dt = (float) (currentTime - lastTime) / 1000.0f;
      lastTime = currentTime;

      background.draw(frame);

      if (isCollecting) {
        pointCollector.push(frame.acquirePointCloud());
        pointCloudRenderer.update(frame.acquirePointCloud());
      }
      pointCloudRenderer.draw(viewMX, projMX);

      for (Cube cube : cubes) {
        cube.update(dt, findPlane.plane);
        cube.draw(viewMX, projMX);
      }

      if (isPointPicked)
        forDebugging.draw(seedPointArr, GLES20.GL_POINTS, 4, 1f, 0f, 0f, viewMX, projMX);

      if (findPlane != null && findPlane.plane != null) {
        float[] pointForDrawingPlane = {
                findPlane.plane.ll[0], findPlane.plane.ll[1], findPlane.plane.ll[2],
                findPlane.plane.lr[0], findPlane.plane.lr[1], findPlane.plane.lr[2],
                findPlane.plane.ur[0], findPlane.plane.ur[1], findPlane.plane.ur[2],
                findPlane.plane.ll[0], findPlane.plane.ll[1], findPlane.plane.ll[2],
                findPlane.plane.ur[0], findPlane.plane.ur[1], findPlane.plane.ur[2],
                findPlane.plane.ul[0], findPlane.plane.ul[1], findPlane.plane.ul[2],
        };
        forDebugging.draw(pointForDrawingPlane, GLES20.GL_TRIANGLES, 3, 0.5f, 0.5f, 0f, viewMX, projMX);
      }
    } catch (CameraNotAvailableException e) {
      e.printStackTrace();
    }
  }

  float[] rayPicking(float xPx, float yPx, int screenWidth, int screenHeight, Camera camera) {
    float x = 2.0f * xPx / screenWidth - 1.0f;
    float y = 1.0f - 2.0f * yPx / screenHeight;

    float[] projMX = new float[16];
    camera.getProjectionMatrix(projMX, 0, 0.1f, 100.0f);
    float[] inverseProjMX = new float[16];
    Matrix.invertM(inverseProjMX, 0, projMX, 0);

    float[] viewMX = new float[16];
    camera.getViewMatrix(viewMX, 0);
    float[] inverseViewMX = new float[16];
    Matrix.invertM(inverseViewMX, 0, viewMX, 0);

    float[] ray_clip = new float[]{x, y, -1f, 1f};

    float[] ray_eye = new float[4];
    Matrix.multiplyMV(ray_eye, 0, inverseProjMX, 0, ray_clip, 0);
    ray_eye = new float[]{ray_eye[0], ray_eye[1], -1.0f, 0.0f};

    float[] ray_wor = new float[4];
    Matrix.multiplyMV(ray_wor, 0, inverseViewMX, 0, ray_eye, 0);

    float ray_wor_length = (float) Math.sqrt(ray_wor[0] * ray_wor[0] + ray_wor[1] * ray_wor[1] + ray_wor[2] * ray_wor[2]);

    float[] out = new float[6];

    // 카메라의 world space 좌표
    out[0] = camera.getPose().tx();
    out[1] = camera.getPose().ty();
    out[2] = camera.getPose().tz();

    // ray의 방향벡터
    out[3] = ray_wor[0] / ray_wor_length;
    out[4] = ray_wor[1] / ray_wor_length;
    out[5] = ray_wor[2] / ray_wor_length;

    return out;
  }

  public void pickPoint(FloatBuffer filterPoints, float[] camera, float[] ray) {
    // camera: 카메라의 world space 위치(x,y,z), ray : ray의 방향벡터
    float minDistanceSq = Float.MAX_VALUE;

    filterPoints.rewind();

    for (int i = 0; i < filterPoints.remaining(); i += 4) {
      float[] point = new float[]{filterPoints.get(i), filterPoints.get(i + 1), filterPoints.get(i + 2), filterPoints.get(i + 3)};
      float[] product = new float[]{point[0] - camera[0], point[1] - camera[1], point[2] - camera[2], 1.0f};

      float distanceSq = product[0] * product[0] + product[1] * product[1] + product[2] * product[2];
      float innerProduct = ray[0] * product[0] + ray[1] * product[1] + ray[2] * product[2];
      distanceSq = distanceSq - (innerProduct * innerProduct);

      if (distanceSq < 0.01f && distanceSq < minDistanceSq) {
        seedPointArr[0] = point[0];
        seedPointArr[1] = point[1];
        seedPointArr[2] = point[2];
        seedID = i / 4;
        minDistanceSq = distanceSq;
      }
    }
  }
}
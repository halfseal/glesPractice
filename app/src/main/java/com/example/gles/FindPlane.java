package com.example.gles;

import android.os.AsyncTask;
import android.util.Log;

import com.curvsurf.fsweb.FindSurfaceRequester;
import com.curvsurf.fsweb.RequestForm;
import com.curvsurf.fsweb.ResponseForm;
import com.google.ar.core.Camera;

import java.nio.FloatBuffer;

public class FindPlane extends AsyncTask<Object, ResponseForm.PlaneParam, ResponseForm.PlaneParam> {
  private static final String REQUEST_URL = "https://developers.curvsurf.com/FindSurface/plane"; // Plane searching server address

  private FloatBuffer points;
  private int seedPointID;
  private Camera camera = null;
  private float circleRad = 0.25f;
  private float z_dis = 0;

  public Plane plane = null;

  public FindPlane(FloatBuffer points, int seedPointID, Camera camera) {
    this.points = points;
    this.seedPointID = seedPointID;
    this.camera = camera;
  }

  @Override
  protected ResponseForm.PlaneParam doInBackground(Object[] objects) {
    // Ready Point Cloud
    FloatBuffer points = this.points.duplicate();

    // Ready Request Form
    RequestForm rf = new RequestForm();

    rf.setPointBufferDescription(points.capacity() / 4, 16, 0); //pointcount, pointstride, pointoffset
    rf.setPointDataDescription(0.05f, 0.01f); //accuracy, meanDistance
    rf.setTargetROI(seedPointID, Math.max(z_dis * circleRad, 0.05f));//seedIndex,touchRadius
    rf.setAlgorithmParameter(RequestForm.SearchLevel.NORMAL, RequestForm.SearchLevel.NORMAL);//LatExt, RadExp
    Log.d("PointsBuffer", points.toString());
    FindSurfaceRequester fsr = new FindSurfaceRequester(REQUEST_URL, true);
    // Request Find Surface
    try {
      Log.d("PlaneFinder", "request");
      ResponseForm resp = fsr.request(rf, points);
      if (resp != null && resp.isSuccess()) {
        ResponseForm.PlaneParam param = resp.getParamAsPlane();
        Log.d("PlaneFinder", "request success");
        return param;
      } else {
        Log.d("PlaneFinder", "request fail");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return null;
  }

  @Override
  protected void onPreExecute() {
    super.onPreExecute();
  }

  @Override
  protected void onPostExecute(ResponseForm.PlaneParam o) {
    super.onPostExecute(o);
    if (o == null) {
      Log.d(this.getClass().getName(), "onPostExecute: 평면 추출 실패!!");
      return;
    }
    try {
      plane = new Plane(o.ll, o.lr, o.ur, o.ul, camera.getPose().getZAxis());
    } catch (Exception e) {
      Log.d("Plane", e.getMessage());
    }
  }
}
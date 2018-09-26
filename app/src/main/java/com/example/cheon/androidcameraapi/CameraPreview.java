package com.example.cheon.androidcameraapi;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.SurfaceHolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class CameraPreview extends ViewGroup
        implements SurfaceHolder.Callback {

    private final String TAG = "CameraPreview";

    private int mCameraID;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mHolder;
    private Camera mCamera;
    private Camera.CameraInfo mCameraInfo;
    private int mDisplayOrientation;
    private List<Camera.Size> mSupportedPreviewSizes;
    private Camera.Size mPreviewSize;
    private boolean isPreview = false;

    private long pretime;
    private byte[] processBuffer;
    private int[] pixels;
    private int frameWidth, frameHeight, frameFormat;
    private int pictureWidth, pictureHeight;

    private AppCompatActivity mActivity;

    static{
        System.loadLibrary("jniExample");
    }

    public native String getJNIString();

    public CameraPreview(Context context, AppCompatActivity activity, int cameraID, SurfaceView surfaceView) {
        super(context);

        Log.d("CP construct", "CameraPreview");

        mActivity = activity;
        mCameraID = cameraID;
        mSurfaceView = surfaceView;

        mSurfaceView.setVisibility(View.VISIBLE);

        // SurfaceHolder.Callback을 등록하여 surface의 생성 및 해제 시점 감지
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
//        #cheon
//        mHolder.setFixedSize(320, 240);
//        LayoutParams lp = mSurfaceView.getLayoutParams();
//        lp.width = 320;
//        lp.height = 240;
//        mSurfaceView.getHolder().setFixedSize(1280, 647);
//        mSurfaceView.setLayoutParams(lp);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We purposely disregard child measurements because act as a
        // wrapper to a SurfaceView that centers the camera preview instead
        // of stretching it.
        final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(width, height);

        if (mSupportedPreviewSizes != null) {
            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
        }

    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (changed && getChildCount() > 0) {

            final View child = getChildAt(0);

            final int width = r - l;
            final int height = b - t;

            int previewWidth = width;
            int previewHeight = height;
            if (mPreviewSize != null) {
                previewWidth = mPreviewSize.width;
                previewHeight = mPreviewSize.height;
            }

            // Center the child SurfaceView within the parent.
            if (width * previewHeight > height * previewWidth) {
                final int scaledChildWidth = previewWidth * height / previewHeight;

                child.layout((width - scaledChildWidth) / 2, 0,
                        (width + scaledChildWidth) / 2, height);
            } else {
                final int scaledChildHeight = previewHeight * width / previewWidth;

                child.layout(0, (height - scaledChildHeight) / 2,
                        width, (height + scaledChildHeight) / 2);
            }
        }

    }

    // Surface가 생성되었을 때 어디에 화면에 프리뷰를 출력할지 알려줘야 한다.
    public void surfaceCreated(SurfaceHolder holder) {

        // Open an instance of the camera
        try {
            mCamera = Camera.open(mCameraID); // attempt to get a Camera instance
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
            Log.e(TAG, "Camera " + mCameraID + " is not available: " + e.getMessage());
        }

        // retrieve camera's info.
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraID, cameraInfo);

        mCameraInfo = cameraInfo;
        mDisplayOrientation = mActivity.getWindowManager().getDefaultDisplay().getRotation();

        int orientation = calculatePreviewOrientation(mCameraInfo, mDisplayOrientation);
        mCamera.setDisplayOrientation(orientation);

        mSupportedPreviewSizes =  mCamera.getParameters().getSupportedPreviewSizes();
        for(Camera.Size size : mSupportedPreviewSizes) {
            Log.d("previewSize#", String.valueOf(size.width) + " " + String.valueOf(size.height));
        }


        requestLayout();

        // get Camera parameters
        Camera.Parameters params = mCamera.getParameters();
        frameWidth = params.getPreviewSize().width;
        frameHeight = params.getPreviewSize().height;
        frameFormat = params.getPreviewFormat();
        pictureHeight = params.getPictureSize().height;
        pictureWidth = params.getPictureSize().width;
        Log.d("picpic", String.valueOf(pictureHeight));
        pixels = new int[frameWidth*frameHeight];
//        params.setPreviewSize(256, 144);

        String supportedIsoValues = params.get("iso-values");
        int minExposure = params.getMinExposureCompensation();
        int maxExposure = params.getMaxExposureCompensation();
        Log.d("iso-value", supportedIsoValues);
        Log.d("min max", "min : " + String.valueOf(minExposure) + "  max : " + String.valueOf(maxExposure));
        //params.setExposureCompensation(20);
        //params.set("iso", String.valueOf(800));
//        mCamera.setParameters(params);

        List<String> focusModes = params.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            // set the focus mode
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            // set Camera parameters
            mCamera.setParameters(params);
        }

        pretime = System.currentTimeMillis();

        try {

            mCamera.setPreviewDisplay(holder);


            // Important: Call startPreview() to start updating the preview
            // surface. Preview must be started before you can take a picture.
            mCamera.startPreview();
            isPreview = true;
            Log.d(TAG, "Camera preview started.");
        } catch (IOException e) {
            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        }

        Log.d("JNIJNI", getJNIString());
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        // Release the camera for other applications.
        if (mCamera != null) {
            if (isPreview)
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            isPreview = false;
        }

    }


    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }



    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {

        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null) {
            // preview surface does not exist
            Log.d(TAG, "Preview surface does not exist");
            return;
        }


        // stop preview before making changes
        try {
            mCamera.stopPreview();
            Log.d(TAG, "Preview stopped.");
        } catch (Exception e) {
            // ignore: tried to stop a non-existent preview
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }

        int orientation = calculatePreviewOrientation(mCameraInfo, mDisplayOrientation);
        mCamera.setDisplayOrientation(orientation);

//        #cheon
//        Camera.Parameters resParams = mCamera.getParameters();
//        resParams.setPreviewSize(320, 240);
//        mCamera.setParameters(resParams);
        try {
            mCamera.setPreviewDisplay(mHolder);

            mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] bytes, Camera camera) {
                    long curtime = System.currentTimeMillis();

                    Log.d("fps-measure", String.valueOf(curtime - pretime));

                    pretime = curtime;

                    processBuffer = bytes.clone();
                    applyGrayScale();

                    Log.d("onpreviewsize", String.valueOf(frameWidth) + " " + String.valueOf(frameHeight));

                    //String resultBinary = String.format("%8s", Integer.toBinaryString(result & 0xFF)).replace(' ', '0');

                    Log.d("byte result", String.valueOf(bytes.length));


                }
            });

            mCamera.startPreview();
            Log.d(TAG, "Camera preview started.");
        } catch (Exception e) {
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }

    }



    /**
     * 안드로이드 디바이스 방향에 맞는 카메라 프리뷰를 화면에 보여주기 위해 계산합니다.
     */
    public static int calculatePreviewOrientation(Camera.CameraInfo info, int rotation) {
        int degrees = 0;

        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }

        return result;
    }


    public void takePicture(){

        mCamera.takePicture(shutterCallback, rawCallback, jpegCallback);

    }


    Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
        public void onShutter() {

        }
    };

    Camera.PictureCallback rawCallback = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {

        }
    };


    //참고 : http://stackoverflow.com/q/37135675
    Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {

            //이미지의 너비와 높이 결정
            int w = camera.getParameters().getPictureSize().width;
            int h = camera.getParameters().getPictureSize().height;
            int orientation = calculatePreviewOrientation(mCameraInfo, mDisplayOrientation);
            Log.d("pic", String.valueOf(h));

            //byte array를 bitmap으로 변환
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeByteArray( data, 0, data.length, options);


            //이미지를 디바이스 방향으로 회전
            Matrix matrix = new Matrix();
            matrix.postRotate(orientation);
            bitmap =  Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);

            //bitmap을 byte array로 변환
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            byte[] currentData = stream.toByteArray();

            //파일로 저장
            new SaveImageTask().execute(currentData);

        }
    };



    private class SaveImageTask extends AsyncTask<byte[], Void, Void> {

        @Override
        protected Void doInBackground(byte[]... data) {
            FileOutputStream outStream = null;


            try {

                File path = new File (Environment.getExternalStorageDirectory().getAbsolutePath() + "/camtest");
                if (!path.exists()) {
                    path.mkdirs();
                }

                String fileName = String.format("%d.jpg", System.currentTimeMillis());
                File outputFile = new File(path, fileName);

                outStream = new FileOutputStream(outputFile);
                outStream.write(data[0]);
                outStream.flush();
                outStream.close();

                Log.d(TAG, "onPictureTaken - wrote bytes: " + data.length + " to "
                        + outputFile.getAbsolutePath());


                mCamera.startPreview();


                // 갤러리에 반영
                Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaScanIntent.setData(Uri.fromFile(outputFile));
                getContext().sendBroadcast(mediaScanIntent);



                try {
                    mCamera.setPreviewDisplay(mHolder);
                    mCamera.startPreview();
                    Log.d(TAG, "Camera preview started.");
                } catch (Exception e) {
                    Log.d(TAG, "Error starting camera preview: " + e.getMessage());
                }


            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

    }

    public int byteToint(byte[] arr) {
        return (arr[0] & 0xff)<<24 | (arr[1] & 0xff)<<16 |
                (arr[2] & 0xff)<<8 | (arr[3] & 0xff);
    }

    public void saveProcessFrame() {
        Log.d("saveFrame","sibal");

//        for(int i = 0; i < 1300; i++) {
//            processBuffer[i] = (byte)255;
//        }

//        int orientation = calculatePreviewOrientation(mCameraInfo, mDisplayOrientation);

//        BitmapFactory.Options options = new BitmapFactory.Options();
//        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
//        Bitmap bitmap = BitmapFactory.decodeByteArray( processBuffer, 0, processBuffer.length, options);

//      ######################
//        YuvImage yuv = new YuvImage(processBuffer, frameFormat, frameWidth, frameHeight, null);
//
//        ByteArrayOutputStream out = new ByteArrayOutputStream();
//        yuv.compressToJpeg(new Rect(0, 0, frameWidth, frameHeight), 50, out);
//
//        byte[] bytes = out.toByteArray();
//        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0 , bytes.length);
//      ######################

//        Matrix matrix = new Matrix();
//        matrix.postRotate(orientation);
//        if (bitmap == null) {
//            Log.d("bitmap null", "null nullnull");
//        }
//        else {
//            bitmap = Bitmap.createBitmap(bitmap, 0, 0, pictureWidth, pictureHeight, matrix, true);
//        }

        Bitmap bitmap = applyGrayScale();

        FileOutputStream outStream = null;

        try {
            File path = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/camtest");
            if (!path.exists()) {
                path.mkdirs();
            }

            String fileName = String.format("%d.jpg", System.currentTimeMillis());
            File outputFile = new File(path, fileName);

            outStream = new FileOutputStream(outputFile);

            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
            outStream.flush();
            outStream.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public Bitmap applyGrayScale() {
        int p;
        int size = frameWidth*frameHeight;
        for (int i = 0; i < size; i++) {
            p = processBuffer[i] & 0xFF;
            pixels[i] = 0xff000000 | p << 16 | p << 8 | p;
        }
        //to create bitmap just
        Bitmap bm = Bitmap.createBitmap(pixels, frameWidth, frameHeight, Bitmap.Config.ARGB_8888);
        return bm;
    }


//    Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
//        @Override
//        public void onPreviewFrame(byte[] bytes, Camera camera) {
////            Camera.Parameters params = mCamera.getParameters();
////
////            int width = params.getPreviewSize().width;
////            int height = params.getPreviewSize().height;
//            long curtime = System.currentTimeMillis();
//
//            Log.d("fps-measure", String.valueOf(curtime - pretime));
//
//        }
//    };



}

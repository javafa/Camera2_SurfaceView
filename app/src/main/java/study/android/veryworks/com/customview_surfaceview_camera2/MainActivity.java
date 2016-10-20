package study.android.veryworks.com.customview_surfaceview_camera2;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/*
    - Camera 2에 대한 상세설명이 잘되있는 사이트
      http://www.programkr.com/blog/MQDMyEDMwYT5.html

    - Camera2 요약  ( 출처 : http://blog.naver.com/dydwls121200/220239479720 )
    1, 5.0의 camera2 는 21이하의 camera 보다 좋습니다.
    2, camera2는 안드로이드의 camera Device의 연결을 지원합니다.
    3, 이미지란 연속적 프레임의 하나만 가져오는것인대 이것을 출력할때 캡쳐한 프레임을 메타 데이터의 패킷으로 이미지를 내보내게 된다.
        추가적으로 출력하는 이미지의 버퍼마저도 마저도 요청하는것에 따라 지정해줄 수 있다.
    4,  질의나 유효한 카레라 장비를 열거나 열때 CameraMagager 클래스의 인스턴스를 얻어야 한다.
    5, CameraDevices  는 하드웨어 장비를 묘사또는 기술한거나 유효한 세팅들이나 장비에 대한 출력 또는 반환받을 파라메터같은 정보를 제공한다.
    6, 이 정보들은 CameraCharacteristics 라는 Object에서 제공한다
    7,  createCaptureSession(List, CameraCapturSession, StateCallback , Handler) 라는 메세지를 통해서 카메라 디바이스에서 캡쳐나 이미지 스트리밍을 할수 있다.
    8, 각각의 서페이스는 포멧과 적당한 크기를 미리 확인할 수 있게 만들어야한다.
       이 Surface 들은 다양한 클래스로  SurfaceView ,SurfaceTexture , Surface(SurfaceTexture),MediaCodec, MediaRecorder,Allocation,ImageReader를 통해 Target Surface 를 얻을 수 있다.
    9, SurfaceView 나 TextureView 로 ... 또는 SurfaceTexture 로  카메라 미리보기 이미지들이 보내진다
    10, ImageReader 객체를 이용해서 버퍼스트림으로[RawBuffer]로 읽을 수있다 .
    11, 한컷의 이미지를 캡쳐하기위해서는 카메라 device의 캡쳐 파라메터들을 정의해야한다.


    - 카메라2 기본처리순서 (출처 : 퍼왔으나 주소를 모르겠군요)
    1. 카메라 디바이스를 준비한다 > CameraManager#openCamera
    2. 준비 완료 알림을 받는다 > CameraDevice.StateCallback
    3. CaptureRequest를 준비한다 > CaptureRequest.Builder
    4. CaptureSession을 준비한다 > CameraDevice.createCaptureSession
    5. Session 상태 알림을 받는다 > CameraCaptureSession.StateCallback
    6. CaptureRequest를 발행한다 > CameraCaptureSession#capture
    7. 카메라 디바이스 사용을 완료한다 > CameraDevice#close

    소스코드 출처 : https://android.googlesource.com/platform/frameworks/base

 */

public class MainActivity extends AppCompatActivity {
    static final String TAG = "Camera2MainActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    static final String CAPTURE_FILENAME_PREFIX = "Camera2Test";  // 캡쳐후 저장되는 파일명 앞에 붙는 prefix
    HandlerThread mBackgroundThread;  // Preview 정상적으로 동작하게 하기 위해서 사용하는 Background Thread
    Handler mBackgroundHandler; // Background 에서 동작하는 핸들러
    Handler mForegroundHandler; // UI Thread에서 동작하는 핸들러

    SurfaceView mSurfaceView;
    Button btnTake;

    ImageReader mCaptureBuffer;           // 캡쳐 이미지를 읽어올 리더
    CameraManager mCameraManager;         // 카메라 매니저
    CameraDevice mCamera;                  // 사용하는 카메라 디바이스
    CameraCaptureSession mCaptureSession; // 캡쳐 세션

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnTake = (Button) findViewById(R.id.btn_takepicture);
        btnTake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto();
            }
        });
    }

    /**
     * Activity에 포커스가 생기면 카메라를 초기화해준다
     */
    @Override
    protected void onResume() {
        super.onResume();
        // 카메라 관리를 요청하기 위한 백그라운드 Thread  << 프리뷰를 하지 않으면 없어도 된다
        mBackgroundThread = new HandlerThread("background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        mForegroundHandler = new Handler(getMainLooper());
        mCameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mSurfaceView.getHolder().addCallback(mSurfaceHolderCallback);
    }
    /**
     * Activity가 포거스를 잃으면 프리뷰를 위한 캡쳐세션과 서피스뷰를 닫아준다
     */
    @Override
    protected void onPause() {
        super.onPause();
        try {
            // Ensure SurfaceHolderCallback#surfaceChanged() will run again if the user returns
            mSurfaceView.getHolder().setFixedSize(/*width*/0, /*height*/0);
            // 캡쳐세션 종료
            if (mCaptureSession != null) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
        } finally {
            if (mCamera != null) {
                mCamera.close();
                mCamera = null;
            }
        }
        // background Thread를 종료한다
        mBackgroundThread.quitSafely();
        try {
            // background thread의 종료를 동기화 시키기 위해 main thread에 join 해준다
            mBackgroundThread.join();
        } catch (InterruptedException ex) {
            Log.e(TAG, "Background worker thread was interrupted while joined", ex);
        }
        // 백그라운드 thread가 멈추면 프리뷰를 재생해주는 imageReader도 닫아준다
        if (mCaptureBuffer != null) mCaptureBuffer.close();
    }

    // Full 해상도 이미지를 저장해준다
    public void takePhoto() {
        if (mCaptureSession != null) {
            try {
                CaptureRequest.Builder requester = mCamera.createCaptureRequest(mCamera.TEMPLATE_STILL_CAPTURE);
                requester.addTarget(mCaptureBuffer.getSurface());
                try {
                    // 캡쳐 세션에 콜백을 Attach 하지 않았으므로 handler를 null처리할 수 있다
                    mCaptureSession.capture(requester.build(), /*listener*/null, /*handler*/null);
                } catch (CameraAccessException ex) {
                    Log.e(TAG, "Failed to file actual capture request", ex);
                }
            } catch (CameraAccessException ex) {
                Log.e(TAG, "Failed to build actual capture request", ex);
            }
        } else {
            Log.e(TAG, "User attempted to perform a capture outside our session");
        }
        // 흐름제어는 mImageCaptureListener.onImageAvailable() 안에서 이뤄진다
    }
    /**
     * Surface뷰의 상태변화시 호출된다
     */
    final SurfaceHolder.Callback mSurfaceHolderCallback = new SurfaceHolder.Callback() {
        private String mCameraId;
        private boolean mGotSecondCallback;
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            // surface가 포그라운드에 그려지면 항상 호출된다
            Log.i(TAG, "Surface created");
            mCameraId = null;
            mGotSecondCallback = false;
        }
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, "Surface destroyed");
            holder.removeCallback(this);
            // onResume에서 항상 reattach를 하기 때문에 제거해도 향후에 callback을 계속 받을 수 있다
        }
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (mCameraId == null) {
                // 프리뷰를 동작시키기 위해 카메라를 가져오고
                // 카메라에서 가져온 이미지를 화면에 최적화 시켜서 그려준다
                try {
                    for (String cameraId : mCameraManager.getCameraIdList()) {
                        CameraCharacteristics cameraCharacteristics =
                                mCameraManager.getCameraCharacteristics(cameraId);
                        if (cameraCharacteristics.get(cameraCharacteristics.LENS_FACING) ==
                                CameraCharacteristics.LENS_FACING_BACK) {
                            Log.i(TAG, "Found a back-facing camera");
                            StreamConfigurationMap info = cameraCharacteristics
                                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                            // 카메라에서 입력받을 수 있는 이미지 최대크기
                            Size largestSize = Collections.max(
                                    Arrays.asList(info.getOutputSizes(ImageFormat.JPEG)),
                                    new CompareSizesByArea());

                            // 이미지를 캡쳐하기위한 reader 준비
                            Log.i(TAG, "Capture size: " + largestSize);
                            mCaptureBuffer = ImageReader.newInstance(largestSize.getWidth(),
                                    largestSize.getHeight(), ImageFormat.JPEG, /*maxImages*/2);
                            mCaptureBuffer.setOnImageAvailableListener(
                                    mImageCaptureListener, mBackgroundHandler);
                            // 현재 화면상에 있는 서피스뷰의 사이즈
                            Log.i(TAG, "SurfaceView size: " +
                                    mSurfaceView.getWidth() + 'x' + mSurfaceView.getHeight());

                            // 카메라가 지원하는 최적사이즈 선택
                            Size optimalSize = chooseBigEnoughSize(
                                    info.getOutputSizes(SurfaceHolder.class), width, height);

                            Log.i(TAG, "Preview size: " + optimalSize);
                            SurfaceHolder surfaceHolder = mSurfaceView.getHolder();
                            surfaceHolder.setFixedSize(optimalSize.getWidth(),
                                    optimalSize.getHeight());
                            mCameraId = cameraId;
                            return;
                        }
                    }
                } catch (CameraAccessException ex) {
                    Log.e(TAG, "Unable to list cameras", ex);
                }
                Log.e(TAG, "Didn't find any back-facing cameras");

            // 카메라가 널이 아니면 이미 세팅된 상태
            } else if (!mGotSecondCallback) {
                if (mCamera != null) {
                    Log.e(TAG, "Aborting camera open because it hadn't been closed");
                    return;
                }
                // 3. 카메라를 열기위한 권한 체크
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                        && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                    return;
                }
                try {
                    mCameraManager.openCamera(mCameraId, mCameraStateCallback,
                            mBackgroundHandler);
                } catch (CameraAccessException ex) {
                    Log.e(TAG, "Failed to configure output surface", ex);
                }
                mGotSecondCallback = true;
            }
        }
    };

    /**
     * 카메라 디바이스의 상태가 변경되면 호출되는 CallBack
     */
    final CameraDevice.StateCallback mCameraStateCallback =
        new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                Log.i(TAG, "Successfully opened camera");
                mCamera = camera;
                try {
                    List<Surface> outputs = Arrays.asList(
                            mSurfaceView.getHolder().getSurface(), mCaptureBuffer.getSurface());
                    camera.createCaptureSession(outputs, mCaptureSessionListener,
                            mBackgroundHandler);
                } catch (CameraAccessException ex) {
                    Log.e(TAG, "Failed to create a capture session", ex);
                }
            }
            @Override
            public void onDisconnected(CameraDevice camera) {
                Log.e(TAG, "Camera was disconnected");
            }
            @Override
            public void onError(CameraDevice camera, int error) {
                Log.e(TAG, "State error on device '" + camera.getId() + "': code " + error);
            }
        };
    /**
     * 백그라운드 Thread에서 화면을 캡쳐하는 Session에 변경사항이 있을때 호출되는 Callback 이다
     */
    final CameraCaptureSession.StateCallback mCaptureSessionListener =
        new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession session) {
                Log.i(TAG, "Finished configuring camera outputs");
                mCaptureSession = session;
                SurfaceHolder holder = mSurfaceView.getHolder();
                if (holder != null) {
                    try {
                        // 화면에 뿌려줄 프리뷰 정보를 만들고
                        CaptureRequest.Builder requestBuilder =
                                mCamera.createCaptureRequest(mCamera.TEMPLATE_PREVIEW);
                        requestBuilder.addTarget(holder.getSurface());
                        CaptureRequest previewRequest = requestBuilder.build();
                        // 프리뷰 이미지를 화면에 뿌려준다
                        try {
                            session.setRepeatingRequest(previewRequest, /*listener*/null,
                            /*handler*/null);
                        } catch (CameraAccessException ex) {
                            Log.e(TAG, "Failed to make repeating preview request", ex);
                        }
                    } catch (CameraAccessException ex) {
                        Log.e(TAG, "Failed to build preview request", ex);
                    }
                }
                else {
                    Log.e(TAG, "Holder didn't exist when trying to formulate preview request");
                }
            }
            @Override
            public void onClosed(CameraCaptureSession session) {
                mCaptureSession = null;
            }
            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
                Log.e(TAG, "Configuration error on device '" + mCamera.getId());
            }
        };
    /**
     * 카메라로 부터 JPEG이미지를 받게 되면 Callback 이 호출된다
     */
    final ImageReader.OnImageAvailableListener mImageCaptureListener =
        new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                // Save the image once we get a chance
                mBackgroundHandler.post(new CapturedImageSaver(reader.acquireNextImage()));
                // Control flow continues in CapturedImageSaver#run()
            }
        };

    // 백그라운드 Thread에서 캡쳐화면을 저장한다
    static class CapturedImageSaver implements Runnable {
        private Image mCapture;
        public CapturedImageSaver(Image capture) {
            mCapture = capture;
        }
        @Override
        public void run() {
            try {
                // 파일명 지정
                File file = File.createTempFile(CAPTURE_FILENAME_PREFIX, ".jpg",
                        Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_PICTURES));
                try (FileOutputStream ostream = new FileOutputStream(file)) {
                    Log.i(TAG, "Retrieved image is" +
                            (mCapture.getFormat() == ImageFormat.JPEG ? "" : "n't") + " a JPEG");
                    ByteBuffer buffer = mCapture.getPlanes()[0].getBuffer();
                    Log.i(TAG, "Captured image size: " +
                            mCapture.getWidth() + 'x' + mCapture.getHeight());
                    // 이미지를 파일에 쓰기
                    byte[] jpeg = new byte[buffer.remaining()];
                    buffer.get(jpeg);
                    ostream.write(jpeg);
                } catch (FileNotFoundException ex) {
                    Log.e(TAG, "Unable to open output file for writing", ex);
                } catch (IOException ex) {
                    Log.e(TAG, "Failed to write the image to the output file", ex);
                }
            } catch (IOException ex) {
                Log.e(TAG, "Unable to create a new output file", ex);
            } finally {
                mCapture.close();
            }
        }
    }

    /**
     * 카메라가 지원하는 프리뷰 사이즈를 반환해 주는 함수
     * @param choices 카메라가 지원하는 출력가능 화면 크기들
     * @param width 가로 최소사이즈 요청
     * @param height 세로 최소사이즈 요청
     * @return 요청 사이즈가 없으면 카메라가 지원하는 목록의 첫번째 사이즈를 리턴해준다... 이게 아마 1024 * 768인듯...
     */
    static Size chooseBigEnoughSize(Size[] choices, int width, int height) {
        // 내가 요청한 사이즈에 맞는 카메라 프리뷰 찾기
        List<Size> bigEnough = new ArrayList<Size>();
        for (Size option : choices) {
            if (option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        // 맞는 사이즈가 있으면 그중에 가장 작은 사이즈를 리턴
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
            // 없으면 카메라가 지원하는 첫번째값 리턴
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }
    /**
     * 카메라 영역 사이즈 비교
     */
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // 곱셈시 오버플로우 되지 않도록 캐스팅 필요
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    // 권한 Callback
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(MainActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}
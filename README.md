# Camera2_SurfaceView
    - 소스코드 출처 : https://android.googlesource.com/platform/frameworks/base

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

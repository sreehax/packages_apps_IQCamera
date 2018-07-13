package com.sreeharis.camera;


import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.app.Activity;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by mhidaka on 2015/11/09.
 */
public class BasicCamera {

	private static final String TAG = "BasicCamera";

	private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

	static {
		ORIENTATIONS.append(Surface.ROTATION_0, 90);
		ORIENTATIONS.append(Surface.ROTATION_90, 0);
		ORIENTATIONS.append(Surface.ROTATION_180, 270);
		ORIENTATIONS.append(Surface.ROTATION_270, 180);
	}

	private static final int STATE_PREVIEW = 0x00;
	private static final int STATE_WAITING_LOCK = 0x01;
	private static final int STATE_WAITING_PRECAPTURE = 0x02;
	private static final int STATE_WAITING_NON_PRECAPTURE = 0x03;
	private static final int STATE_PICTURE_TAKEN = 0x04;
	private int mState = STATE_PREVIEW;
	private Semaphore mCameraOpenCloseLock = new Semaphore(1);
	private CameraDevice mCameraDevice;
	private CaptureRequest.Builder mPreviewRequestBuilder;
	private CaptureRequest mPreviewRequest;
	private CameraCaptureSession mCaptureSession = null;

	private CameraInterface mInterface;

	public void setInterface(CameraInterface param) {
		mInterface = param;
	}

	public final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

		@Override
		public void onOpened(CameraDevice cameraDevice) {
			mCameraOpenCloseLock.release();
			mCameraDevice = cameraDevice;
			createCameraPreviewSession();
		}

		@Override
		public void onDisconnected(CameraDevice cameraDevice) {
			mCameraOpenCloseLock.release();
			cameraDevice.close();
			mCameraDevice = null;
		}

		@Override
		public void onError(CameraDevice cameraDevice, int error) {
			mCameraOpenCloseLock.release();
			cameraDevice.close();
			mCameraDevice = null;
			Log.e(TAG, "Camera StateCallback onError: Please Reboot Android OS");
		}

	};

	public boolean isLocked() throws InterruptedException {
		return mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS);
	}

	public void close() {
		try {
			mCameraOpenCloseLock.acquire();
			if (null != mCaptureSession) {
				mCaptureSession.close();
				mCaptureSession = null;
			}
			if (null != mCameraDevice) {
				mCameraDevice.close();
				mCameraDevice = null;
			}
		} catch (InterruptedException e) {
			throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
		} finally {
			mCameraOpenCloseLock.release();
		}
	}

	private void createCameraPreviewSession() {
		try {
			SurfaceTexture texture = mInterface.getSurfaceTextureFromTextureView();
			assert texture != null;

			Size preview = mInterface.getPreviewSize();
			texture.setDefaultBufferSize(preview.getWidth(), preview.getHeight());

			Surface surface = new Surface(texture);
			mPreviewRequestBuilder
					= mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			mPreviewRequestBuilder.addTarget(surface);

			Surface imageRenderSurface = mInterface.getImageRenderSurface();
			mCameraDevice.createCaptureSession(Arrays.asList(surface, imageRenderSurface),
					new CameraCaptureSession.StateCallback() {

						@Override
						public void onConfigured(CameraCaptureSession cameraCaptureSession) {
							if (null == mCameraDevice) {
								return;
							}

							mCaptureSession = cameraCaptureSession;
							try {
								mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
										CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
								mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
										CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

								mPreviewRequest = mPreviewRequestBuilder.build();
								mCaptureSession.setRepeatingRequest(mPreviewRequest,
										mCaptureCallback, mInterface.getBackgroundHandler());
							} catch (CameraAccessException e) {
								e.printStackTrace();
							}
						}

						@Override
						public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
							Log.e(TAG, "CameraCaptureSession onConfigureFailed");
						}
					}, null
			);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private CameraCaptureSession.CaptureCallback mCaptureCallback
			= new CameraCaptureSession.CaptureCallback() {

		@Override
		public void onCaptureProgressed(CameraCaptureSession session,
										CaptureRequest request,
										CaptureResult partialResult) {
			process(partialResult);
		}

		@Override
		public void onCaptureCompleted(CameraCaptureSession session,
									   CaptureRequest request,
									   TotalCaptureResult result) {
			process(result);
		}


		private void process(CaptureResult result) {
			switch (mState) {
				case STATE_PREVIEW: {
					break;
				}
				case STATE_WAITING_LOCK: {
					Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
					if (afState == null) {
						captureStillPicture();
					} else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
							CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
						Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
						if (aeState == null ||
								aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
							mState = STATE_PICTURE_TAKEN;
							captureStillPicture();
						} else {
							runPrecaptureSequence();
						}
					}
					break;
				}
				case STATE_WAITING_PRECAPTURE: {
					Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
							aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
							aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
						mState = STATE_WAITING_NON_PRECAPTURE;
					}
					break;
				}
				case STATE_WAITING_NON_PRECAPTURE: {
					Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
					if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
						mState = STATE_PICTURE_TAKEN;
						captureStillPicture();
					}
					break;
				}
			}
		}

	};

	private void captureStillPicture() {
		try {
			if (null == mCameraDevice) {
				return;
			}
			final CaptureRequest.Builder captureBuilder =
					mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
			captureBuilder.addTarget(mInterface.getImageRenderSurface());

			captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
					CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
			captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,
					CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

			int rotation = mInterface.getRotation();
			captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

			CameraCaptureSession.CaptureCallback CaptureCallback
					= new CameraCaptureSession.CaptureCallback() {

				@Override
				public void onCaptureCompleted(CameraCaptureSession session,
											   CaptureRequest request,
											   TotalCaptureResult result) {
					Log.e(TAG, "onCaptureCompleted Picture Saved");
					unlockFocus();
				}
			};

			mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}


	private void runPrecaptureSequence() {
		try {
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
					CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
			mState = STATE_WAITING_PRECAPTURE;
			mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
					mInterface.getBackgroundHandler());
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	public void takePicture() {
		if (mCaptureSession != null) {
			lockFocus();
		}
	}

	private void lockFocus() {
		try {
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
					CameraMetadata.CONTROL_AF_TRIGGER_START);
			mState = STATE_WAITING_LOCK;
			mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
					mInterface.getBackgroundHandler());
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private void unlockFocus() {
		try {
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
					CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
			mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
					CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
			mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
					mInterface.getBackgroundHandler());
			mState = STATE_PREVIEW;
			mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
					mInterface.getBackgroundHandler());
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}
}

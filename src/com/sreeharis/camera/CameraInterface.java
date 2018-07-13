package com.sreeharis.camera;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.util.Size;
import android.view.Surface;

public interface CameraInterface {
	SurfaceTexture getSurfaceTextureFromTextureView();
	Size getPreviewSize();
	Handler getBackgroundHandler();
	Surface getImageRenderSurface();
	int getRotation();
}

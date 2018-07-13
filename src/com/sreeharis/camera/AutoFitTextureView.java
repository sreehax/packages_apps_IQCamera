package com.sreeharis.camera;
import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

public class AutoFixTextureView extends TextureView {
	private int mRatioWidth = 0;
	private int mRatioHeight = 0;
	public AutoFitTextureView(Context context) {
		this(context, null);
	}
	public AutoFitTextureView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}
	public AutoFitTextureView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		int width = MeasureSpec.getSize(widthMeasureSpec);
		int height = MeasureSpec.getSize(heightMeasureSpec);
		if(mRatioWidth == 0 || mRatioHeight == 0) {
			setMeasureDimension(width, height);
		} else {
			if (width < height * mRatioHeight / mRatioWidth) {
				setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
			} else {
				setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
			}
		}
	}
}

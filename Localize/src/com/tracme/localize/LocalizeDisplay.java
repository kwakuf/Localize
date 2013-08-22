package com.tracme.localize;

import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.widget.ImageView;

public class LocalizeDisplay {

	// these matrices will be used to move and zoom image
	public Matrix matrix = new Matrix();
	public Matrix savedMatrix = new Matrix();
	public Matrix resMatrix = new Matrix();

	// we can be in one of these 3 states
	public static final int NONE = 0;
	public static final int DRAG = 1;
	public static final int ZOOM = 2;

	// Default to NONE
	public int mode = NONE;

	// Arrays containing continuous matrix details
	public float[] eventMatrix = new float[9];
	public float[] lastEvent = null;

	// variables for zoom and rotate calculations
	public float oldDist = 1f;
	public float d = 0f;
	public float newRot = 0f;

	float mCurrentScale = 1.0f;

	// Points for zoom and rotate calculations
	public PointF start = new PointF();
	public PointF mid = new PointF();

	/*
	 * We'll need to save the initial scaling factor in order to account for the
	 * pixel positioning of the localization program
	 */
	public static float initScaleX;
	public static float initScaleY;
	
	//Default Image properties
	public Drawable drawable;

	public LocalizeDisplay() {
		matrix.getValues(eventMatrix);
	}

	public void calcInitScale() {
		// Drawable drawable = getResources().getDrawable(R.drawable.cc_1);

		/* The initial size of the image will have to predefined somewhere */
		initScaleX = (float) drawable.getIntrinsicWidth() / 1293;
		initScaleY = (float) drawable.getIntrinsicHeight() / 755;

		// IMAGE is 1293x755 NOTE ALL SHOULD BE THE SAME SIZE
		// NOTE: The xy spreadsheet is 1291 by 754 for some reason
		return;
	}

	public float spacing(MotionEvent event) {
		float x = event.getX(0) - event.getX(1);
		float y = event.getY(0) - event.getY(1);
		return (float) Math.sqrt(x * x + y * y);
	}

	public void midPoint(PointF point, MotionEvent event) {
		float x = event.getX(0) + event.getX(1);
		float y = event.getY(0) + event.getY(1);
		point.set(x / 2, y / 2);
	}

	public float rotation(MotionEvent event) {
		double delta_x = (event.getX(0) - event.getX(1));
		double delta_y = (event.getY(0) - event.getY(1));
		double radians = Math.atan2(delta_y, delta_x);
		return (float) Math.toDegrees(radians);
	}
	
	/*
	 * Changes the image matrix to account for a drag
	 */
	public void drag(MotionEvent event) {
		matrix.set(savedMatrix);
		float dx = event.getX() - start.x;
		float dy = event.getY() - start.y;

		/*
		 * WE MIGHT WANT TO RESTRICT MOVEMENT IN CERTAIN DIRECTIONS BASED ON
		 * SCALE: if (view.getHeight() >= drawable.getIntrinsicHeight()) { dy =
		 * 0; } if (view.getWidth() >= drawable.getIntrinsicWidth()) { dx = 0; }
		 */
		matrix.postTranslate(dx, dy);
	}
	
	/*
	 * Changes the image matrix to account for a zoom/rotate
	 */
	public void zoomAndRotate(MotionEvent event, ImageView view) {
		float newDist = spacing(event);
		if (newDist > 10f) {
			matrix.set(savedMatrix);
			float scale = (newDist / oldDist);

			matrix.postScale(scale, scale, mid.x, mid.y);
		}
		if (lastEvent != null && event.getPointerCount() == 3) {
			newRot = rotation(event);
			float r = newRot - d;
			float[] values = new float[9];
			matrix.getValues(values);
			float tx = values[2];
			float ty = values[5];
			float sx = values[0];
			float xc = (view.getWidth() / 2) * sx;
			float yc = (view.getHeight() / 2) * sx;
			matrix.postRotate(r, tx + xc, ty + yc);
		}
	}
	
	/*
	 * Adjusts the x position on the image for scaling and dragging
	 */
	public float getAdjustedX(float x) {
		return (x * initScaleX * eventMatrix[Matrix.MSCALE_X]) - 25
				+ eventMatrix[Matrix.MTRANS_X];
	}
	
	/*
	 * Adjusts the y position on the image for scaling and dragging
	 */
	public float getAdjustedY(float y) {
		return (y * initScaleY * eventMatrix[Matrix.MSCALE_Y]) - 25
		+ eventMatrix[Matrix.MTRANS_Y];
	}
	
	public void checkEdgeCases(ImageView view) {
		switch (mode) {
		case LocalizeDisplay.DRAG:
			view.getImageMatrix().getValues(eventMatrix);

			if (view.getWidth() < drawable.getIntrinsicWidth()) {
				if (eventMatrix[Matrix.MTRANS_X] > 0) {
					eventMatrix[Matrix.MTRANS_X] = 0;
				}
				else if (eventMatrix[Matrix.MTRANS_X] < view.getWidth()
						- drawable.getIntrinsicWidth()) {
					eventMatrix[Matrix.MTRANS_X] = view.getWidth()
							- drawable.getIntrinsicWidth();
				}
			}
			else
				eventMatrix[Matrix.MTRANS_X] = 0;

			if (view.getHeight() < drawable.getIntrinsicHeight()) {
				if (eventMatrix[Matrix.MTRANS_Y] > 0) {
					eventMatrix[Matrix.MTRANS_Y] = 0;
				}
				else if (eventMatrix[Matrix.MTRANS_Y] < view.getHeight()
						- drawable.getIntrinsicHeight()) {
					eventMatrix[Matrix.MTRANS_Y] = view.getHeight()
							- drawable.getIntrinsicHeight();
				}
			}
			else
				eventMatrix[Matrix.MTRANS_Y] = 0;

			resMatrix.setValues(eventMatrix);
			matrix = resMatrix;
			break;
		case LocalizeDisplay.ZOOM:
			// Ensure minimum and maximum scales
			view.getImageMatrix().getValues(eventMatrix);
			if (eventMatrix[0] < 1) {
				eventMatrix[0] = (float) 1.0;
				eventMatrix[2] = 0;
			}
			if (eventMatrix[4] < 1) {
				eventMatrix[4] = (float) 1.0;
				eventMatrix[5] = 0;
			}
			resMatrix.setValues(eventMatrix);
			matrix = resMatrix;
			System.out.println("AFTER: " + view.getImageMatrix().toString());
			break;
		}
	}
	
	public void actionDown(MotionEvent event) {
		savedMatrix.set(matrix);
		start.set(event.getX(), event.getY());
		// lastEvent = null;
	}
	
	public void pointerDown(MotionEvent event) {
		lastEvent = new float[4];
		lastEvent[0] = event.getX(0);
		lastEvent[1] = event.getX(1);
		lastEvent[2] = event.getY(0);
		lastEvent[3] = event.getY(1);
		d = rotation(event);
	}
}